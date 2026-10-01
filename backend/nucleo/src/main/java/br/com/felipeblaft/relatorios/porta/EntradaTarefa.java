package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import java.util.List;
import java.util.Objects;

/**
 * Entradas permitidas para a IA (FR-023, FR-044): nome, CPF, data de nascimento e responsável do
 * aluno não existem nestes tipos, então não há como enviá-los.
 */
public sealed interface EntradaTarefa
        permits EntradaTarefa.EstruturacaoAula,
                EntradaTarefa.IncorporacaoRespostas,
                EntradaTarefa.RevisaoAula,
                EntradaTarefaSemestral {

    /** ESTRUTURACAO_AULA: só a transcrição. */
    record EstruturacaoAula(String transcricao) implements EntradaTarefa {

        public EstruturacaoAula {
            Objects.requireNonNull(transcricao);
        }

        @Override
        public String toString() {
            return "EstruturacaoAula[***]";
        }
    }

    /** INCORPORACAO_RESPOSTAS: conteúdo atual, perguntas pendentes, resposta e rodada atual. */
    record IncorporacaoRespostas(
            ConteudoRelatorioAula conteudoAtual, List<String> perguntas, String resposta, int rodada)
            implements EntradaTarefa {

        public IncorporacaoRespostas {
            Objects.requireNonNull(conteudoAtual);
            perguntas = List.copyOf(perguntas);
            Objects.requireNonNull(resposta);
        }

        @Override
        public String toString() {
            return "IncorporacaoRespostas[rodada=" + rodada + "]";
        }
    }

    /** REVISAO_AULA: conteúdo atual e o pedido de alteração. */
    record RevisaoAula(ConteudoRelatorioAula conteudoAtual, String pedido) implements EntradaTarefa {

        public RevisaoAula {
            Objects.requireNonNull(conteudoAtual);
            Objects.requireNonNull(pedido);
        }

        @Override
        public String toString() {
            return "RevisaoAula[***]";
        }
    }
}
