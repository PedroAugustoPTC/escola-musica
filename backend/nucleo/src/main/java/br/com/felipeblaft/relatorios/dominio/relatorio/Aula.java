package br.com.felipeblaft.relatorios.dominio.relatorio;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aula (data-model.md §7): data de hoje no fuso da escola (FR-016). É criada junto com o relatório,
 * só depois de a transcrição dar certo (FR-017). Várias aulas no mesmo dia são permitidas.
 */
public record Aula(UUID id, UUID professorId, UUID alunoId, LocalDate dataAula, Instant criadoEm) {

    public Aula {
        Objects.requireNonNull(id);
        Objects.requireNonNull(professorId);
        Objects.requireNonNull(alunoId);
        Objects.requireNonNull(dataAula);
        Objects.requireNonNull(criadoEm);
    }

    public static Aula nova(UUID professorId, UUID alunoId, LocalDate dataAula, Instant agora) {
        return new Aula(GeradorId.novo(), professorId, alunoId, dataAula, agora);
    }
}
