package br.com.felipeblaft.reports.domain.registry;

import java.util.Objects;
import java.util.UUID;

/**
 * Escopo de visibilidade de alunos, passado ao repositório (R-14): o administrador e o sistema
 * enxergam a escola inteira; o professor, só os alunos associados a ele. O escopo é decidido no
 * núcleo e aplicado na consulta, nunca por filtragem posterior nem na interface.
 */
public record StudentScope(UUID teacherId) {

    public static StudentScope all() {
        return new StudentScope(null);
    }

    public static StudentScope ofTeacher(UUID teacherId) {
        return new StudentScope(Objects.requireNonNull(teacherId));
    }

    /** Escola inteira (sem restrição por professor). */
    public boolean unrestricted() {
        return teacherId == null;
    }
}
