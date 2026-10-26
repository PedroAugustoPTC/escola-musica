package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base dos testes de persistência da feature 005 (W-G): cria professor, aluno, associação, vínculo
 * e relatório em aberto diretamente pelos portos, com dados fictícios e CPF válido gerado em
 * sequência. Nenhum dado real.
 */
abstract class RegistryPersistenceBase extends IntegrationBase {

    @Autowired protected TeacherRepository teachers;
    @Autowired protected StudentRepository students;
    @Autowired protected AssignmentRepository assignments;
    @Autowired protected TelegramLinkRepository links;
    @Autowired protected LessonReportRepository lessonReports;
    @Autowired protected DataProtectionPort protection;

    private int sequence;

    protected Instant now() {
        return clock.now();
    }

    protected Teacher teacher(String name, boolean active) {
        int n = ++sequence;
        Teacher teacher = Teacher.create(name, "professor" + n + "@example.test", true, now());
        teacher.replaceCode(
                protection.hashAccessCode(AccessCode.of(accessCodeOf(n))),
                now(),
                Duration.ofDays(7));
        if (!active) {
            teacher.inactivate(now());
        }
        teachers.save(teacher);
        return teacher;
    }

    /** Código de acesso válido e distinto por sequência (8 caracteres do alfabeto do código). */
    private static String accessCodeOf(int n) {
        String alphabet = AccessCode.ALPHABET;
        StringBuilder code = new StringBuilder("K7QM");
        int rest = n;
        for (int i = 0; i < 4; i++) {
            code.append(alphabet.charAt(rest % alphabet.length()));
            rest /= alphabet.length();
        }
        return code.toString();
    }

    protected void inactivate(Teacher teacher) {
        teacher.inactivate(now());
        teachers.save(teacher);
    }

    protected void reactivate(Teacher teacher) {
        teacher.reactivate(now());
        teachers.save(teacher);
    }

    /** Aluno adulto, ativo e sem professor associado. */
    protected Student student(String name) {
        return student(name, LocalDate.of(1990, 1, 15), true);
    }

    protected Student student(String name, LocalDate birthDate, boolean active) {
        Cpf cpf = Cpf.of(validCpf(++sequence));
        Student student =
                Student.create(
                        name,
                        birthDate,
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        null,
                        "Piano",
                        now());
        if (!active) {
            student.inactivate(now());
        }
        students.save(student);
        return student;
    }

    protected Student studentOf(Teacher teacher, String name) {
        Student student = student(name);
        assign(teacher, student);
        return student;
    }

    protected void assign(Teacher teacher, Student student) {
        assignments.save(TeacherStudent.create(teacher.id(), student.id(), now()));
    }

    protected TelegramLink link(Teacher teacher, long telegramUserId, long chatId) {
        TelegramLink link = TelegramLink.create(teacher.id(), telegramUserId, chatId, now());
        links.save(link);
        return link;
    }

    /** Relatório de aula em aberto (em processamento) do par professor e aluno. */
    protected LessonReport openReport(Teacher teacher, Student student) {
        Lesson lesson = Lesson.create(teacher.id(), student.id(), LocalDate.of(2026, 9, 20), now());
        lessonReports.saveLesson(lesson);
        LessonReport report =
                LessonReport.create(lesson, "transcrição fictícia", Channel.WEB, now());
        lessonReports.save(report);
        return report;
    }

    /** CPF válido a partir de um número de sequência (algoritmo dos dígitos verificadores). */
    protected static String validCpf(int n) {
        String base = String.format("%09d", 100_000_000 + n * 7919);
        int first = digit(base, 9);
        int second = digit(base + first, 10);
        return base + first + second;
    }

    private static int digit(String digits, int quantity) {
        int sum = 0;
        for (int i = 0; i < quantity; i++) {
            sum += (digits.charAt(i) - '0') * (quantity + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }
}
