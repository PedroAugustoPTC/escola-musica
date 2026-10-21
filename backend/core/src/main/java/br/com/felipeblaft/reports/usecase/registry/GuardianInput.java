package br.com.felipeblaft.reports.usecase.registry;

/**
 * Responsável como chega de qualquer canal: textos brutos, validados no núcleo (R-11). O {@code
 * toString} não revela dado pessoal.
 */
public record GuardianInput(String name, String phone, String email) {

    @Override
    public String toString() {
        return "GuardianInput";
    }
}
