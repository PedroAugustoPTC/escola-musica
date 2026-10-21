package br.com.felipeblaft.reports.usecase.registry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aluno como o caso de uso o devolve às interfaces: nunca traz CPF, cifrado ou não. A lista de
 * professores só é preenchida para o administrador; o professor nunca vê os outros. O {@code
 * toString} não revela dado pessoal.
 *
 * @param age idade em anos completos no fuso da escola
 * @param guardian nulo quando o aluno não tem responsável cadastrado
 * @param teachers professores associados (administrador); vazio para o professor
 * @param canInactivate só para o professor: falso quando há mais de um professor ativo (FR-026);
 *     nulo para o administrador
 */
public record StudentView(
        UUID id,
        String name,
        LocalDate birthDate,
        int age,
        String instrument,
        boolean active,
        GuardianView guardian,
        List<TeacherRef> teachers,
        Boolean canInactivate) {

    public StudentView {
        teachers = teachers == null ? List.of() : List.copyOf(teachers);
    }

    /** Responsável: telefone só com dígitos. O {@code toString} não revela dado pessoal. */
    public record GuardianView(String name, String phone, String email) {

        @Override
        public String toString() {
            return "GuardianView";
        }
    }

    /** Professor associado ao aluno, como o administrador o enxerga. */
    public record TeacherRef(UUID id, String name, boolean active) {

        @Override
        public String toString() {
            return "TeacherRef[id=" + id + "]";
        }
    }

    @Override
    public String toString() {
        return "StudentView[id=" + id + "]";
    }
}
