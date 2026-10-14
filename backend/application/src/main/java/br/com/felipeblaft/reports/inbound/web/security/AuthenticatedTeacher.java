package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.time.Instant;
import java.util.UUID;

/**
 * Professor identificado pela sessão da Web. A identidade vem sempre da sessão, nunca de dados
 * enviados pelo cliente (FR-009).
 */
public record AuthenticatedTeacher(Teacher teacher, Instant sessionExpiresAt) {

    public UUID id() {
        return teacher.id();
    }

    public String name() {
        return teacher.name();
    }

    @Override
    public String toString() {
        return "AuthenticatedTeacher[" + teacher.id() + "]";
    }
}
