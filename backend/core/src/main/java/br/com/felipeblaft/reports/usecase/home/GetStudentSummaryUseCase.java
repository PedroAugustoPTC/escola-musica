package br.com.felipeblaft.reports.usecase.home;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.AuthorizationService;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumo de um aluno associado. Aluno de outro professor (ou inexistente) é {@code
 * ALUNO_NAO_ASSOCIADO}, sem revelar nenhum dado (FR-009).
 */
public class GetStudentSummaryUseCase {

    private final StudentRepository students;
    private final OpenReportsQueryPort query;
    private final AuthorizationService authorization;

    public GetStudentSummaryUseCase(
            StudentRepository students,
            OpenReportsQueryPort query,
            AuthorizationService authorization) {
        this.students = Objects.requireNonNull(students);
        this.query = Objects.requireNonNull(query);
        this.authorization = Objects.requireNonNull(authorization);
    }

    public StudentSummary run(UUID teacherId, UUID studentId) {
        authorization.requireAssignment(teacherId, studentId);
        Student student =
                students.searchById(studentId)
                        .orElseThrow(() -> new DomainException(ErrorCode.STUDENT_NOT_ASSIGNED));
        return new StudentSummary(
                student.id(),
                student.name(),
                student.instrument(),
                query.lastLessonByStudent(teacherId).get(studentId),
                query.searchByStudent(teacherId, studentId).isPresent());
    }
}
