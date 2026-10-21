package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Administrator;
import java.time.Instant;
import java.util.UUID;

/**
 * Administrador como o caso de uso o devolve às interfaces: nunca traz hash de senha nem código. O
 * {@code toString} não revela dado pessoal.
 *
 * @param activatedAt nulo enquanto a conta está pendente de ativação
 */
public record AdministratorView(
        UUID id,
        String name,
        String email,
        boolean active,
        ActivationState activationState,
        Instant activatedAt) {

    /** Situação de ativação derivada: sem senha e com código pendente, ou com senha definida. */
    public enum ActivationState {
        PENDING,
        ACTIVATED
    }

    /** Projeta a conta sem hash nem código; sem senha a conta está pendente e sem data. */
    public static AdministratorView of(Administrator administrator) {
        boolean activated = administrator.activated();
        return new AdministratorView(
                administrator.id(),
                administrator.name(),
                administrator.email(),
                administrator.active(),
                activated ? ActivationState.ACTIVATED : ActivationState.PENDING,
                activated ? administrator.activatedAt() : null);
    }

    @Override
    public String toString() {
        return "AdministratorView[id=" + id + "]";
    }
}
