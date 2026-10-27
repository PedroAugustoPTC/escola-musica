package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;

/** Conversão entre as entidades JPA do administrador e os objetos de domínio. */
final class AdministratorMapper {

    private AdministratorMapper() {}

    static Administrator toDomain(AdministratorEntity e) {
        return new Administrator(
                e.getId(),
                e.getName(),
                e.getEmail(),
                e.isActive(),
                e.getPasswordHash(),
                e.isPasswordProvisional(),
                e.getPasswordProvisionalExpiresAt(),
                e.getActivationCodeHash(),
                e.getActivationCodeExpiresAt(),
                e.getActivatedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    static AdministratorEntity toEntity(Administrator a) {
        return new AdministratorEntity(
                a.id(),
                a.name(),
                a.email(),
                a.active(),
                a.passwordHash(),
                a.passwordProvisional(),
                a.passwordProvisionalExpiresAt(),
                a.activationCodeHash(),
                a.activationCodeExpiresAt(),
                a.activatedAt(),
                a.createdAt(),
                a.updatedAt());
    }

    static AdministratorSession toDomain(AdministratorSessionEntity e) {
        return new AdministratorSession(
                e.getId(),
                e.getAdministratorId(),
                e.getTokenHash(),
                e.getCreatedAt(),
                e.getExpiresAt(),
                e.getRevokedAt(),
                e.getRevocationReason());
    }

    static AdministratorSessionEntity toEntity(AdministratorSession s) {
        return new AdministratorSessionEntity(
                s.id(),
                s.administratorId(),
                s.tokenHash(),
                s.createdAt(),
                s.expiresAt(),
                s.revokedAt(),
                s.revocationReason());
    }
}
