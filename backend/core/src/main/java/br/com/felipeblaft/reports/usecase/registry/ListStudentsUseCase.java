package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;

/**
 * Lista alunos ({@code STUDENT_LIST}): administrador, a escola inteira; professor, só os seus, o
 * mesmo caso de uso na Web e no Telegram. Busca por nome ou instrumento; termo com forma de CPF é
 * recusado. Esqueleto da Fundação; a regra é do fluxo W-C1.
 */
public class ListStudentsUseCase {

    public record Command(String search, StudentStatusFilter status, PageRequest page) {

        @Override
        public String toString() {
            return "ListStudentsUseCase.Command";
        }
    }

    public ListStudentsUseCase(
            ActorAuthorizationService authorization, StudentAccessService access) {}

    public PageResult<StudentView> run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ListStudentsUseCase: fluxo W-C1");
    }
}
