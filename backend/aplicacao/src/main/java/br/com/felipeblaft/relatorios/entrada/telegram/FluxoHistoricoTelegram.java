package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.DocumentoObtido;
import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso.Historico;
import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso.ItemHistorico;
import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.FluxoConversa;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Documento;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Histórico pelo bot (sequência 5.5, estado #9): lista numerada dos relatórios aprovados, 10 por
 * página com "Mais", com os mesmos rótulos da Web; tocar num item envia o PDF oficial.
 */
@Component
@Order(9)
public class FluxoHistoricoTelegram implements FluxoTelegram {

    static final int POR_PAGINA = 10;
    static final String ALUNO = "histAluno";
    static final String ITENS = "histItens";

    private final ConsultarHistoricoAlunoCasoDeUso consultar;
    private final ObterDocumentoRelatorioCasoDeUso obterDocumento;
    private final ApresentadorTelegram apresentador;

    public FluxoHistoricoTelegram(
            ConsultarHistoricoAlunoCasoDeUso consultar,
            ObterDocumentoRelatorioCasoDeUso obterDocumento,
            ApresentadorTelegram apresentador) {
        this.consultar = consultar;
        this.obterDocumento = obterDocumento;
        this.apresentador = apresentador;
    }

    @Override
    public boolean aoEscolherAluno(ContextoTelegram contexto, AlunoResumo aluno) {
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.fluxo() != FluxoConversa.HISTORICO) {
            return false;
        }
        conversa.limparFoco();
        Historico historico = consultar.executar(contexto.professor().id(), aluno.id());
        if (historico.itens().isEmpty()) {
            conversa.encerrarFluxo();
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(),
                            apresentador.texto("HISTORICO_VAZIO"),
                            Teclado.de(apresentador.botao("BOTAO_REGISTRAR_AULA", "reg"))));
            return true;
        }
        conversa.definirEtapa(EtapaFluxoConversa.ESCOLHENDO_ITEM_HISTORICO);
        conversa.guardar(ALUNO, aluno.id().toString());
        conversa.guardar(
                ITENS,
                String.join(
                        ",",
                        historico.itens().stream()
                                .map(
                                        i ->
                                                (i.tipo() == TipoRelatorio.AULA ? "a" : "s")
                                                        + i.relatorioId())
                                .toList()));
        mostrarPagina(contexto, historico, 0);
        return true;
    }

    private void mostrarPagina(ContextoTelegram contexto, Historico historico, int pagina) {
        List<ItemHistorico> itens = historico.itens();
        int inicio = pagina * POR_PAGINA;
        int fim = Math.min(itens.size(), inicio + POR_PAGINA);
        StringBuilder texto =
                new StringBuilder("<b>")
                        .append(
                                apresentador.texto(
                                        "HISTORICO_TITULO",
                                        Map.of("aluno", historico.aluno().nome())))
                        .append("</b>");
        List<List<Botao>> linhas = new ArrayList<>();
        for (int i = inicio; i < fim; i++) {
            ItemHistorico item = itens.get(i);
            String rotulo = apresentador.formatacao().rotuloHistorico(item);
            texto.append('\n')
                    .append(i + 1)
                    .append(". ")
                    .append(ApresentadorTelegram.escapar(rotulo))
                    .append(" · ")
                    .append(
                            ApresentadorTelegram.escapar(
                                    apresentador.formatacao().aprovadoEm(item.aprovadoEm())));
            linhas.add(
                    List.of(
                            new Botao(
                                    rotulo,
                                    DadosCallbackCodificador.codificar(
                                            "hitem", Integer.toString(i)))));
        }
        if (fim < itens.size()) {
            linhas.add(
                    List.of(
                            apresentador.botao(
                                    "BOTAO_MAIS",
                                    Map.of(),
                                    "hmais",
                                    Integer.toString(pagina + 1))));
        }
        linhas.add(List.of(apresentador.botao("BOTAO_INICIO", "inicio")));
        contexto.responder(new Mensagem(contexto.chatId(), texto.toString(), new Teclado(linhas)));
    }

    @Override
    public boolean aoCallback(ContextoTelegram contexto, String acao, String argumento) {
        ConversaTelegram conversa = contexto.conversa();
        if (conversa.etapa() != EtapaFluxoConversa.ESCOLHENDO_ITEM_HISTORICO) {
            return false;
        }
        Optional<UUID> aluno = conversa.dado(ALUNO).map(UUID::fromString);
        Integer numero = numero(argumento);
        if (aluno.isEmpty() || numero == null) {
            return false;
        }
        switch (acao) {
            case "hmais" -> {
                Historico historico = consultar.executar(contexto.professor().id(), aluno.get());
                if (numero < 0 || numero * POR_PAGINA >= historico.itens().size()) {
                    return false;
                }
                mostrarPagina(contexto, historico, numero);
                return true;
            }
            case "hitem" -> {
                List<String> itens =
                        conversa.dado(ITENS).map(s -> List.of(s.split(","))).orElse(List.of());
                if (numero < 0 || numero >= itens.size()) {
                    return false;
                }
                String item = itens.get(numero);
                TipoRelatorio tipo =
                        item.charAt(0) == 'a' ? TipoRelatorio.AULA : TipoRelatorio.SEMESTRAL;
                DocumentoObtido documento =
                        obterDocumento.oficial(
                                contexto.professor().id(),
                                tipo,
                                UUID.fromString(item.substring(1)));
                contexto.responder(
                        new Documento(
                                contexto.chatId(),
                                documento.conteudo(),
                                documento.nomeArquivo(),
                                null));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return contexto.conversa().etapa() == EtapaFluxoConversa.ESCOLHENDO_ITEM_HISTORICO
                ? apresentador.rotulo("RECEBER_ALUNO")
                : null;
    }

    private static Integer numero(String argumento) {
        try {
            return argumento == null ? null : Integer.valueOf(argumento);
        } catch (NumberFormatException invalido) {
            return null;
        }
    }
}
