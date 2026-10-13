package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Documento;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.History;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.HistoryItem;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
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

    private final QueryStudentHistoryUseCase consultar;
    private final GetReportDocumentUseCase obterDocumento;
    private final ApresentadorTelegram apresentador;

    public FluxoHistoricoTelegram(
            QueryStudentHistoryUseCase consultar,
            GetReportDocumentUseCase obterDocumento,
            ApresentadorTelegram apresentador) {
        this.consultar = consultar;
        this.obterDocumento = obterDocumento;
        this.apresentador = apresentador;
    }

    @Override
    public boolean aoEscolherAluno(ContextoTelegram contexto, StudentSummary aluno) {
        TelegramConversation conversa = contexto.conversa();
        if (conversa.flow() != ConversationFlow.HISTORICO) {
            return false;
        }
        conversa.cleanFocus();
        History historico = consultar.run(contexto.professor().id(), aluno.id());
        if (historico.items().isEmpty()) {
            conversa.endFlow();
            contexto.responder(
                    new Mensagem(
                            contexto.chatId(),
                            apresentador.texto("HISTORICO_VAZIO"),
                            Teclado.de(apresentador.botao("BOTAO_REGISTRAR_AULA", "reg"))));
            return true;
        }
        conversa.defineStep(ConversationFlowStep.ESCOLHENDO_ITEM_HISTORICO);
        conversa.store(ALUNO, aluno.id().toString());
        conversa.store(
                ITENS,
                String.join(
                        ",",
                        historico.items().stream()
                                .map(i -> (i.type() == ReportType.AULA ? "a" : "s") + i.reportId())
                                .toList()));
        mostrarPagina(contexto, historico, 0);
        return true;
    }

    private void mostrarPagina(ContextoTelegram contexto, History historico, int pagina) {
        List<HistoryItem> itens = historico.items();
        int inicio = pagina * POR_PAGINA;
        int fim = Math.min(itens.size(), inicio + POR_PAGINA);
        StringBuilder texto =
                new StringBuilder("<b>")
                        .append(
                                apresentador.texto(
                                        "HISTORICO_TITULO",
                                        Map.of("aluno", historico.student().name())))
                        .append("</b>");
        List<List<Botao>> linhas = new ArrayList<>();
        for (int i = inicio; i < fim; i++) {
            HistoryItem item = itens.get(i);
            String rotulo = apresentador.formatacao().rotuloHistorico(item);
            texto.append('\n')
                    .append(i + 1)
                    .append(". ")
                    .append(ApresentadorTelegram.escapar(rotulo))
                    .append(" · ")
                    .append(
                            ApresentadorTelegram.escapar(
                                    apresentador.formatacao().aprovadoEm(item.approvedAt())));
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
        TelegramConversation conversa = contexto.conversa();
        if (conversa.step() != ConversationFlowStep.ESCOLHENDO_ITEM_HISTORICO) {
            return false;
        }
        Optional<UUID> aluno = conversa.data(ALUNO).map(UUID::fromString);
        Integer numero = numero(argumento);
        if (aluno.isEmpty() || numero == null) {
            return false;
        }
        switch (acao) {
            case "hmais" -> {
                History historico = consultar.run(contexto.professor().id(), aluno.get());
                if (numero < 0 || numero * POR_PAGINA >= historico.items().size()) {
                    return false;
                }
                mostrarPagina(contexto, historico, numero);
                return true;
            }
            case "hitem" -> {
                List<String> itens =
                        conversa.data(ITENS).map(s -> List.of(s.split(","))).orElse(List.of());
                if (numero < 0 || numero >= itens.size()) {
                    return false;
                }
                String item = itens.get(numero);
                ReportType tipo = item.charAt(0) == 'a' ? ReportType.AULA : ReportType.SEMESTRAL;
                ObtainedDocument documento =
                        obterDocumento.official(
                                contexto.professor().id(),
                                tipo,
                                UUID.fromString(item.substring(1)));
                contexto.responder(
                        new Documento(
                                contexto.chatId(),
                                documento.content(),
                                documento.fileName(),
                                null,
                                ReferenciaDocumento.oficial(
                                        contexto.professor().id(),
                                        tipo,
                                        UUID.fromString(item.substring(1))),
                                apresentador.avisoPdfIndisponivel(
                                        contexto.chatId(), "hitem", Integer.toString(numero))));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public String oQuePossoReceber(ContextoTelegram contexto) {
        return contexto.conversa().step() == ConversationFlowStep.ESCOLHENDO_ITEM_HISTORICO
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
