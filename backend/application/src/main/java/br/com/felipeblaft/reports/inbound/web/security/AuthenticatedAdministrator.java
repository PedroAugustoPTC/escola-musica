package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import java.time.Instant;
import java.util.UUID;

/**
 * Administrador identificado pela sessão da área administrativa. A identidade vem sempre da sessão
 * validada no núcleo, nunca de dados enviados pelo cliente. Guarda o token só para as operações que
 * atuam sobre a própria sessão ("Sair", ler a sessão); o {@code toString} nunca o revela.
 */
public record AuthenticatedAdministrator(Actor actor, Instant sessionExpiresAt, Secret token) {

    public UUID id() {
        return actor.id();
    }

    @Override
    public String toString() {
        return "AuthenticatedAdministrator[" + actor.id() + "]";
    }
}
