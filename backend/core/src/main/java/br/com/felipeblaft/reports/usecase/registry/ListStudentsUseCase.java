package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.RegistryLimits;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lista alunos ({@code STUDENT_LIST}): administrador, a escola inteira; professor, só os seus, o
 * mesmo caso de uso na Web e no Telegram (o escopo vem do {@link StudentAccessService}). Busca por
 * parte do nome ou do instrumento, sem diferenciar caixa nem acento; termo com forma de CPF é
 * recusado com {@code VALIDATION_FAILED} no campo {@code search}, sem repetir o termo (FR-021).
 * Situação padrão: ativos.
 */
public class ListStudentsUseCase {

    public record Command(String search, StudentStatusFilter status, PageRequest page) {

        @Override
        public String toString() {
            return "ListStudentsUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final GetStudentUseCase.Views views;

    public ListStudentsUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.views = new GetStudentUseCase.Views(assignments, teachers, clock);
    }

    public PageResult<StudentView> run(Actor actor, Command command) {
        authorization.require(actor, Operation.STUDENT_LIST);
        String query = query(command.search());
        StudentStatusFilter status =
                command.status() == null ? StudentStatusFilter.ACTIVE : command.status();
        PageRequest page = command.page() == null ? PageRequest.first() : command.page();
        List<Student> students =
                access.list(actor, status).stream()
                        .filter(student -> query.isEmpty() || matches(student, query))
                        .toList();
        boolean forTeacher = actor.role() == Role.TEACHER;
        return PageResult.slice(students, page).map(student -> views.of(student, forTeacher));
    }

    /** Termo normalizado; recusa o termo longo demais e o que tem forma de CPF. */
    private static String query(String search) {
        if (search == null || search.isBlank()) {
            return "";
        }
        String term = search.strip();
        boolean cpfShaped = term.replaceAll("[.\\-\\s]", "").matches("\\d{11}");
        if (cpfShaped || term.codePointCount(0, term.length()) > RegistryLimits.SEARCH_MAX) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of(
                            "fields",
                            List.of(
                                    Map.of(
                                            "field",
                                            "search",
                                            "code",
                                            ErrorCode.INVALID_DATA.name()))));
        }
        return StudentSearch.normalize(term);
    }

    private static boolean matches(Student student, String query) {
        return StudentSearch.normalize(student.name()).contains(query)
                || (student.instrument() != null
                        && StudentSearch.normalize(student.instrument()).contains(query));
    }
}
