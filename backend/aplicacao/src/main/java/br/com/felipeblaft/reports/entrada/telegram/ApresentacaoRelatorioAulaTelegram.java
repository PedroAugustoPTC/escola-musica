package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Documento;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apresenta a etapa atual de um relatório de aula no chat (sequência 5.2): processamento,
 * perguntas, conferência com o PDF, falha ou situação final. Ao apresentar perguntas ou
 * conferência, põe o relatório em foco e grava a versão apresentada na conversa (FR-027b, FR-033).
 */
@Component
public class ApresentacaoRelatorioAulaTelegram {

    private final GetLessonReportUseCase obterRelatorio;
    private final GetReportDocumentUseCase obterDocumento;
    private final ApresentadorTelegram apresentador;

    public ApresentacaoRelatorioAulaTelegram(
            GetLessonReportUseCase obterRelatorio,
            GetReportDocumentUseCase obterDocumento,
            ApresentadorTelegram apresentador) {
        this.obterRelatorio = obterRelatorio;
        this.obterDocumento = obterDocumento;
        this.apresentador = apresentador;
    }

    public LessonReportView obter(UUID professorId, UUID relatorioId) {
        return obterRelatorio.run(professorId, relatorioId);
    }

    /** Apresenta a etapa atual; {@code prefixo} (já escapado) vem antes, se houver. */
    public List<SaidaTelegram> apresentar(
            long chatId,
            UUID professorId,
            UUID relatorioId,
            TelegramConversation conversa,
            String prefixo) {
        return apresentar(chatId, professorId, obter(professorId, relatorioId), conversa, prefixo);
    }

    public List<SaidaTelegram> apresentar(
            long chatId,
            UUID professorId,
            LessonReportView visao,
            TelegramConversation conversa,
            String prefixo) {
        LessonReport relatorio = visao.report();
        if (relatorio.status() == ReportStatus.APROVADO
                || relatorio.status() == ReportStatus.CANCELADO) {
            conversa.cleanFocus();
            String codigo =
                    relatorio.status() == ReportStatus.APROVADO
                            ? "RELATORIO_JA_APROVADO"
                            : "RELATORIO_CANCELADO_ERRO";
            return List.of(
                    new Mensagem(
                            chatId,
                            comPrefixo(prefixo, apresentador.texto(codigo)),
                            apresentador.acoesInicio()));
        }
        conversa.focus(
                new FocusReport(ReportType.AULA, relatorio.id()), relatorio.currentVersion());
        if (emProcessamento(visao)) {
            return List.of(
                    new Mensagem(chatId, comPrefixo(prefixo, processando(visao)), Teclado.NENHUM));
        }
        return switch (relatorio.status()) {
            case AGUARDANDO_RESPOSTAS -> List.of(perguntas(chatId, relatorio, prefixo));
            case AGUARDANDO_CONFERENCIA -> conferencia(chatId, professorId, visao, prefixo);
            default -> List.of(falha(chatId, prefixo));
        };
    }

    public static boolean emProcessamento(LessonReportView visao) {
        return visao.jobInProgress().isPresent()
                || visao.report().step() != null
                || visao.report().status() == ReportStatus.EM_PROCESSAMENTO;
    }

    public String processando(LessonReportView visao) {
        String etapa =
                visao.jobInProgress()
                        .map(t -> t.step().name())
                        .or(() -> Optional.ofNullable(visao.report().step()).map(Enum::name))
                        .orElse("ESTRUTURANDO");
        return apresentador.texto(
                "PROCESSAMENTO_EM_ANDAMENTO",
                Map.of("etapa", apresentador.formatacao().etapa(etapa)));
    }

    public Mensagem perguntas(long chatId, LessonReport relatorio, String prefixo) {
        StringBuilder numeradas = new StringBuilder();
        List<String> perguntas = relatorio.pendingQuestions();
        for (int i = 0; i < perguntas.size(); i++) {
            numeradas.append('\n').append(i + 1).append(". ").append(perguntas.get(i));
        }
        String texto =
                apresentador.texto("PERGUNTAS_PENDENTES", Map.of("perguntasNumeradas", numeradas))
                        + "\n\n<i>"
                        + apresentador.texto("LEMBRETE_FOCO")
                        + "</i>";
        return new Mensagem(
                chatId,
                comPrefixo(prefixo, texto),
                Teclado.de(
                        apresentador.botao("BOTAO_CANCELAR_RELATORIO", "canc"),
                        apresentador.botao("BOTAO_REGISTRAR_AULA", "reg")));
    }

    /** Cabeçalho e seções em texto, o PDF como documento e as ações com o lembrete de foco. */
    public List<SaidaTelegram> conferencia(
            long chatId, UUID professorId, LessonReportView visao, String prefixo) {
        LessonReport relatorio = visao.report();
        List<SaidaTelegram> saidas = new ArrayList<>();
        saidas.add(
                new Mensagem(chatId, comPrefixo(prefixo, textoConferencia(visao)), Teclado.NENHUM));
        documentoConferencia(chatId, professorId, relatorio)
                .ifPresentOrElse(
                        saidas::add,
                        () -> saidas.add(apresentador.simples(chatId, "CONFERENCIA_PDF_GERANDO")));
        saidas.add(acoesConferencia(chatId, relatorio.currentVersion(), null));
        return saidas;
    }

