package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.CanaryStudent;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistência do aluno na feature 005 (T110): contato do responsável, V12 e consultas novas. */
class StudentRepositoryAdapterIT extends RegistryPersistenceBase {

    @Autowired TransactionTemplate transaction;
    @Autowired EntityManagerFactory factory;

    private Student minorWithGuardian(String name, Cpf cpf) {
        return Student.create(
                name,
                CanaryStudent.BIRTH_DATE,
                protection.encryptCpf(cpf),
                protection.hashCpf(cpf),
                CanaryStudent.GUARDIAN_NAME,
                CanaryStudent.GUARDIAN_PHONE_DIGITS,
                CanaryStudent.GUARDIAN_EMAIL,
                CanaryStudent.INSTRUMENT,
                now());
    }

    // ---- V12: contato do responsável

    @Test
    void guardianPhoneAndEmailRoundTrip() {
        Student minor = minorWithGuardian(CanaryStudent.NAME, Cpf.of(validCpf(901)));

        students.save(minor);

        Student read = students.searchById(minor.id()).orElseThrow();
        assertThat(read.guardianName()).isEqualTo(CanaryStudent.GUARDIAN_NAME);
        assertThat(read.guardianPhone()).isEqualTo(CanaryStudent.GUARDIAN_PHONE_DIGITS);
        assertThat(read.guardianEmail()).isEqualTo(CanaryStudent.GUARDIAN_EMAIL);
        assertThat(students.searchByCpfHash(minor.cpfHash()).orElseThrow().guardianPhone())
                .isEqualTo(CanaryStudent.GUARDIAN_PHONE_DIGITS);
    }

