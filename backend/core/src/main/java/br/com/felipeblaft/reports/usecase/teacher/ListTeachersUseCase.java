package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;

/**
 * Lista os professores ({@code TEACHER_LIST}) com busca por nome ou e-mail e filtro de situação,
 * indicando se há Telegram vinculado; nunca devolve código. Esqueleto da Fundação; a regra é do
 * fluxo W-B.
 */
public class ListTeachersUseCase {

    /** {@code active} nulo lista todos. */
    public record Command(String search, Boolean active, PageRequest page) {

        @Override
        public String toString() {
            return "ListTeachersUseCase.Command";
        }
    }

    public ListTeachersUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links) {}

    public PageResult<TeacherView> run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ListTeachersUseCase: fluxo W-B");
    }
}
