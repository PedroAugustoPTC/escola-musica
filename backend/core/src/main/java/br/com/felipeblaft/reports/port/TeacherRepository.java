package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.util.Optional;
import java.util.UUID;

public interface TeacherRepository {

    Optional<Teacher> searchById(UUID id);

    Optional<Teacher> searchByEmail(String normalizedEmail);

    Optional<Teacher> searchByAccessCodeHash(String hash);

    void save(Teacher teacher);
}
