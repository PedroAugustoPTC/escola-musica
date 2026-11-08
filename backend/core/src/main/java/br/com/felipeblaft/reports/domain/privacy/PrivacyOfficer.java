package br.com.felipeblaft.reports.domain.privacy;

/**
 * Responsável pela LGPD mostrado nas telas (D11). Nome e contato são da escola, não de aluno; vazio
 * vira "a definir pela escola" na exibição.
 */
public record PrivacyOfficer(String name, String contact) {

    public PrivacyOfficer {
        name = name == null ? "" : name.trim();
        contact = contact == null ? "" : contact.trim();
    }

    public boolean defined() {
        return !name.isEmpty() && !contact.isEmpty();
    }
}
