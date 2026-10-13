package br.com.felipeblaft.reports.domain.semester;

import br.com.felipeblaft.reports.domain.report.VersionContent;
import java.util.List;
import java.util.Objects;

/**
 * Seções 2 a 10 do relatório semestral (data-model.md §9), geradas e revisadas pela IA. Campos sem
 * base nos relatórios de aula ficam vazios (FR-020): textos normalizados, itens em branco
 * descartados e seções ausentes tratadas como vazias.
 */
public record SemesterReportContent(
        AttendanceAndStudy frequenciaEEstudo,
        Technique tecnica,
        Musicality musicalidade,
        ReadingAndMemorization leituraEMemorizacao,
        Items pontosDeAtencao,
        Items estrategiasPedagogicas,
        FamilySupport acompanhamentoFamiliar,
        NextSemesterPlan planejamentoProximoSemestre,
        String parecerFinal)
        implements VersionContent {

    public SemesterReportContent {
        frequenciaEEstudo =
                Objects.requireNonNullElseGet(
                        frequenciaEEstudo, () -> new AttendanceAndStudy(null, null, null, null));
        tecnica =
                Objects.requireNonNullElseGet(tecnica, () -> new Technique(null, null, null, null));
        musicalidade =
                Objects.requireNonNullElseGet(
                        musicalidade,
                        () -> new Musicality(null, null, null, null, null, null, null));
        leituraEMemorizacao =
                Objects.requireNonNullElseGet(
                        leituraEMemorizacao, () -> new ReadingAndMemorization(null, null));
        pontosDeAtencao = Objects.requireNonNullElseGet(pontosDeAtencao, () -> new Items(null));
        estrategiasPedagogicas =
                Objects.requireNonNullElseGet(estrategiasPedagogicas, () -> new Items(null));
        acompanhamentoFamiliar =
                Objects.requireNonNullElseGet(
                        acompanhamentoFamiliar, () -> new FamilySupport(null, null));
        planejamentoProximoSemestre =
                Objects.requireNonNullElseGet(
                        planejamentoProximoSemestre,
                        () -> new NextSemesterPlan(null, null, null, null, null, null, null));
        parecerFinal = text(parecerFinal);
    }

    public static SemesterReportContent empty() {
        return new SemesterReportContent(null, null, null, null, null, null, null, null, null);
    }

    static List<String> items(List<String> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(i -> !i.isEmpty())
                .toList();
    }

    static String text(String text) {
        return text == null ? "" : text.strip();
    }

    public record AttendanceAndStudy(
            String frequenciaAulas,
            String regularidadeEstudoEmCasa,
            String engajamento,
            String comentarioGeral) {
        public AttendanceAndStudy {
            frequenciaAulas = text(frequenciaAulas);
            regularidadeEstudoEmCasa = text(regularidadeEstudoEmCasa);
            engajamento = text(engajamento);
            comentarioGeral = text(comentarioGeral);
        }
    }

    public record Technique(
            List<String> conteudosTrabalhados,
            String evolucaoTecnica,
            List<String> pontosADesenvolver,
            List<String> estudosRealizados) {
        public Technique {
            conteudosTrabalhados = items(conteudosTrabalhados);
            evolucaoTecnica = text(evolucaoTecnica);
            pontosADesenvolver = items(pontosADesenvolver);
            estudosRealizados = items(estudosRealizados);
        }
    }

    public record Musicality(
            List<String> repertorioEstudado,
            String som,
            String ritmo,
            String fraseado,
            String articulacao,
            String estilo,
            String comentarioMusical) {
        public Musicality {
            repertorioEstudado = items(repertorioEstudado);
            som = text(som);
            ritmo = text(ritmo);
            fraseado = text(fraseado);
            articulacao = text(articulacao);
            estilo = text(estilo);
            comentarioMusical = text(comentarioMusical);
        }
    }

    public record ReadingAndMemorization(String avaliacao, String comentario) {
        public ReadingAndMemorization {
            avaliacao = text(avaliacao);
            comentario = text(comentario);
        }
    }

    public record Items(List<String> itens) {
        public Items {
            itens = SemesterReportContent.items(itens);
        }
    }

    public record FamilySupport(String nivelDePresenca, String observacaoDoProfessor) {
        public FamilySupport {
            nivelDePresenca = text(nivelDePresenca);
            observacaoDoProfessor = text(observacaoDoProfessor);
        }
    }

    public record NextSemesterPlan(
            List<String> metasTecnicas,
            List<String> metasMusicais,
            List<String> habitosDeEstudo,
            List<String> repertorioProposto,
            List<String> estudosTecnicos,
            List<String> leituraETeoria,
            String projetoDoSemestre) {
        public NextSemesterPlan {
            metasTecnicas = items(metasTecnicas);
            metasMusicais = items(metasMusicais);
            habitosDeEstudo = items(habitosDeEstudo);
            repertorioProposto = items(repertorioProposto);
            estudosTecnicos = items(estudosTecnicos);
            leituraETeoria = items(leituraETeoria);
            projetoDoSemestre = text(projetoDoSemestre);
        }
    }
}
