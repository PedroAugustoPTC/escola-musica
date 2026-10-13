package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.registry.Student;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository {

    Optional<Student> searchById(UUID id);

    Optional<Student> searchByCpfHash(String cpfHash);

    /** Alunos associados ao professor, em ordem alfabética de nome. */
    List<Student> listByTeacher(UUID teacherId);

    void save(Student student);
}
