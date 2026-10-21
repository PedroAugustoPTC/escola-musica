package br.com.felipeblaft.reports.domain.registry;

/**
 * Responsável pelo aluno: nome, telefone e e-mail (obrigatórios para menor de 18 anos). Nunca vai a
 * IA nem a relatório (FR-017). O {@code toString} não revela dado pessoal.
 */
public record Guardian(PersonName name, BrazilianPhone phone, EmailAddress email) {

    @Override
    public String toString() {
        return "Guardian";
    }
}
