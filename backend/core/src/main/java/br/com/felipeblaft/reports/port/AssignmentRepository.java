package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import java.util.UUID;

public interface AssignmentRepository {

    boolean exists(UUID teacherId, UUID studentId);

    void save(TeacherStudent assignment);
}
