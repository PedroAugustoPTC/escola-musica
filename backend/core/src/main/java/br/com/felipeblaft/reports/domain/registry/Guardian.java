package br.com.felipeblaft.reports.domain.registry;

import java.util.Objects;

/**
 * Responsável pelo aluno: nome, telefone e e-mail (obrigatórios para menor de 18 anos). Nunca vai a
 * IA nem a relatório (FR-017). Telefone e e-mail podem faltar apenas em aluno adulto já existente
 * da 001, que tinha só o nome do responsável. O {@code toString} não revela dado pessoal.
 */
public record Guardian(PersonName name, BrazilianPhone phone, EmailAddress email) {

    public Guardian {
        Objects.requireNonNull(name);
    }

    /** Telefone e e-mail presentes (o nome sempre está). */
    public boolean isComplete() {
        return phone != null && email != null;
    }

    @Override
    public String toString() {
        return "Guardian";
    }
}
