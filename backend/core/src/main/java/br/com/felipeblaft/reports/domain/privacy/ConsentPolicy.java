package br.com.felipeblaft.reports.domain.privacy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.RegistryLimits;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

/**
 * Quando o consentimento é exigido e qual é a situação dele (FR-030 a FR-035). Aluno menor de 18
 * anos (no fuso da escola) exige consentimento válido para qualquer chamada de IA; adulto não.
 */
public final class ConsentPolicy {

    private ConsentPolicy() {}

    /** O aluno é menor de 18 anos na data de referência? */
    public static boolean required(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears() < RegistryLimits.ADULT_AGE;
    }

    /**
     * Situação pelo histórico: qualquer registro válido e compatível vence; senão revogado; senão
     * versão superada; sem registro, ausente.
     */
    public static ConsentStatus status(List<GuardianConsent> history, int compatibleFrom) {
        if (history.isEmpty()) {
            return ConsentStatus.MISSING;
        }
        boolean anyRevoked = false;
        for (GuardianConsent consent : history) {
            if (consent.revoked()) {
                anyRevoked = true;
            } else if (consent.termVersionNumber() >= compatibleFrom) {
                return ConsentStatus.VALID;
            }
        }
        return anyRevoked ? ConsentStatus.REVOKED : ConsentStatus.SUPERSEDED;
    }

    /** Situação de um registro isolado (histórico do aluno). */
    public static ConsentStatus recordStatus(GuardianConsent consent, int compatibleFrom) {
        if (consent.revoked()) {
            return ConsentStatus.REVOKED;
        }
        return consent.termVersionNumber() >= compatibleFrom
                ? ConsentStatus.VALID
                : ConsentStatus.SUPERSEDED;
    }

    /** A IA fica bloqueada para menor sem consentimento válido. */
    public static boolean blocksAi(LocalDate birthDate, LocalDate today, ConsentStatus status) {
        return required(birthDate, today) && status != ConsentStatus.VALID;
    }

    /**
     * Cadastro ou edição: menor sem consentimento válido precisa trazer o {@link ConsentInput}
     * (recusa em {@code consent}).
     */
    public static void requireOnRegistration(
            LocalDate birthDate, LocalDate today, ConsentStatus current, ConsentInput input) {
        if (input == null && blocksAi(birthDate, today, current)) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of(
                            "fields",
                            List.of(
                                    Map.of(
                                            "field",
                                            "consent",
                                            "code",
                                            ErrorCode.CONSENT_REQUIRED.name()))));
        }
    }
}
