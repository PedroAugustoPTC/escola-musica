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
    record LessonStructuring(String transcription) implements TaskInput {

        public LessonStructuring {
            Objects.requireNonNull(transcription);
        }

        @Override
        public String toString() {
            return "LessonStructuring[***]";
        }
    }

    /** INCORPORACAO_RESPOSTAS: conteúdo atual, perguntas pendentes, resposta e rodada atual. */
    record AnswerIncorporation(
            LessonReportContent currentContent, List<String> questions, String response, int round)
            implements TaskInput {

        public AnswerIncorporation {
            Objects.requireNonNull(currentContent);
            questions = List.copyOf(questions);
            Objects.requireNonNull(response);
        }

        @Override
        public String toString() {
            return "AnswerIncorporation[round=" + round + "]";
        }
    }

    /** REVISAO_AULA: conteúdo atual e o pedido de alteração. */
    record LessonRevision(LessonReportContent currentContent, String request) implements TaskInput {

        public LessonRevision {
            Objects.requireNonNull(currentContent);
            Objects.requireNonNull(request);
        }

        @Override
        public String toString() {
            return "LessonRevision[***]";
        }
    }
}
