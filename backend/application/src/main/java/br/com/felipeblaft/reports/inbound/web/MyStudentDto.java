package br.com.felipeblaft.reports.inbound.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aluno na resposta de {@code /my-students} (schema {@code Student}). Nunca tem CPF, cifrado ou
 * não, nem a lista de professores (só as rotas administrativas a trazem).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyStudentDto(
        UUID id,
        String name,
        LocalDate birthDate,
        int age,
        String instrument,
        boolean active,
        MyStudentGuardianDto guardian,
        Boolean canInactivate) {

    @Override
    public String toString() {
        return "MyStudentDto[id=" + id + "]";
    }
}
