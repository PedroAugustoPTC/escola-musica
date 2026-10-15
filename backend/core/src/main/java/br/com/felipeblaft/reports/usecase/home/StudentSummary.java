package br.com.felipeblaft.reports.usecase.home;

import br.com.felipeblaft.reports.domain.registry.Student;
import java.time.LocalDate;
import java.util.UUID;

/** Aluno como aparece nas listas dos dois canais (sem CPF nem data de nascimento). */
public record StudentSummary(
        UUID id, String name, String instrument, LocalDate lastLessonAt, boolean hasOpenReport) {

    static StudentSummary of(Student student, LocalDate lastLesson, boolean open) {
        return new StudentSummary(
                student.id(), student.name(), student.instrument(), lastLesson, open);
    }
}
