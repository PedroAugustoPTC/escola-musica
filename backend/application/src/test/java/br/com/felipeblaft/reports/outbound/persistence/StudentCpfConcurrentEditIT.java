package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.usecase.registry.StudentInput;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Duas edições simultâneas para o mesmo CPF novo, com Postgres real: o índice único decide, uma é
 * aceita e a outra recebe a recusa de duplicidade (nunca erro 500 do banco).
 */
class StudentCpfConcurrentEditIT extends RegistryPersistenceBase {

    private static final int ROUNDS = 8;

    @Autowired UpdateStudentUseCase update;

    private final Actor admin = Actor.administrator(UUID.randomUUID(), false);

    private Callable<Object> edit(Student student, String cpf) {
        return () -> {
            try {
                return update.run(
                        admin,
                        new UpdateStudentUseCase.Command(
                                student.id(),
                                new StudentInput(null, null, new Secret(cpf), null, null),
                                "req-concurrent"));
            } catch (DomainException refusal) {
                return refusal;
            }
        };
    }

    @Test
    void twoSimultaneousEditsToTheSameCpfAcceptOneAndRefuseTheOther() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Student first = student("Aluno Um " + round);
            Student second = student("Aluno Dois " + round);
            String contested = validCpf(900 + round);

            List<Callable<Object>> tasks = new ArrayList<>();
            tasks.add(edit(first, contested));
            tasks.add(edit(second, contested));
            List<Object> results = ConcurrentRunner.runTogether(tasks);

            List<Object> accepted = results.stream().filter(r -> r instanceof StudentView).toList();
            List<DomainException> refused =
                    results.stream()
                            .filter(r -> r instanceof DomainException)
                            .map(DomainException.class::cast)
                            .toList();
            assertThat(accepted).as("rodada %d: uma aceita", round).hasSize(1);
            assertThat(refused).as("rodada %d: uma recusada", round).hasSize(1);
            assertThat(refused.getFirst().code()).isEqualTo(ErrorCode.DUPLICATE_CPF);
            assertThat(refused.getFirst().details()).containsKey("name");
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from student where cpf_hash = ?",
                                    Integer.class,
                                    protection.hashCpf(
                                            br.com.felipeblaft.reports.domain.registry.Cpf.of(
                                                    contested))))
                    .isEqualTo(1);
        }
    }

    @Test
    void savingAStudentWithTheHashOfAnotherIsTranslatedIntoTheDuplicate() {
        Student holder = student("Ana Souza");
        Student other = student("Bruno Lima");
        Student clash =
                new Student(
                        other.id(),
                        other.name(),
                        other.birthDate(),
                        other.encryptedCpf(),
                        holder.cpfHash(),
                        null,
                        null,
                        null,
                        other.instrument(),
                        true,
                        other.createdAt(),
                        now());

        assertThatThrownBy(() -> students.save(clash))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_CPF);
                            assertThat(e.details()).containsEntry("name", "Ana Souza");
                            assertThat(e.toString()).doesNotContain(holder.cpfHash());
                        });
    }
}
