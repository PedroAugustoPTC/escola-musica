package br.com.felipeblaft.relatorios.saida.telegram;

import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso.VisaoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.RelatorioFoco;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.entrada.telegram.ApresentacaoRelatorioAulaTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.ApresentacaoRelatorioSemestralTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.ApresentadorTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.PublicacaoAcaoTelegramPorta;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.NotificacaoProfessorPorta;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Avisos ativos ao professor pelo bot (FR-021, contracts/telegram-conversa.md §5.2 e §6).
 *
 * <ul>
 *   <li>Trabalho pedido no Telegram: a mensagem de etapa é editada no lugar e o resultado é
 *       entregue pela mesma apresentação da conversa, que põe o relatório em foco.
 *   <li>Trabalho pedido na Web, com vínculo: só um aviso curto com "Continuar no chat", sem mudar o
 *       foco da conversa até o professor tocar no botão.
 * </ul>
 *
 * Falhas de envio nunca interrompem o processamento.
 */
@Component
public class NotificacaoTelegramAdaptador implements NotificacaoProfessorPorta {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacaoTelegramAdaptador.class);
    private static final int TENTATIVAS_GRAVACAO = 3;

    private final VinculoTelegramRepositorio vinculos;
    private final AlunoRepositorio alunos;
    private final FilaTrabalhoPorta fila;
    private final ApresentacaoRelatorioAulaTelegram apresentacao;
    private final ApresentacaoRelatorioSemestralTelegram apresentacaoSemestral;
    private final ApresentadorTelegram apresentador;
    private final PublicacaoAcaoTelegramPorta publicacao;

    public NotificacaoTelegramAdaptador(
            VinculoTelegramRepositorio vinculos,
            AlunoRepositorio alunos,
            FilaTrabalhoPorta fila,
            ApresentacaoRelatorioAulaTelegram apresentacao,
            ApresentacaoRelatorioSemestralTelegram apresentacaoSemestral,
            ApresentadorTelegram apresentador,
            PublicacaoAcaoTelegramPorta publicacao) {
        this.vinculos = vinculos;
        this.alunos = alunos;
        this.fila = fila;
        this.apresentacao = apresentacao;
        this.apresentacaoSemestral = apresentacaoSemestral;
        this.apresentador = apresentador;
        this.publicacao = publicacao;
    }

    // ------------------------------------------------------------------ etapas

    @Override
    public void notificarEtapa(TrabalhoProcessamento trabalho) {
        if (trabalho.canalSolicitacao() != Canal.TELEGRAM
                || trabalho.etapa() == EtapaTrabalho.ENVIANDO) {
            return;
        }
        protegido(
                "etapa",
                () -> {
                    Optional<VinculoTelegram> vinculo =
                            vinculos.buscarPorProfessorId(trabalho.professorId());
                    if (vinculo.isEmpty()) {
                        return;
                    }
                    // a etapa edita a mensagem de etapa já conhecida do trabalho; sem ela, o envio
                    // direto
                    // nada faz e o despacho do orquestrador a resolve pelo trabalho no momento da
                    // entrega
                    publicacao.publicar(
                            "etapa:" + trabalho.id() + ":" + trabalho.etapa().name(),
                            trabalho.professorId(),
                            List.of(
                                    new SaidaTelegram.MensagemEtapa(
                                            vinculo.get().chatId(),
                                            mensagemEtapa(trabalho).orElse(null),
                                            textoEtapa(trabalho),
                                            trabalho.id())));
                });
    }

    /** Id da mensagem de etapa: no trabalho em memória ou, se o envio o gravou depois, no banco. */
    private Optional<Long> mensagemEtapa(TrabalhoProcessamento trabalho) {
        Optional<String> valor =
                trabalho.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA)
                        .or(
                                () ->
                                        fila.buscarPorId(trabalho.id())
                                                .flatMap(
                                                        t ->
                                                                t.parametro(
                                                                        TrabalhoProcessamento
                                                                                .MENSAGEM_ETAPA)));
        valor.ifPresent(v -> trabalho.definirParametro(TrabalhoProcessamento.MENSAGEM_ETAPA, v));
        try {
            return valor.map(Long::valueOf);
        } catch (NumberFormatException invalido) {
            return Optional.empty();
        }
    }

    private String textoEtapa(TrabalhoProcessamento trabalho) {
        return "<i>"
                + ApresentadorTelegram.escapar(
                        apresentador.formatacao().etapa(trabalho.etapa().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ demora

    @Override
    public void avisarDemora(TrabalhoProcessamento trabalho) {
        if (trabalho.canalSolicitacao() != Canal.TELEGRAM) {
            return;
        }
        protegido(
                "demora",
                () ->
                        vinculos.buscarPorProfessorId(trabalho.professorId())
                                .ifPresent(
                                        vinculo ->
                                                publicacao.publicar(
                                                        "demora:" + trabalho.id(),
                                                        trabalho.professorId(),
                                                        List.of(
                                                                apresentador.simples(
                                                                        vinculo.chatId(),
                                                                        "PROCESSAMENTO_DEMORADO")))));
    }

    // ------------------------------------------------------------------ resultado

    @Override
    public void notificarResultado(TrabalhoProcessamento trabalho) {
        if (trabalho.resultado() == null || trabalho.resultado() == ResultadoTrabalho.DESCARTADO) {
            return;
        }
        protegido(
                "resultado",
                () -> {
                    if (trabalho.canalSolicitacao() == Canal.WEB) {
                        avisoDaWeb(trabalho);
                    } else {
                        resultadoNoChat(trabalho);
                    }
                });
    }

    private void avisoDaWeb(TrabalhoProcessamento trabalho) {
        Optional<VinculoTelegram> vinculo = vinculos.buscarPorProfessorId(trabalho.professorId());
        if (vinculo.isEmpty()) {
            return;
        }
        String codigo =
                switch (trabalho.resultado()) {
                    case PRONTO_PARA_CONFERENCIA -> "AVISO_RESULTADO_WEB_PRONTO";
                    case PERGUNTAS_PENDENTES -> "AVISO_RESULTADO_WEB_PERGUNTAS";
                    case NOVA_VERSAO -> "AVISO_RESULTADO_WEB_NOVA_VERSAO";
                    case FALHA_ESTRUTURACAO, AUDIO_NAO_PROCESSAVEL, FALHA_CONSOLIDACAO ->
                            "AVISO_RESULTADO_WEB_FALHA";
                    default -> null;
                };
        if (codigo == null) {
            return;
        }
        String aluno = alunos.buscarPorId(trabalho.alunoId()).map(a -> a.nome()).orElse("");
        boolean semestral = trabalho.relatorioTipo() == TipoRelatorio.SEMESTRAL;
        Integer versao = null;
        if (trabalho.relatorioId() != null) {
            versao =
                    semestral
                            ? apresentacaoSemestral
                                    .obter(trabalho.professorId(), trabalho.relatorioId())
                                    .relatorio()
                                    .versaoVigente()
                            : apresentacao
                                    .obter(trabalho.professorId(), trabalho.relatorioId())
                                    .relatorio()
                                    .versaoVigente();
        }
        Teclado teclado =
                trabalho.relatorioId() == null
                        ? Teclado.NENHUM
                        : Teclado.de(
                                apresentador.botao(
                                        "CONTINUAR_NO_CHAT",
                                        Map.of(),
                                        "foco",
                                        (semestral ? "s" : "a") + trabalho.relatorioId()));
        publicacao.publicar(
                "resultado:" + trabalho.id(),
                trabalho.professorId(),
                List.of(
                        new Mensagem(
                                vinculo.get().chatId(),
                                apresentador.texto(
                                        codigo,
                                        Map.of(
                                                "aluno",
                                                aluno,
                                                "versao",
                                                versao == null ? "" : versao)),
                                teclado)));
    }

    /**
     * Resultado de um trabalho pedido no chat: grava o foco na conversa (com nova tentativa diante
     * de outro update simultâneo) e só depois envia as mensagens.
     */
    private void resultadoNoChat(TrabalhoProcessamento trabalho) {
        for (int tentativa = 1; tentativa <= TENTATIVAS_GRAVACAO; tentativa++) {
            Optional<VinculoTelegram> vinculo =
                    vinculos.buscarPorProfessorId(trabalho.professorId());
            if (vinculo.isEmpty()) {
                return;
            }
            List<SaidaTelegram> saidas = saidasDoResultado(trabalho, vinculo.get());
            try {
                vinculos.salvar(vinculo.get());
            } catch (OptimisticLockingFailureException | ConflitoConcorrenciaExcecao conflito) {
                if (tentativa < TENTATIVAS_GRAVACAO) {
                    continue;
                }
                LOG.warn("telegram-foco-nao-gravado trabalhoId={}", trabalho.id());
            }
            List<SaidaTelegram> todas = new ArrayList<>();
            etapaPronta(trabalho, vinculo.get().chatId()).ifPresent(todas::add);
            todas.addAll(saidas);
            if (!todas.isEmpty()) {
                publicacao.publicar("resultado:" + trabalho.id(), trabalho.professorId(), todas);
            }
            return;
        }
    }

    /** A mensagem de etapa vira "Pronto" (editada no lugar) antes das mensagens do resultado. */
    private Optional<SaidaTelegram> etapaPronta(TrabalhoProcessamento trabalho, long chatId) {
        if (trabalho.resultado() != ResultadoTrabalho.PRONTO_PARA_CONFERENCIA
                && trabalho.resultado() != ResultadoTrabalho.NOVA_VERSAO) {
            return Optional.empty();
        }
        return Optional.of(
                new SaidaTelegram.MensagemEtapa(
                        chatId,
                        mensagemEtapa(trabalho).orElse(null),
                        "<i>" + apresentador.texto("ETAPA_PRONTO") + "</i>",
                        trabalho.id()));
    }

    List<SaidaTelegram> saidasDoResultado(TrabalhoProcessamento trabalho, VinculoTelegram vinculo) {
        long chatId = vinculo.chatId();
        ConversaTelegram conversa = vinculo.conversa();
        Optional<String> entendido = textoEntendido(trabalho);
        switch (trabalho.resultado()) {
            case AUDIO_NAO_PROCESSAVEL -> {
                return List.of(apresentador.simples(chatId, "AUDIO_NAO_PROCESSAVEL"));
            }
            case FALHA_CONSOLIDACAO -> {
                return List.of(apresentador.simples(chatId, "FALHA_CONSOLIDACAO"));
            }
            default -> {
                // demais resultados têm relatório de aula
            }
        }
        if (trabalho.relatorioId() == null) {
            return List.of();
        }
        if (trabalho.relatorioTipo() == TipoRelatorio.SEMESTRAL) {
            return saidasDoSemestral(trabalho, chatId, conversa, entendido);
        }
        VisaoRelatorioAula visao;
        try {
            visao = apresentacao.obter(trabalho.professorId(), trabalho.relatorioId());
        } catch (ExcecaoDominio naoEncontrado) {
            return List.of();
        }
        RelatorioAula relatorio = visao.relatorio();
        int vigente = relatorio.versaoVigente();
        switch (trabalho.resultado()) {
            case SEM_ALTERACAO_DE_CONTEUDO, PEDIDO_DE_ACAO_IGNORADO, FALHA_ALTERACAO -> {
                conversa.focar(new RelatorioFoco(TipoRelatorio.AULA, relatorio.id()), vigente);
                String texto =
                        apresentador.texto(trabalho.resultado().name(), Map.of("versao", vigente))
                                + entendido
                                        .map(
                                                t ->
                                                        "\n"
                                                                + apresentador.texto(
                                                                        "TEXTO_ENTENDIDO",
                                                                        Map.of("texto", t)))
                                        .orElse("");
                return List.of(apresentacao.acoesConferencia(chatId, vigente, texto));
            }
            default -> {
                return new ArrayList<>(
                        apresentacao.apresentar(
                                chatId,
                                trabalho.professorId(),
                                visao,
                                conversa,
                                prefixo(trabalho, vigente, entendido)));
            }
        }
    }

    private List<SaidaTelegram> saidasDoSemestral(
            TrabalhoProcessamento trabalho,
            long chatId,
            ConversaTelegram conversa,
            Optional<String> entendido) {
        String prefixo =
                switch (trabalho.resultado()) {
                    case NOVA_VERSAO,
                            SEM_ALTERACAO_DE_CONTEUDO,
                            PEDIDO_DE_ACAO_IGNORADO,
                            FALHA_ALTERACAO -> {
                        int vigente =
                                apresentacaoSemestral
                                        .obter(trabalho.professorId(), trabalho.relatorioId())
                                        .relatorio()
                                        .versaoVigente();
                        String codigo =
                                trabalho.resultado() == ResultadoTrabalho.NOVA_VERSAO
                                        ? "ALTERACAO_APLICADA"
                                        : trabalho.resultado().name();
                        yield apresentador.texto(codigo, Map.of("versao", vigente))
                                + entendido
                                        .map(
                                                t ->
                                                        "\n"
                                                                + apresentador.texto(
                                                                        "TEXTO_ENTENDIDO",
                                                                        Map.of("texto", t)))
                                        .orElse("");
                    }
                    case VERSAO_DESATUALIZADA ->
                            apresentador.texto(
                                    "VERSAO_DESATUALIZADA",
                                    Map.of(
                                            "versaoVigente",
                                            apresentacaoSemestral
                                                    .obter(
                                                            trabalho.professorId(),
                                                            trabalho.relatorioId())
                                                    .relatorio()
                                                    .versaoVigente()));
                    default -> null;
                };
        try {
            return new ArrayList<>(
                    apresentacaoSemestral.apresentar(
                            chatId,
                            trabalho.professorId(),
                            trabalho.relatorioId(),
                            conversa,
                            prefixo));
        } catch (ExcecaoDominio naoEncontrado) {
            return List.of();
        }
    }

    private String prefixo(
            TrabalhoProcessamento trabalho, int vigente, Optional<String> entendido) {
        return switch (trabalho.resultado()) {
            case NOVA_VERSAO ->
                    apresentador.texto("ALTERACAO_APLICADA", Map.of("versao", vigente))
                            + entendido
                                    .map(
                                            t ->
                                                    "\n"
                                                            + apresentador.texto(
                                                                    "TEXTO_ENTENDIDO",
                                                                    Map.of("texto", t)))
                                    .orElse("");
            case VERSAO_DESATUALIZADA ->
                    ApresentadorTelegram.escapar(
                            apresentador.rotulo(
                                    CodigoErro.VERSAO_DESATUALIZADA.chaveMensagem(),
                                    Map.of("versaoVigente", vigente)));
            case FALHA_RESPOSTA -> apresentador.texto("FALHA_RESPOSTA");
            case PRONTO_PARA_CONFERENCIA, PERGUNTAS_PENDENTES ->
                    trabalho.tipo() == TipoTrabalho.RESPOSTA_PERGUNTAS && entendido.isPresent()
                            ? apresentador.texto(
                                    "RESPOSTA_INCORPORADA", Map.of("texto", entendido.get()))
                            : null;
            default -> null;
        };
    }

    private static Optional<String> textoEntendido(TrabalhoProcessamento trabalho) {
        return trabalho.parametro(TrabalhoProcessamento.TEXTO_ENTENDIDO)
                .or(() -> trabalho.parametro(TrabalhoProcessamento.TEXTO))
                .filter(t -> !t.isBlank());
    }

    private static void protegido(String aviso, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException erro) {
            LOG.warn(
                    "telegram-notificacao-falhou aviso={} tipo={}",
                    aviso,
                    erro.getClass().getSimpleName());
        }
    }
}
