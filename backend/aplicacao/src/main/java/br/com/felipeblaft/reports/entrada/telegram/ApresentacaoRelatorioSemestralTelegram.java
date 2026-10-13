package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.reports.configuracao.SecoesRelatorioSemestral.Campo;
import br.com.felipeblaft.reports.configuracao.SecoesRelatorioSemestral.Secao;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Documento;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apresenta o semestral no chat (sequência 5.4): conferência com "N relatórios de aula foram
 * considerados", o aviso de relatórios em aberto, as 10 seções em texto, o PDF e as mesmas ações da
 * conferência de aula. Põe o semestral em foco com a versão apresentada.
 */
@Component
public class ApresentacaoRelatorioSemestralTelegram {

    private final GetSemesterReportUseCase obterRelatorio;
    private final GetReportDocumentUseCase obterDocumento;
    private final ApresentadorTelegram apresentador;
    private final ApresentacaoRelatorioAulaTelegram apresentacaoAula;
    private final SecoesRelatorioSemestral secoes;

    public ApresentacaoRelatorioSemestralTelegram(
            GetSemesterReportUseCase obterRelatorio,
            GetReportDocumentUseCase obterDocumento,
            ApresentadorTelegram apresentador,
            ApresentacaoRelatorioAulaTelegram apresentacaoAula,
            SecoesRelatorioSemestral secoes) {
        this.obterRelatorio = obterRelatorio;
        this.obterDocumento = obterDocumento;
        this.apresentador = apresentador;
        this.apresentacaoAula = apresentacaoAula;
        this.secoes = secoes;
    }

    public SemesterReportView obter(UUID professorId, UUID relatorioId) {
        return obterRelatorio.run(professorId, relatorioId);
    }

    public static boolean emProcessamento(SemesterReportView visao) {
        return visao.jobInProgress().isPresent() || visao.report().step() != null;
    }

    public String processando(SemesterReportView visao) {
        String etapa =
                visao.jobInProgress()
                        .map(t -> t.step().name())
                        .or(() -> Optional.ofNullable(visao.report().step()).map(Enum::name))
                        .orElse("APLICANDO_ALTERACAO");
        return apresentador.texto(
                "PROCESSAMENTO_EM_ANDAMENTO",
                Map.of("etapa", apresentador.formatacao().etapa(etapa)));
    }

    public List<SaidaTelegram> apresentar(
            long chatId,
            UUID professorId,
            UUID relatorioId,
            TelegramConversation conversa,
            String prefixo) {
        SemesterReportView visao = obter(professorId, relatorioId);
        SemesterReport relatorio = visao.report();
        if (!relatorio.status().isOpen()) {
            conversa.cleanFocus();
            String codigo =
                    relatorio.status() == ReportStatus.APROVADO
                            ? "RELATORIO_JA_APROVADO"
                            : "RELATORIO_CANCELADO_ERRO";
            return List.of(
                    new Mensagem(
                            chatId,
                            ApresentacaoRelatorioAulaTelegram.comPrefixo(
                                    prefixo, apresentador.texto(codigo)),
                            apresentador.acoesInicio()));
        }
        conversa.focus(
                new FocusReport(ReportType.SEMESTRAL, relatorio.id()), relatorio.currentVersion());
        if (emProcessamento(visao)) {
            return List.of(
                    new Mensagem(
                            chatId,
                            ApresentacaoRelatorioAulaTelegram.comPrefixo(
                                    prefixo, processando(visao)),
                            Teclado.NENHUM));
        }
        List<SaidaTelegram> saidas = new ArrayList<>();
        saidas.add(
                new Mensagem(
                        chatId,
                        ApresentacaoRelatorioAulaTelegram.comPrefixo(
                                prefixo, textoConferencia(relatorio)),
                        Teclado.NENHUM));
        documentoConferencia(chatId, professorId, relatorio)
                .ifPresentOrElse(
                        saidas::add,
                        () -> saidas.add(apresentador.simples(chatId, "CONFERENCIA_PDF_GERANDO")));
        saidas.add(apresentacaoAula.acoesConferencia(chatId, relatorio.currentVersion(), null));
        return saidas;
    }

    public Optional<SaidaTelegram> documentoConferencia(
            long chatId, UUID professorId, SemesterReport relatorio) {
        try {
            GetReportDocumentUseCase.ObtainedDocument documento =
                    obterDocumento.review(
                            professorId,
                            ReportType.SEMESTRAL,
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
                                    ReportType.SEMESTRAL,
                                    relatorio.id(),
                                    relatorio.currentVersion()),
                            apresentador.avisoPdfIndisponivel(chatId, "pdf", null)));
        } catch (DomainException semDocumento) {
            return Optional.empty();
        }
    }

    public String textoConferencia(SemesterReport relatorio) {
        StringBuilder texto = new StringBuilder();
        texto.append("<b>").append(apresentador.texto("CONFERENCIA_TITULO")).append("</b>\n");
        texto.append(apresentador.texto("CABECALHO_ALUNO"))
                .append(": ")
                .append(ApresentadorTelegram.escapar(relatorio.generalInformation().nome()))
                .append('\n');
        texto.append(apresentador.texto("CABECALHO_PERIODO"))
                .append(": ")
                .append(
                        ApresentadorTelegram.escapar(
                                secoes.rotuloPeriodo(relatorio.period().key())))
                .append('\n');
        texto.append(
                apresentador.texto(
                        "CONFERENCIA_VERSAO", Map.of("versao", relatorio.currentVersion())));
        texto.append("\n\n")
                .append(
                        apresentador.texto(
                                "AULAS_CONSIDERADAS",
                                Map.of("quantidade", relatorio.consideredLessonCount())));
        if (relatorio.openReportCount() > 0) {
            texto.append('\n')
                    .append(
                            apresentador.texto(
                                    "HA_RELATORIOS_EM_ABERTO",
                                    Map.of("quantidade", relatorio.openReportCount())));
        }
        for (Secao secao : secoes.secoes(relatorio.generalInformation(), relatorio.content())) {
            texto.append("\n\n<b>")
                    .append(secao.getNumero())
                    .append(". ")
                    .append(ApresentadorTelegram.escapar(secao.getTitulo()))
                    .append("</b>");
            for (Campo campo : secao.getCampos()) {
                texto.append('\n');
                if (campo.getRotulo() != null) {
                    texto.append("<i>")
                            .append(ApresentadorTelegram.escapar(campo.getRotulo()))
                            .append("</i>: ");
                }
                if (campo.vazio()) {
                    texto.append(apresentador.texto("SECAO_VAZIA"));
                } else if (campo.getItens().isEmpty()) {
                    texto.append(ApresentadorTelegram.escapar(campo.getTexto()));
                } else {
                    for (String item : campo.getItens()) {
                        texto.append("\n• ").append(ApresentadorTelegram.escapar(item));
                    }
                }
            }
        }
        return texto.toString();
    }
}
