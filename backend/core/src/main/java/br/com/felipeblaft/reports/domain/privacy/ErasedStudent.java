package br.com.felipeblaft.reports.domain.privacy;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Registro de aluno eliminado, só acréscimo; base para reaplicar após restauração (FR-026). */
public record ErasedStudent(UUID studentId, Instant erasedAt) {

    /** Rótulo de exibição "Aluno removido · #XXXXXX" a partir do identificador (FR-024). */
    public static String label(UUID studentId) {
        String hex = studentId.toString().replace("-", "");
        return "Aluno removido · #" + hex.substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
