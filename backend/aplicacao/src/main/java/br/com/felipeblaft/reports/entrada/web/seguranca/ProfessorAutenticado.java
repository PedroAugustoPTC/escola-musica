package br.com.felipeblaft.reports.entrada.web.seguranca;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.time.Instant;
import java.util.UUID;

/**
 * Professor identificado pela sessão da Web. A identidade vem sempre da sessão, nunca de dados
 * enviados pelo cliente (FR-009).
 */
public record ProfessorAutenticado(Teacher professor, Instant sessaoExpiraEm) {

    public UUID id() {
        return professor.id();
    }

    public String nome() {
        return professor.name();
    }

    @Override
    public String toString() {
        return "ProfessorAutenticado[" + professor.id() + "]";
    }
}
