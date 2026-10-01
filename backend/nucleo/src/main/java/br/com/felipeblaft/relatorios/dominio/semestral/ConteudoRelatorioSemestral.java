package br.com.felipeblaft.relatorios.dominio.semestral;

import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoVersao;
import java.util.List;
import java.util.Objects;

/**
 * Seções 2 a 10 do relatório semestral (data-model.md §9), geradas e revisadas pela IA. Campos sem
 * base nos relatórios de aula ficam vazios (FR-020): textos normalizados, itens em branco
 * descartados e seções ausentes tratadas como vazias.
 */
public record ConteudoRelatorioSemestral(
        FrequenciaEEstudo frequenciaEEstudo,
        Tecnica tecnica,
        Musicalidade musicalidade,
        LeituraEMemorizacao leituraEMemorizacao,
        Itens pontosDeAtencao,
        Itens estrategiasPedagogicas,
        AcompanhamentoFamiliar acompanhamentoFamiliar,
        PlanejamentoProximoSemestre planejamentoProximoSemestre,
        String parecerFinal)
        implements ConteudoVersao {

    public ConteudoRelatorioSemestral {
        frequenciaEEstudo =
                Objects.requireNonNullElseGet(
                        frequenciaEEstudo, () -> new FrequenciaEEstudo(null, null, null, null));
        tecnica = Objects.requireNonNullElseGet(tecnica, () -> new Tecnica(null, null, null, null));
        musicalidade =
                Objects.requireNonNullElseGet(
                        musicalidade,
                        () -> new Musicalidade(null, null, null, null, null, null, null));
        leituraEMemorizacao =
                Objects.requireNonNullElseGet(
                        leituraEMemorizacao, () -> new LeituraEMemorizacao(null, null));
        pontosDeAtencao = Objects.requireNonNullElseGet(pontosDeAtencao, () -> new Itens(null));
        estrategiasPedagogicas =
                Objects.requireNonNullElseGet(estrategiasPedagogicas, () -> new Itens(null));
        acompanhamentoFamiliar =
                Objects.requireNonNullElseGet(
                        acompanhamentoFamiliar, () -> new AcompanhamentoFamiliar(null, null));
        planejamentoProximoSemestre =
                Objects.requireNonNullElseGet(
                        planejamentoProximoSemestre,
                        () ->
                                new PlanejamentoProximoSemestre(
                                        null, null, null, null, null, null, null));
        parecerFinal = texto(parecerFinal);
    }

    public static ConteudoRelatorioSemestral vazio() {
        return new ConteudoRelatorioSemestral(null, null, null, null, null, null, null, null, null);
    }

    static List<String> itens(List<String> itens) {
        if (itens == null) {
            return List.of();
        }
        return itens.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(i -> !i.isEmpty())
                .toList();
    }

    static String texto(String texto) {
        return texto == null ? "" : texto.strip();
    }

    public record FrequenciaEEstudo(
            String frequenciaAulas,
            String regularidadeEstudoEmCasa,
            String engajamento,
            String comentarioGeral) {
        public FrequenciaEEstudo {
            frequenciaAulas = texto(frequenciaAulas);
            regularidadeEstudoEmCasa = texto(regularidadeEstudoEmCasa);
            engajamento = texto(engajamento);
            comentarioGeral = texto(comentarioGeral);
        }
    }

    public record Tecnica(
            List<String> conteudosTrabalhados,
            String evolucaoTecnica,
            List<String> pontosADesenvolver,
            List<String> estudosRealizados) {
        public Tecnica {
            conteudosTrabalhados = itens(conteudosTrabalhados);
            evolucaoTecnica = texto(evolucaoTecnica);
            pontosADesenvolver = itens(pontosADesenvolver);
            estudosRealizados = itens(estudosRealizados);
        }
    }

    public record Musicalidade(
            List<String> repertorioEstudado,
            String som,
            String ritmo,
            String fraseado,
            String articulacao,
            String estilo,
            String comentarioMusical) {
        public Musicalidade {
            repertorioEstudado = itens(repertorioEstudado);
            som = texto(som);
            ritmo = texto(ritmo);
            fraseado = texto(fraseado);
            articulacao = texto(articulacao);
            estilo = texto(estilo);
            comentarioMusical = texto(comentarioMusical);
        }
    }

    public record LeituraEMemorizacao(String avaliacao, String comentario) {
        public LeituraEMemorizacao {
            avaliacao = texto(avaliacao);
            comentario = texto(comentario);
        }
    }

    public record Itens(List<String> itens) {
        public Itens {
            itens = ConteudoRelatorioSemestral.itens(itens);
        }
    }

    public record AcompanhamentoFamiliar(String nivelDePresenca, String observacaoDoProfessor) {
        public AcompanhamentoFamiliar {
            nivelDePresenca = texto(nivelDePresenca);
            observacaoDoProfessor = texto(observacaoDoProfessor);
        }
    }

    public record PlanejamentoProximoSemestre(
            List<String> metasTecnicas,
            List<String> metasMusicais,
            List<String> habitosDeEstudo,
            List<String> repertorioProposto,
            List<String> estudosTecnicos,
            List<String> leituraETeoria,
            String projetoDoSemestre) {
        public PlanejamentoProximoSemestre {
            metasTecnicas = itens(metasTecnicas);
            metasMusicais = itens(metasMusicais);
            habitosDeEstudo = itens(habitosDeEstudo);
            repertorioProposto = itens(repertorioProposto);
            estudosTecnicos = itens(estudosTecnicos);
            leituraETeoria = itens(leituraETeoria);
            projetoDoSemestre = texto(projetoDoSemestre);
        }
    }
}
