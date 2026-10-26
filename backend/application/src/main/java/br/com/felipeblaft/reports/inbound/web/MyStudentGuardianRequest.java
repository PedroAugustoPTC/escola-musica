package br.com.felipeblaft.reports.inbound.web;

/** Responsável como chega pela Web (schema {@code GuardianInput}); validado no núcleo. */
public record MyStudentGuardianRequest(String name, String phone, String email) {

    @Override
    public String toString() {
        return "MyStudentGuardianRequest";
    }
}
