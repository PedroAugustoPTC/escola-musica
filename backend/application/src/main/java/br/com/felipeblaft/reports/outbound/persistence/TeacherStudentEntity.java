package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teacher_student_assignment")
public class TeacherStudentEntity {

    @Id private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TeacherStudentEntity() {}

    TeacherStudentEntity(UUID id, UUID teacherId, UUID studentId, Instant createdAt) {
        this.id = id;
        this.teacherId = teacherId;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTeacherId() {
        return teacherId;
    }

    UUID getStudentId() {
        return studentId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
