package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.report.VersionContent;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.AulaEntidade;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.DocumentoPdfEntidade;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.InteracaoRelatorioEntidade;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import br.com.felipeblaft.reports.saida.persistencia.RelatorioEntidades.VersaoRelatorioEntidade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.core.type.TypeReference;

/** Conversão entre as entidades do relatório de aula e o domínio (seções em jsonb). */
final class RelatorioAulaMapeador {

    private static final TypeReference<List<String>> LISTA = new TypeReference<>() {};

    /** Leitores do conteúdo de versão por tipo de relatório (o semestral registra o seu). */
    static final Map<ReportType, Function<String, VersionContent>> LEITORES = new HashMap<>();

    static {
        LEITORES.put(
                ReportType.AULA, json -> JsonPersistencia.ler(json, LessonReportContent.class));
        LEITORES.put(
                ReportType.SEMESTRAL,
                json ->
                        JsonPersistencia.ler(
                                json,
                                br.com.felipeblaft.reports.domain.semester.SemesterReportContent
                                        .class));
    }

    private RelatorioAulaMapeador() {}

    static AulaEntidade paraEntidade(Lesson aula) {
        AulaEntidade e = new AulaEntidade();
        e.id = aula.id();
        e.professorId = aula.teacherId();
        e.alunoId = aula.studentId();
        e.dataAula = aula.lessonDate();
        e.criadoEm = aula.createdAt();
        return e;
    }

    static Lesson paraDominio(AulaEntidade e) {
        return new Lesson(e.id, e.professorId, e.alunoId, e.dataAula, e.criadoEm);
    }

    static RelatorioAulaEntidade paraEntidade(LessonReport r) {
        RelatorioAulaEntidade e = new RelatorioAulaEntidade();
        e.id = r.id();
        e.aulaId = r.lessonId();
        e.professorId = r.teacherId();
        e.alunoId = r.studentId();
        e.transcricao = r.transcription();
        e.conteudo = JsonPersistencia.escrever(r.content());
        e.perguntasPendentes = JsonPersistencia.escrever(r.pendingQuestions());
        e.rodadasPerguntas = r.questionRounds();
        e.status = r.status().name();
        e.etapaProcessamento = r.step() == null ? null : r.step().name();
        e.versaoVigente = r.currentVersion();
        e.canalOrigem = r.originChannel().name();
        e.motivoFalha = r.failureReason();
        e.criadoEm = r.createdAt();
        e.atualizadoEm = r.updatedAt();
        e.aprovadoEm = r.approvedAt();
        e.versaoAprovada = r.approvedVersion();
        e.canalAprovacao = r.approvalChannel() == null ? null : r.approvalChannel().name();
        e.canceladoEm = r.cancelledAt();
        e.tempoProcessamentoMs = r.processingTimeMs();
        e.bloqueioOtimista = r.version();
        return e;
    }

    static LessonReport paraDominio(RelatorioAulaEntidade e) {
        return new LessonReport(
                e.id,
                e.aulaId,
                e.professorId,
                e.alunoId,
                e.transcricao,
                JsonPersistencia.ler(e.conteudo, LessonReportContent.class),
                JsonPersistencia.ler(e.perguntasPendentes, LISTA),
                e.rodadasPerguntas,
                ReportStatus.valueOf(e.status),
                e.etapaProcessamento == null ? null : ReportStep.valueOf(e.etapaProcessamento),
                e.versaoVigente,
                Channel.valueOf(e.canalOrigem),
                e.motivoFalha,
                e.criadoEm,
                e.atualizadoEm,
                e.aprovadoEm,
                e.versaoAprovada,
                e.canalAprovacao == null ? null : Channel.valueOf(e.canalAprovacao),
                e.canceladoEm,
                e.tempoProcessamentoMs,
                e.bloqueioOtimista);
    }

    static VersaoRelatorioEntidade paraEntidade(ReportVersion v) {
        VersaoRelatorioEntidade e = new VersaoRelatorioEntidade();
        e.id = v.id();
        e.relatorioTipo = v.reportType().name();
        e.relatorioId = v.reportId();
        e.numero = v.number();
        e.conteudo = JsonPersistencia.escrever(v.content());
        e.origem = v.origin().name();
        e.interacaoId = v.interactionId();
        e.canal = v.channel().name();
        e.promptVersao = v.promptVersion();
        e.criadaEm = v.createdAt();
        return e;
    }

    static ReportVersion paraDominio(VersaoRelatorioEntidade e) {
        ReportType tipo = ReportType.valueOf(e.relatorioTipo);
        return new ReportVersion(
                e.id,
                tipo,
                e.relatorioId,
                e.numero,
                LEITORES.get(tipo).apply(e.conteudo),
                VersionOrigin.valueOf(e.origem),
                e.interacaoId,
                Channel.valueOf(e.canal),
                e.promptVersao,
                e.criadaEm);
    }

    static InteracaoRelatorioEntidade paraEntidade(ReportInteraction i) {
        InteracaoRelatorioEntidade e = new InteracaoRelatorioEntidade();
        e.id = i.id();
        e.relatorioTipo = i.reportType().name();
        e.relatorioId = i.reportId();
        e.tipo = i.type().name();
        e.canal = i.channel().name();
        e.meio = i.medium().name();
        e.texto = i.text();
        e.versaoBase = i.baseVersion();
        e.rodada = i.round();
        e.resultado = i.result().name();
        e.versaoResultante = i.resultingVersion();
        e.criadoEm = i.createdAt();
        return e;
    }

    static ReportInteraction paraDominio(InteracaoRelatorioEntidade e) {
        return new ReportInteraction(
                e.id,
                ReportType.valueOf(e.relatorioTipo),
                e.relatorioId,
                InteractionType.valueOf(e.tipo),
                Channel.valueOf(e.canal),
                Medium.valueOf(e.meio),
                e.texto,
                e.versaoBase,
                e.rodada,
                InteractionResult.valueOf(e.resultado),
                e.versaoResultante,
                e.criadoEm);
    }

    static DocumentoPdfEntidade paraEntidade(PdfDocument d) {
        DocumentoPdfEntidade e = new DocumentoPdfEntidade();
        e.id = d.id();
        e.versaoId = d.versionId();
        e.tipo = d.type().name();
        e.chaveArmazenamento = d.storageKey();
        e.tamanhoBytes = d.sizeBytes();
        e.sha256 = d.sha256();
        e.geradoEm = d.generatedAt();
        return e;
    }

    static PdfDocument paraDominio(DocumentoPdfEntidade e) {
        return new PdfDocument(
                e.id,
                e.versaoId,
                DocumentType.valueOf(e.tipo),
                e.chaveArmazenamento,
                e.tamanhoBytes,
                e.sha256,
                e.geradoEm);
    }
}
