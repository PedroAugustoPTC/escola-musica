package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.RegistryLimits;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.Map;
import java.util.Objects;

/**
 * Lista os professores ({@code TEACHER_LIST}) com busca por nome ou e-mail e filtro de situação,
 * indicando se há Telegram vinculado; nunca devolve código. A busca vazia lista tudo; busca com
 * mais de 100 caracteres é recusada sem eco.
 */
public class ListTeachersUseCase {

    /** {@code active} nulo lista todos. */
    public record Command(String search, Boolean active, PageRequest page) {

        @Override
        public String toString() {
            return "ListTeachersUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final TelegramLinkRepository links;

    public ListTeachersUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.links = Objects.requireNonNull(links);
    }

    public PageResult<TeacherView> run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_LIST);
        String search = command.search() == null ? "" : command.search().strip();
        if (search.codePointCount(0, search.length()) > RegistryLimits.SEARCH_MAX) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of(
                            "fields",
                            java.util.List.of(
                                    Map.of(
                                            "field",
                                            "search",
                                            "code",
                                            ErrorCode.INVALID_TEXT.name()))));
        }
        return teachers.list(search.isEmpty() ? null : search, command.active(), command.page())
                .map(
                        teacher ->
                                TeacherView.of(
                                        teacher,
                                        links.searchByTeacherId(teacher.id()).isPresent()));
    }
}
