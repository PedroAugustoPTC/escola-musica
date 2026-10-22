package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.UUID;

/** Esqueleto (testes primeiro); a regra vem em seguida. */
public class ReportEligibilityService {

    public ReportEligibilityService(TeacherRepository teachers, StudentRepository students) {}

    public void requireActiveTeacher(UUID teacherId) {
        throw new UnsupportedOperationException();
    }

    public void requireActiveTeacher(Teacher teacher) {
        throw new UnsupportedOperationException();
    }

    public void requireActiveStudent(UUID studentId) {
        throw new UnsupportedOperationException();
    }
}
