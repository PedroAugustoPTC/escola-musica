package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.AttendanceAndStudy;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.FamilySupport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.Items;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.Musicality;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.NextSemesterPlan;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.ReadingAndMemorization;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent.Technique;

/**
 * Saída da IA para o semestral (contracts/portas-provedores.md §6.2). Não há Informações Gerais: se
 * a IA mandar essa chave, ela é descartada na leitura (FR-044).
 */
public record AiSemesterReportOutput(
        AttendanceAndStudy attendanceAndStudy,
        Technique technique,
        Musicality musicality,
        ReadingAndMemorization readingAndMemorization,
        Items attentionPoints,
        Items pedagogicalStrategies,
        FamilySupport familySupport,
        NextSemesterPlan nextSemesterPlan,
        String finalOpinion,
        Boolean noContentChange,
        String actionRequest) {

    public SemesterReportContent content() {
        return new SemesterReportContent(
                attendanceAndStudy,
                technique,
                musicality,
                readingAndMemorization,
                attentionPoints,
                pedagogicalStrategies,
                familySupport,
                nextSemesterPlan,
                finalOpinion);
    }

    public static AiSemesterReportOutput of(
            SemesterReportContent content, boolean withoutChange, String actionRequest) {
        return new AiSemesterReportOutput(
                content.attendanceAndStudy(),
                content.technique(),
                content.musicality(),
                content.readingAndMemorization(),
                content.attentionPoints(),
                content.pedagogicalStrategies(),
                content.familySupport(),
                content.nextSemesterPlan(),
                content.finalOpinion(),
                withoutChange,
                actionRequest);
    }

    public boolean withoutChange() {
        return Boolean.TRUE.equals(noContentChange);
    }

    public boolean indicatedActionRequest() {
        return actionRequest != null && !AiLessonReportOutput.NONE.equals(actionRequest);
    }
}
