package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import java.util.List;

/**
 * Seções 2 a 10 do relatório semestral como o contrato do Portal as expõe. Os nomes dos campos
 * seguem o contrato atual (português) até a fatia 4 da feature 003; o registro do núcleo já usa os
 * nomes novos, e só esta camada converte.
 */
public record SemesterReportContentDto(
        AttendanceAndStudyDto frequenciaEEstudo,
        TechniqueDto tecnica,
        MusicalityDto musicalidade,
        ReadingAndMemorizationDto leituraEMemorizacao,
        ItemsDto pontosDeAtencao,
        ItemsDto estrategiasPedagogicas,
        FamilySupportDto acompanhamentoFamiliar,
        NextSemesterPlanDto planejamentoProximoSemestre,
        String parecerFinal) {

    public static SemesterReportContentDto from(SemesterReportContent content) {
        return new SemesterReportContentDto(
                new AttendanceAndStudyDto(
                        content.attendanceAndStudy().lessonAttendance(),
                        content.attendanceAndStudy().homeStudyRegularity(),
                        content.attendanceAndStudy().engagement(),
                        content.attendanceAndStudy().generalComment()),
                new TechniqueDto(
                        content.technique().workedTopics(),
                        content.technique().technicalProgress(),
                        content.technique().pointsToDevelop(),
                        content.technique().completedStudies()),
                new MusicalityDto(
                        content.musicality().studiedRepertoire(),
                        content.musicality().sound(),
                        content.musicality().rhythm(),
                        content.musicality().phrasing(),
                        content.musicality().articulation(),
                        content.musicality().style(),
                        content.musicality().musicalComment()),
                new ReadingAndMemorizationDto(
                        content.readingAndMemorization().assessment(),
                        content.readingAndMemorization().comment()),
                new ItemsDto(content.attentionPoints().items()),
                new ItemsDto(content.pedagogicalStrategies().items()),
                new FamilySupportDto(
                        content.familySupport().presenceLevel(),
                        content.familySupport().teacherObservation()),
                new NextSemesterPlanDto(
                        content.nextSemesterPlan().technicalGoals(),
                        content.nextSemesterPlan().musicalGoals(),
                        content.nextSemesterPlan().studyHabits(),
                        content.nextSemesterPlan().proposedRepertoire(),
                        content.nextSemesterPlan().technicalStudies(),
                        content.nextSemesterPlan().readingAndTheory(),
                        content.nextSemesterPlan().semesterProject()),
                content.finalOpinion());
    }

    public record AttendanceAndStudyDto(
            String frequenciaAulas,
            String regularidadeEstudoEmCasa,
            String engajamento,
            String comentarioGeral) {}

    public record TechniqueDto(
            List<String> conteudosTrabalhados,
            String evolucaoTecnica,
            List<String> pontosADesenvolver,
            List<String> estudosRealizados) {}

    public record MusicalityDto(
            List<String> repertorioEstudado,
            String som,
            String ritmo,
            String fraseado,
            String articulacao,
            String estilo,
            String comentarioMusical) {}

    public record ReadingAndMemorizationDto(String avaliacao, String comentario) {}

    public record ItemsDto(List<String> itens) {}

    public record FamilySupportDto(String nivelDePresenca, String observacaoDoProfessor) {}

    public record NextSemesterPlanDto(
            List<String> metasTecnicas,
            List<String> metasMusicais,
            List<String> habitosDeEstudo,
            List<String> repertorioProposto,
            List<String> estudosTecnicos,
            List<String> leituraETeoria,
            String projetoDoSemestre) {}
}
