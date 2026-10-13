package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Associação professor–aluno: base de toda autorização (FR-009). */
public record TeacherStudent(UUID id, UUID teacherId, UUID studentId, Instant createdAt) {

    public TeacherStudent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(teacherId);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(createdAt);
    }

    public static TeacherStudent create(UUID teacherId, UUID studentId, Instant now) {
        return new TeacherStudent(GeneratorId.create(), teacherId, studentId, now);
    }
}
