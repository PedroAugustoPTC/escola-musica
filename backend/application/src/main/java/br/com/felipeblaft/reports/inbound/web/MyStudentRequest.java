package br.com.felipeblaft.reports.inbound.web;

/**
 * Corpo de {@code POST /my-students} (schema {@code StudentInput}). O professor da sessão é o único
 * associado: o corpo não carrega professores. Sem validação de negócio aqui; o núcleo valida. O
 * {@code toString} não revela dado pessoal.
 */
public record MyStudentRequest(
        String name,
        String birthDate,
        String cpf,
        MyStudentGuardianRequest guardian,
        String instrument) {

    @Override
    public String toString() {
        return "MyStudentRequest";
    }
}
