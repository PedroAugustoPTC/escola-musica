package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Quem pode iniciar ou ver relatório (R-23, FR-022): o professor precisa estar ativo, porque os
 * relatórios de professor inativo ficam congelados (preservados, fora de listas e ações) e voltam
 * quando ele é reativado; e o aluno precisa estar ativo para receber relatório novo. Os relatórios
 * já em aberto de aluno inativo continuam acessíveis ao professor associado pelas regras normais,
 * então esta verificação só vale para o que é novo. Quem chama já confirmou a associação, de modo
 * que a situação do aluno nunca revela aluno alheio.
 */
public class ReportEligibilityService {

    private final TeacherRepository teachers;
    private final StudentRepository students;

    public ReportEligibilityService(TeacherRepository teachers, StudentRepository students) {
        this.teachers = Objects.requireNonNull(teachers);
        this.students = Objects.requireNonNull(students);
    }

    /** Professor existente e ativo; inativo é {@code TEACHER_INACTIVE}. */
    public void requireActiveTeacher(UUID teacherId) {
        requireActiveTeacher(
                teachers.searchById(teacherId)
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    public void requireActiveTeacher(Teacher teacher) {
        if (!teacher.active()) {
            throw new DomainException(ErrorCode.TEACHER_INACTIVE);
        }
    }

    /** Aluno existente e ativo; inativo é {@code STUDENT_INACTIVE}. */
    public void requireActiveStudent(UUID studentId) {
        boolean active =
                students.searchById(studentId)
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND))
                        .active();
        if (!active) {
            throw new DomainException(ErrorCode.STUDENT_INACTIVE);
        }
    }
}
