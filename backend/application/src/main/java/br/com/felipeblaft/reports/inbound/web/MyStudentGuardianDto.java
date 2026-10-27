package br.com.felipeblaft.reports.inbound.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Responsável na resposta (schema {@code Guardian}); telefone só com dígitos. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyStudentGuardianDto(String name, String phone, String email) {

    @Override
    public String toString() {
        return "MyStudentGuardianDto";
    }
}
