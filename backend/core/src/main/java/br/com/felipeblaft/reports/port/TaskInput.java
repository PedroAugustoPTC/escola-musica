package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import java.util.List;
import java.util.Objects;

/**
 * Entradas permitidas para a IA (FR-023, FR-044): nome, CPF, data de nascimento e responsável do
 * aluno não existem nestes tipos, então não há como enviá-los.
 */
public sealed interface TaskInput
        permits TaskInput.LessonStructuring,
                TaskInput.AnswerIncorporation,
                TaskInput.LessonRevision,
                SemesterTaskInput {

    /** ESTRUTURACAO_AULA: só a transcrição. */
    record LessonStructuring(String transcricao) implements TaskInput {

        public LessonStructuring {
            Objects.requireNonNull(transcricao);
        }

        @Override
        public String toString() {
            return "EstruturacaoAula[***]";
        }
    }

    /** INCORPORACAO_RESPOSTAS: conteúdo atual, perguntas pendentes, resposta e rodada atual. */
    record AnswerIncorporation(
            LessonReportContent conteudoAtual, List<String> perguntas, String resposta, int rodada)
            implements TaskInput {

        public AnswerIncorporation {
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
    record LessonRevision(LessonReportContent conteudoAtual, String pedido) implements TaskInput {

        public LessonRevision {
            Objects.requireNonNull(conteudoAtual);
            Objects.requireNonNull(pedido);
        }

        @Override
        public String toString() {
            return "RevisaoAula[***]";
        }
    }
}
