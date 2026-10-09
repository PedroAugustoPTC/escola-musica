package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.ResultadoConfirmacao;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recebe o resultado da execução de cada ação pelo orquestrador (FR-007b, FR-017). Idempotente;
 * cada resultado é validado isoladamente; o backend decide a compensação quando uma edição falha
 * por mensagem não editável ou um documento fica indisponível. O orquestrador só classifica o erro:
 * o que fazer depois é daqui.
 */
public class ConfirmarExecucaoAcoesCanalCasoDeUso {

    public static final String MENSAGEM_NAO_EDITAVEL = AcaoCanal.CODIGO_MENSAGEM_NAO_EDITAVEL;
    public static final String DOCUMENTO_INDISPONIVEL = AcaoCanal.CODIGO_DOCUMENTO_INDISPONIVEL;
    public static final String ERRO_TELEGRAM = "ERRO_TELEGRAM";

    private static final Set<String> CODIGOS_CONHECIDOS =
            Set.of(
                    MENSAGEM_NAO_EDITAVEL,
                    "CHAT_INDISPONIVEL",
                    "ACAO_NAO_SUPORTADA",
                    DOCUMENTO_INDISPONIVEL,
                    "LIMITE_DE_TAXA",
                    ERRO_TELEGRAM);
    private static final Pattern FORMATO_CODIGO = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
    private static final Pattern FORMATO_ACAO_ID = Pattern.compile("^[A-Za-z0-9:_.-]{3,120}$");
    private static final int TENTATIVAS_DE_GRAVACAO = 3;

    public enum Situacao {
        EXECUTADA,
        FALHOU
    }

    public record Resultado(
            String acaoId, Situacao situacao, String codigoMotivo, Long mensagemId) {}

    /**
     * @param aceitas confirmações aplicadas ou idênticas às já registradas
     * @param ignoradas ids desconhecidos, vencidos, divergentes ou com resultado inválido
     * @param violacaoDeContrato o orquestrador omitiu o {@code mensagemId} obrigatório de uma etapa
     *     executada (os demais resultados foram aplicados normalmente)
     */
    public record Resposta(int aceitas, List<String> ignoradas, boolean violacaoDeContrato) {}

    private final AcaoCanalRepositorio acoes;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;
    private final PoliticaRetentativa politica;

    public ConfirmarExecucaoAcoesCanalCasoDeUso(
            AcaoCanalRepositorio acoes,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio,
            PoliticaRetentativa politica) {
        this.acoes = Objects.requireNonNull(acoes);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
        this.politica = Objects.requireNonNull(politica);
    }

    public Resposta executar(List<Resultado> resultados) {
        int aceitas = 0;
        boolean violacao = false;
        List<String> ignoradas = new ArrayList<>();
        for (Resultado resultado : resultados) {
            Desfecho desfecho = confirmar(resultado);
            switch (desfecho) {
                case ACEITA -> aceitas++;
                case IGNORADA -> registrarIgnorada(ignoradas, resultado);
                case VIOLACAO -> {
                    violacao = true;
                    registrarIgnorada(ignoradas, resultado);
                }
            }
        }
        return new Resposta(aceitas, ignoradas, violacao);
    }

    private enum Desfecho {
        ACEITA,
        IGNORADA,
        VIOLACAO
    }

    private Desfecho confirmar(Resultado resultado) {
        if (resultado == null
                || resultado.acaoId() == null
                || resultado.acaoId().isBlank()
                || resultado.situacao() == null
                || (resultado.situacao() == Situacao.FALHOU
                        && (resultado.codigoMotivo() == null
                                || resultado.codigoMotivo().isBlank()))) {
            return Desfecho.IGNORADA;
        }
        for (int tentativa = 0; tentativa < TENTATIVAS_DE_GRAVACAO; tentativa++) {
            Optional<AcaoCanal> encontrada = acoes.buscarPorId(resultado.acaoId());
            if (encontrada.isEmpty()) {
                return Desfecho.IGNORADA;
            }
            AcaoCanal acao = encontrada.get();
            int versaoLida = acao.versao();
            Instant agora = relogio.agora();
            if (resultado.situacao() == Situacao.EXECUTADA) {
                Desfecho desfecho = executada(acao, resultado, versaoLida, agora);
                if (desfecho != null) {
                    return desfecho;
                }
            } else {
                Desfecho desfecho = falhou(acao, resultado, versaoLida, agora);
                if (desfecho != null) {
                    return desfecho;
                }
            }
        }
        return Desfecho.IGNORADA;
    }

    /**
     * @return {@code null} se a gravação perdeu a disputa e deve ser refeita
     */
    private Desfecho executada(AcaoCanal acao, Resultado resultado, int versaoLida, Instant agora) {
        boolean etapaComTrabalho =
                acao.tipo() == TipoAcaoCanal.MENSAGEM_ETAPA && acao.trabalhoId() != null;
        if (etapaComTrabalho && (resultado.mensagemId() == null || resultado.mensagemId() < 1)) {
            return Desfecho.VIOLACAO;
        }
        ResultadoConfirmacao efeito = acao.confirmarExecutada(agora);
        if (efeito == ResultadoConfirmacao.DIVERGENTE) {
            return Desfecho.IGNORADA;
        }
        if (efeito == ResultadoConfirmacao.REPETIDA) {
            return Desfecho.ACEITA;
        }
        if (!acoes.atualizarSeVersao(acao, versaoLida)) {
            return null;
        }
        if (etapaComTrabalho) {
            // grava o id da mensagem de etapa no trabalho; as próximas etapas editam a mesma
            // mensagem
            fila.registrarMensagemEtapa(acao.trabalhoId(), resultado.mensagemId());
        }
        return Desfecho.ACEITA;
    }

    /**
     * @return {@code null} se a gravação perdeu a disputa e deve ser refeita
     */
    private Desfecho falhou(AcaoCanal acao, Resultado resultado, int versaoLida, Instant agora) {
        String codigo = normalizar(resultado.codigoMotivo());
        ResultadoConfirmacao efeito = acao.confirmarFalha(codigo, agora);
        if (efeito == ResultadoConfirmacao.DIVERGENTE) {
            return Desfecho.IGNORADA;
        }
        if (efeito == ResultadoConfirmacao.REPETIDA) {
            return Desfecho.ACEITA;
        }
        if (!acoes.atualizarSeVersao(acao, versaoLida)) {
            return null;
        }
        if (acao.compensaPor(codigo)) {
            // uma só compensação por ação (id <acaoId>-c); uma compensação que falha não gera outra
            acoes.gravarLote(List.of(acao.criarCompensacao(agora, politica)));
        }
        return Desfecho.ACEITA;
    }

    /**
     * Código novo do orquestrador (ou texto livre) nunca é gravado como veio: vira {@code
     * ERRO_TELEGRAM}.
     */
    public static String normalizar(String codigo) {
        String limpo = codigo.trim();
        return CODIGOS_CONHECIDOS.contains(limpo) && FORMATO_CODIGO.matcher(limpo).matches()
                ? limpo
                : ERRO_TELEGRAM;
    }

    /** Só ecoa ids com formato de {@code acaoId}: nada de texto livre de volta ao orquestrador. */
    private static void registrarIgnorada(List<String> ignoradas, Resultado resultado) {
        if (resultado != null
                && resultado.acaoId() != null
                && FORMATO_ACAO_ID.matcher(resultado.acaoId()).matches()) {
            ignoradas.add(resultado.acaoId());
        }
    }
}
