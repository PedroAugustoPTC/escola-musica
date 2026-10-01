package br.com.felipeblaft.relatorios.dominio.cadastro;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Associação professor–aluno: base de toda autorização (FR-009). */
public record ProfessorAluno(UUID id, UUID professorId, UUID alunoId, Instant criadoEm) {

    public ProfessorAluno {
        Objects.requireNonNull(id);
        Objects.requireNonNull(professorId);
        Objects.requireNonNull(alunoId);
        Objects.requireNonNull(criadoEm);
    }

    public static ProfessorAluno nova(UUID professorId, UUID alunoId, Instant agora) {
        return new ProfessorAluno(GeradorId.novo(), professorId, alunoId, agora);
    }
}
