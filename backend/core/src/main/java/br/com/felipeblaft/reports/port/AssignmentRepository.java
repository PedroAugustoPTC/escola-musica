package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import java.util.List;
import java.util.UUID;

public interface AssignmentRepository {

    boolean exists(UUID teacherId, UUID studentId);

    void save(TeacherStudent assignment);

    /** Retira a associação; devolve {@code false} se ela não existia. */
    boolean delete(UUID teacherId, UUID studentId);

    /** Ids dos professores associados ao aluno. */
    List<UUID> listTeachersOfStudent(UUID studentId);

    /** Quantos professores ativos estão associados ao aluno (regra do único professor, FR-026). */
    int countActiveTeachers(UUID studentId);
}