    @Test
    void oldStudentWithoutContactRemainsValidAndReadable() {
        Student old = student("Aluno Antigo");

        Student read = students.searchById(old.id()).orElseThrow();

        assertThat(read.guardianPhone()).isNull();
        assertThat(read.guardianEmail()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
        // editar sem informar contato continua possível no banco
        read.inactivate(now());
        students.save(read);
        assertThat(students.searchById(old.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void databaseRejectsMalformedGuardianPhone() {
        Student student = student("Aluno Fictício");

        for (String invalid : List.of("123456789", "123456789012", "1194321876a", "(11)94321876")) {
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "update student set guardian_phone = ? where id = ?",
                                            invalid,
                                            student.id()))
                    .as("telefone %s", invalid)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        // 10 dígitos (fixo), 11 dígitos (celular) e nulo são aceitos
        assertThat(
                        jdbc.update(
                                "update student set guardian_phone = ? where id = ?",
                                "1132165498",
                                student.id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.update(
                                "update student set guardian_phone = ? where id = ?",
                                "11943218765",
                                student.id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.update(
                                "update student set guardian_phone = null where id = ?",
                                student.id()))
                .isEqualTo(1);
    }

    @Test
    void guardianContactColumnsHaveTheExpectedShape() {
        List<String> columns =
                jdbc.queryForList(
                        "select column_name || ':' || data_type || ':' || coalesce("
                                + "character_maximum_length::text, '') || ':' || is_nullable"
                                + " from information_schema.columns where table_name = 'student'"
                                + " and column_name in ('guardian_phone', 'guardian_email')"
                                + " order by column_name",
                        String.class);

        assertThat(columns)
                .containsExactly(
                        "guardian_email:character varying:254:YES",
                        "guardian_phone:character varying:11:YES");
    }

    // ---- insertIfAbsent

    @Test
    void insertIfAbsentInsertsOnceAndReportsDuplicate() {
        Cpf cpf = Cpf.of(validCpf(902));
        Student first = minorWithGuardian("Primeiro", cpf);
        Student duplicate = minorWithGuardian("Duplicado", cpf);

        assertThat(students.insertIfAbsent(first)).isTrue();
        assertThat(students.insertIfAbsent(duplicate)).isFalse();

        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
        assertThat(students.searchById(first.id()).orElseThrow().name()).isEqualTo("Primeiro");
        assertThat(students.searchById(duplicate.id())).isEmpty();
        Student read = students.searchById(first.id()).orElseThrow();
        assertThat(read.guardianPhone()).isEqualTo(CanaryStudent.GUARDIAN_PHONE_DIGITS);
        assertThat(read.guardianEmail()).isEqualTo(CanaryStudent.GUARDIAN_EMAIL);
        assertThat(read.birthDate()).isEqualTo(CanaryStudent.BIRTH_DATE);
        assertThat(read.active()).isTrue();
    }

    @Test
    void duplicateDoesNotAbortTheEnclosingTransaction() {
        Cpf cpf = Cpf.of(validCpf(903));
        students.insertIfAbsent(minorWithGuardian("Primeiro", cpf));

        Integer total =
                transaction.execute(
                        status -> {
                            boolean inserted =
                                    students.insertIfAbsent(minorWithGuardian("Duplicado", cpf));
                            assertThat(inserted).isFalse();
                            // a transação segue utilizável depois da duplicidade
                            students.insertIfAbsent(
                                    minorWithGuardian("Outro", Cpf.of(validCpf(904))));
                            return jdbc.queryForObject(
                                    "select count(*) from student", Integer.class);
                        });

        assertThat(total).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(2);
    }

    @Test
    void simultaneousInsertsWithSameCpfInsertExactlyOne() throws Exception {
        Cpf cpf = Cpf.of(validCpf(905));
        int competitors = 8;
        ExecutorService pool = Executors.newFixedThreadPool(competitors);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < competitors; i++) {
            Student candidate = minorWithGuardian("Concorrente " + i, cpf);
            results.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return students.insertIfAbsent(candidate);
                            }));
        }
        start.countDown();
        int inserted = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                inserted++;
            }
        }
        pool.shutdown();

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
    }

    // ---- searchByCpfHash

    @Test
    void searchByCpfHashFindsByKeyedHashOnly() {
        Cpf cpf = Cpf.of(validCpf(906));
        Student student = minorWithGuardian("Aluno", cpf);
        students.save(student);

        assertThat(students.searchByCpfHash(protection.hashCpf(cpf)))
                .map(Student::id)
                .contains(student.id());
        assertThat(students.searchByCpfHash(protection.hashCpf(Cpf.of(validCpf(907))))).isEmpty();
        assertThat(students.searchByCpfHash(cpf.digits())).isEmpty();
    }

    // ---- findVisible

    @Test
    void findVisibleDecidesTheScopeInTheQuery() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student ofClara = studentOf(clara, "Aluno da Clara");
        Student ofBruno = studentOf(bruno, "Aluno do Bruno");
        Student withoutTeacher = student("Aluno sem professor");

        assertThat(students.findVisible(StudentScope.all(), ofClara.id())).isPresent();
        assertThat(students.findVisible(StudentScope.all(), withoutTeacher.id())).isPresent();
        assertThat(students.findVisible(StudentScope.ofTeacher(clara.id()), ofClara.id()))
                .map(Student::id)
                .contains(ofClara.id());
        assertThat(students.findVisible(StudentScope.ofTeacher(clara.id()), ofBruno.id()))
                .isEmpty();
        assertThat(students.findVisible(StudentScope.ofTeacher(clara.id()), withoutTeacher.id()))
                .isEmpty();
    }

    @Test
    void foreignStudentIsIndistinguishableFromNonexistent() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student ofBruno = studentOf(bruno, "Aluno do Bruno");
        StudentScope scope = StudentScope.ofTeacher(clara.id());

        assertThat(students.findVisible(scope, ofBruno.id()))
                .isEqualTo(students.findVisible(scope, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void findVisibleShowsInactiveStudentsToo() {
        Teacher clara = teacher("Clara Souza", true);
        Student inactive = student("Aluno Inativo", LocalDate.of(1995, 5, 5), false);
        assign(clara, inactive);

        assertThat(students.findVisible(StudentScope.ofTeacher(clara.id()), inactive.id()))
                .map(Student::active)
                .contains(false);
    }

    @Test
    void findVisibleRunsExactlyOneStatement() {
        Teacher clara = teacher("Clara Souza", true);
        Student ofClara = studentOf(clara, "Aluno da Clara");
        Student foreign = student("Aluno alheio");
        SessionFactory sessions = factory.unwrap(SessionFactory.class);
        sessions.getStatistics().setStatisticsEnabled(true);
        try {
            for (var query :
                    List.of(
                            new Object[] {StudentScope.ofTeacher(clara.id()), ofClara.id()},
                            new Object[] {StudentScope.ofTeacher(clara.id()), foreign.id()},
                            new Object[] {StudentScope.all(), foreign.id()})) {
                sessions.getStatistics().clear();

                students.findVisible((StudentScope) query[0], (UUID) query[1]);

                assertThat(sessions.getStatistics().getPrepareStatementCount()).isEqualTo(1);
            }
        } finally {
            sessions.getStatistics().setStatisticsEnabled(false);
        }
    }

    // ---- list

    @Test
    void listFiltersByStatusAndScopeInAlphabeticalOrder() {
        Teacher clara = teacher("Clara Souza", true);
        Teacher bruno = teacher("Bruno Lima", true);
        Student zelia = studentOf(clara, "Zélia Prado");
        Student alvaro = studentOf(clara, "Álvaro Dias");
        Student carlos = studentOf(clara, "carlos Eduardo");
        Student inactive = student("Beatriz Inativa", LocalDate.of(1990, 1, 1), false);
        assign(clara, inactive);
        Student foreign = studentOf(bruno, "Aluno do Bruno");
        Student withoutTeacher = student("Aluno sem professor");

        assertThat(
                        names(
                                students.list(
                                        StudentScope.ofTeacher(clara.id()),
                                        StudentStatusFilter.ACTIVE)))
                .containsExactlyInAnyOrder("Zélia Prado", "Álvaro Dias", "carlos Eduardo");
        assertThat(
                        names(
                                students.list(
                                        StudentScope.ofTeacher(clara.id()),
                                        StudentStatusFilter.INACTIVE)))
                .containsExactly("Beatriz Inativa");
        assertThat(
                        names(
                                students.list(
                                        StudentScope.ofTeacher(clara.id()),
                                        StudentStatusFilter.ALL)))
                .hasSize(4)
                .doesNotContain(foreign.name(), withoutTeacher.name());
        assertThat(students.list(StudentScope.all(), StudentStatusFilter.ALL))
                .extracting(Student::id)
                .containsExactlyInAnyOrder(
                        zelia.id(),
                        alvaro.id(),
                        carlos.id(),
                        inactive.id(),
                        foreign.id(),
                        withoutTeacher.id());
        assertThat(students.list(StudentScope.all(), StudentStatusFilter.INACTIVE))
                .extracting(Student::id)
                .containsExactly(inactive.id());
        assertThat(
                        names(
                                students.list(
                                        StudentScope.ofTeacher(bruno.id()),
                                        StudentStatusFilter.ALL)))
                .containsExactly("Aluno do Bruno");
    }

    @Test
    void listIsOrderedByNameIgnoringCase() {
        Teacher clara = teacher("Clara Souza", true);
        studentOf(clara, "bruno");
        studentOf(clara, "Ana");
        studentOf(clara, "Carla");

        assertThat(
                        names(
                                students.list(
                                        StudentScope.ofTeacher(clara.id()),
                                        StudentStatusFilter.ALL)))
                .containsExactly("Ana", "bruno", "Carla");
    }

    @Test
    void accentInsensitiveSearchOverTheListFindsAccentedNames() {
        Teacher clara = teacher("Clara Souza", true);
        studentOf(clara, "Álvaro Dias");
        studentOf(clara, "Zélia Prado");
        studentOf(clara, "João Pereira");
        List<Student> all =
                students.list(StudentScope.ofTeacher(clara.id()), StudentStatusFilter.ACTIVE);

        // a busca é em memória (R-22), com a normalização do núcleo; o banco entrega os nomes
        // intactos
        assertThat(matching(all, "alvaro")).containsExactly("Álvaro Dias");
        assertThat(matching(all, "ZELIA")).containsExactly("Zélia Prado");
        assertThat(matching(all, "joao")).containsExactly("João Pereira");
        assertThat(matching(all, "xyz")).isEmpty();
    }

    @Test
    void listOfScopeWithoutStudentsIsEmpty() {
        Teacher clara = teacher("Clara Souza", true);

        assertThat(students.list(StudentScope.ofTeacher(clara.id()), StudentStatusFilter.ALL))
                .isEmpty();
        assertThat(students.list(StudentScope.all(), StudentStatusFilter.ALL)).isEmpty();
    }

    private static List<String> names(List<Student> list) {
        return list.stream().map(Student::name).toList();
    }

    private static List<String> matching(List<Student> list, String term) {
        String normalized = StudentSearch.normalize(term);
        return list.stream()
                .map(Student::name)
                .filter(n -> StudentSearch.normalize(n).contains(normalized))
                .toList();
    }
}
