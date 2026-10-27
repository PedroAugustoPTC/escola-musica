package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;

/**
 * Regras comuns dos casos de uso do cadastro em andamento: o cadastro existe só no Telegram e só
 * para o professor; quem não é o professor do Telegram recebe a mesma negativa do recurso
 * inexistente. Cada tipo de cadastro responde à operação própria da matriz de permissões.
 */
public final class TelegramRegistrationAccessService {

    private TelegramRegistrationAccessService() {}

    /** Só o professor, no Telegram, mexe no cadastro em andamento. */
    public static void requireTelegramTeacher(Actor actor) {
        if (actor.role() != Role.TEACHER || actor.origin() != ActionOrigin.TELEGRAM) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    /** Operação da matriz que corresponde ao tipo do cadastro. */
    public static Operation operationOf(StudentRegistrationDraft.Kind kind) {
        return switch (kind) {
            case CREATE -> Operation.STUDENT_REGISTER;
            case UPDATE -> Operation.STUDENT_UPDATE;
            case INACTIVATE -> Operation.STUDENT_INACTIVATE;
            case REACTIVATE -> Operation.STUDENT_REACTIVATE;
        };
    }

    /** Exige o professor do Telegram e a permissão da operação do tipo. */
    public static void require(
            ActorAuthorizationService authorization,
            Actor actor,
            StudentRegistrationDraft.Kind kind) {
        requireTelegramTeacher(actor);
        authorization.require(actor, operationOf(kind));
    }
}
