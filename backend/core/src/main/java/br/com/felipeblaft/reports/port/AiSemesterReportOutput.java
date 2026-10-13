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
        AttendanceAndStudy frequenciaEEstudo,
        Technique tecnica,
        Musicality musicalidade,
        ReadingAndMemorization leituraEMemorizacao,
        Items pontosDeAtencao,
        Items estrategiasPedagogicas,
        FamilySupport acompanhamentoFamiliar,
        NextSemesterPlan planejamentoProximoSemestre,
        String parecerFinal,
        Boolean semAlteracaoDeConteudo,
        String pedidoDeAcao) {

    public SemesterReportContent content() {
        return new SemesterReportContent(
                frequenciaEEstudo,
                tecnica,
                musicalidade,
                leituraEMemorizacao,
                pontosDeAtencao,
                estrategiasPedagogicas,
                acompanhamentoFamiliar,
                planejamentoProximoSemestre,
                parecerFinal);
    }

    public static AiSemesterReportOutput of(
            SemesterReportContent content, boolean withoutChange, String pedidoDeAcao) {
        return new AiSemesterReportOutput(
                content.frequenciaEEstudo(),
                content.tecnica(),
                content.musicalidade(),
                content.leituraEMemorizacao(),
                content.pontosDeAtencao(),
                content.estrategiasPedagogicas(),
                content.acompanhamentoFamiliar(),
                content.planejamentoProximoSemestre(),
                content.parecerFinal(),
                withoutChange,
                pedidoDeAcao);
    }

    public boolean withoutChange() {
        return Boolean.TRUE.equals(semAlteracaoDeConteudo);
    }

    public boolean indicatedActionRequest() {
        return pedidoDeAcao != null && !AiLessonReportOutput.NONE.equals(pedidoDeAcao);
    }
}
