package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Autorização por ator e operação, decidida no núcleo (FR-002, R-01): consulta a {@link
 * PermissionMatrix} e é a primeira instrução de todo caso de uso que exige credencial. O papel que
 * não tem a operação recebe {@code RESOURCE_NOT_FOUND}, igual ao recurso inexistente (a rota não
 * existe para ele); o administrador com senha provisória só troca a senha, lê a sessão e sai.
 */
public class ActorAuthorizationService {

    /**
     * Operações permitidas ao ator restrito (senha provisória ainda não trocada). Fonte única: a
     * segunda barreira da Web (filtro de sessão) deriva as rotas daqui, em vez de repetir a lista.
     */
    public static final Set<Operation> ALLOWED_WHILE_RESTRICTED =
            Collections.unmodifiableSet(
                    EnumSet.of(
                            Operation.ADMIN_CHANGE_PASSWORD,
                            Operation.ADMIN_GET_SESSION,
                            Operation.ADMIN_SIGN_OUT));

    public static boolean allowedWhileRestricted(Operation operation) {
        return ALLOWED_WHILE_RESTRICTED.contains(operation);
    }

    private final PermissionMatrix matrix;

    public ActorAuthorizationService(PermissionMatrix matrix) {
        this.matrix = Objects.requireNonNull(matrix);
    }

    /**
     * Exige que o ator possa executar a operação e devolve a permissão (para o caso de uso aplicar
     * o escopo de alunos). A matriz decide antes da restrição de senha provisória.
     */
    public Permission require(Actor actor, Operation operation) {
        Permission permission = matrix.decide(operation, actor);
        if (!permission.allowed()) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (actor.passwordChangeRequired() && !allowedWhileRestricted(operation)) {
            throw new DomainException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        return permission;
    }
}
