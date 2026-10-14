package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.Instant;
import java.util.List;

/**
 * Resumo da carga. Os erros identificam a linha pelo índice e pelo campo, nunca pelo valor do CPF.
 * Os códigos em claro aparecem só aqui, para a saída do terminal.
 */
public record SeedResult(
        boolean simulation,
        Count teachers,
        Count students,
        int createdAssignments,
        List<SeedError> errors,
        List<IssuedCode> issuedCodes) {

    public SeedResult {
        errors = List.copyOf(errors);
        issuedCodes = List.copyOf(issuedCodes);
    }

    public boolean success() {
        return errors.isEmpty();
    }

    public record Count(int created, int updated, int unchanged) {}

    /** {@code secao} é "teachers" ou "students"; {@code indice} começa em 0. */
    public record SeedError(String section, int index, String field, ErrorCode code) {}

    public record IssuedCode(String email, String code, Instant validUntil) {

        @Override
        public String toString() {
            return "CodigoEmitido[email=" + email + ", codigo=***]";
        }
    }
}
