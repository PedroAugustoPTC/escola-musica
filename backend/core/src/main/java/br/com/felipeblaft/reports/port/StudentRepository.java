package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository {

    Optional<Student> searchById(UUID id);

    Optional<Student> searchByCpfHash(String cpfHash);

    /** Alunos associados ao professor, em ordem alfabética de nome. */
    List<Student> listByTeacher(UUID teacherId);

    /**
     * Aluno visível no escopo, numa única consulta: para o professor, só se estiver associado a
     * ele. Aluno alheio, sem professor ou inexistente produzem o mesmo vazio (R-14).
     */
    Optional<Student> findVisible(StudentScope scope, UUID id);

    /** Alunos do escopo na situação pedida, em ordem alfabética de nome. */
    List<Student> list(StudentScope scope, StudentStatusFilter status);

    /**
     * Insere somente se o CPF (resumo) ainda não existe, sem abortar a transação (FR-041); devolve
     * {@code false} em caso de duplicidade.
     */
    boolean insertIfAbsent(Student student);

    /**
     * Grava o aluno. Se outro aluno já tem o mesmo resumo de CPF (corrida entre duas edições ou
     * cadastros), lança {@code DomainException} com {@code DUPLICATE_CPF} e o nome do aluno que o
     * tem em {@code details} ("name"); a transação do chamador deve ser desfeita.
     */
    void save(Student student);
}
