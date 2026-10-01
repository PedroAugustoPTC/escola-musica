package br.com.felipeblaft.relatorios.entrada.web.seguranca;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import java.time.Instant;
import java.util.UUID;

/**
 * Professor identificado pela sessão da Web. A identidade vem sempre da sessão, nunca de dados
 * enviados pelo cliente (FR-009).
 */
public record ProfessorAutenticado(Professor professor, Instant sessaoExpiraEm) {

    public UUID id() {
        return professor.id();
    }

    public String nome() {
        return professor.nome();
    }

    @Override
    public String toString() {
        return "ProfessorAutenticado[" + professor.id() + "]";
    }
}
