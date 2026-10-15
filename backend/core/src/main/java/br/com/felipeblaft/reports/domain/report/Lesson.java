package br.com.felipeblaft.reports.domain.report;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aula (data-model.md §7): data de hoje no fuso da escola (FR-016). É criada junto com o relatório,
 * só depois de a transcrição dar certo (FR-017). Várias aulas no mesmo dia são permitidas.
 */
public record Lesson(
        UUID id, UUID teacherId, UUID studentId, LocalDate lessonDate, Instant createdAt) {

    public Lesson {
        Objects.requireNonNull(id);
        Objects.requireNonNull(teacherId);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(lessonDate);
        Objects.requireNonNull(createdAt);
    }

    public static Lesson create(UUID teacherId, UUID studentId, LocalDate lessonDate, Instant now) {
        return new Lesson(GeneratorId.create(), teacherId, studentId, lessonDate, now);
    }
}