    public Optional<SaidaTelegram> documentoConferencia(
            long chatId, UUID professorId, LessonReport relatorio) {
        try {
            GetReportDocumentUseCase.ObtainedDocument documento =
                    obterDocumento.review(
                            professorId,
                            ReportType.AULA,
                            relatorio.id(),
                            relatorio.currentVersion());
            return Optional.of(
                    new Documento(
                            chatId,
                            documento.content(),
                            documento.fileName(),
                            null,
                            ReferenciaDocumento.conferencia(
                                    professorId,
                                    ReportType.AULA,
                                    relatorio.id(),
                                    relatorio.currentVersion()),
                            apresentador.avisoPdfIndisponivel(chatId, "pdf", null)));
        } catch (DomainException semDocumento) {
            return Optional.empty();
        }
    }

    /** Lembrete de foco com os botões da conferência ({@code aprov} cita a versão apresentada). */
    public Mensagem acoesConferencia(long chatId, int versao, String prefixo) {
        String texto = "<i>" + apresentador.texto("LEMBRETE_FOCO") + "</i>";
        return new Mensagem(
                chatId,
                comPrefixo(prefixo, texto),
                new Teclado(
                        List.of(
                                List.of(
                                        new Botao(
                                                apresentador.rotulo("BOTAO_APROVAR"),
                                                DadosCallbackCodificador.codificar(
                                                        "aprov", Integer.toString(versao)))),
                                List.of(apresentador.botao("BOTAO_SOLICITAR_ALTERACAO", "alt")),
                                List.of(
                                        apresentador.botao("BOTAO_VER_PDF", "pdf"),
                                        apresentador.botao("BOTAO_CANCELAR_RELATORIO", "canc")),
                                List.of(apresentador.botao("BOTAO_REGISTRAR_AULA", "reg")))));
    }

    public String textoConferencia(LessonReportView visao) {
        LessonReport relatorio = visao.report();
        LessonReportContent conteudo = relatorio.content();
        StringBuilder texto = new StringBuilder();
        texto.append("<b>").append(apresentador.texto("CONFERENCIA_TITULO")).append("</b>\n");
        linha(texto, "CABECALHO_ALUNO", visao.studentName());
        linha(texto, "CABECALHO_PROFESSOR", visao.teacherName());
        linha(
                texto,
                "CABECALHO_DATA_AULA",
                apresentador.formatacao().data(visao.lesson().lessonDate()));
        texto.append(
                apresentador.texto(
                        "CONFERENCIA_VERSAO", Map.of("versao", relatorio.currentVersion())));
        lista(texto, "SECAO_CONTEUDOS_TRABALHADOS", conteudo.conteudosTrabalhados());
        paragrafo(texto, "SECAO_EVOLUCAO", conteudo.evolucao());
        lista(texto, "SECAO_DIFICULDADES", conteudo.dificuldades());
        lista(texto, "SECAO_ATIVIDADES_PROPOSTAS", conteudo.atividadesPropostas());
        paragrafo(texto, "SECAO_OBSERVACOES", conteudo.observacoes());
        return texto.toString();
    }

    public Mensagem falha(long chatId, String prefixo) {
        return new Mensagem(
                chatId,
                comPrefixo(prefixo, apresentador.texto("FALHA_PROCESSAMENTO_OPCOES")),
                Teclado.de(
                        apresentador.botao("BOTAO_TENTAR_NOVAMENTE", "tent"),
                        apresentador.botao("BOTAO_CANCELAR_RELATORIO", "canc")));
    }

    private void linha(StringBuilder texto, String rotulo, String valor) {
        texto.append(apresentador.texto(rotulo))
                .append(": ")
                .append(ApresentadorTelegram.escapar(valor))
                .append('\n');
    }

    private void lista(StringBuilder texto, String secao, List<String> itens) {
        texto.append("\n\n<b>").append(apresentador.texto(secao)).append("</b>");
        if (itens.isEmpty()) {
            texto.append('\n').append(apresentador.texto("SECAO_VAZIA"));
        }
        for (String item : itens) {
            texto.append("\n• ").append(ApresentadorTelegram.escapar(item));
        }
    }

    private void paragrafo(StringBuilder texto, String secao, String valor) {
        texto.append("\n\n<b>")
                .append(apresentador.texto(secao))
                .append("</b>\n")
                .append(
                        valor == null || valor.isBlank()
                                ? apresentador.texto("SECAO_VAZIA")
                                : ApresentadorTelegram.escapar(valor));
    }

    static String comPrefixo(String prefixo, String texto) {
        return prefixo == null || prefixo.isBlank() ? texto : prefixo + "\n\n" + texto;
    }
}
