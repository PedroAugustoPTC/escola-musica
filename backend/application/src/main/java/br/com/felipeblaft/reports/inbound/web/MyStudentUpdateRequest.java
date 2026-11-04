package br.com.felipeblaft.reports.inbound.web;

/**
 * Corpo de {@code PATCH /my-students/{studentId}} (schema {@code StudentUpdate}): campo ausente não
 * muda; {@code cpf} em branco ou ausente mantém o atual (decisão do núcleo). O {@code toString} não
 * revela dado pessoal.
 */
public record MyStudentUpdateRequest(
        String name,
        String birthDate,
        String cpf,
        MyStudentGuardianRequest guardian,
        String instrument,
        ConsentRequest consent) {

    @Override
    public String toString() {
        return "MyStudentUpdateRequest";
    }
}
