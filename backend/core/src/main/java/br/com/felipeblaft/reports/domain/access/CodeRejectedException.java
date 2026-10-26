package br.com.felipeblaft.reports.domain.access;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.Optional;
import java.util.UUID;

/**
 * Código de acesso recusado. Para o chamador externo é um {@code INVALID_CODE} igual em todos os
 * casos (inexistente, fora do formato, vencido, substituído, de professor inativo); o professor
 * identificado pelo código e o motivo interno servem só ao registro de autoria e nunca saem na
 * resposta, no log nem na mensagem.
 */
public class CodeRejectedException extends DomainException {

    private static final String UNKNOWN = "UNKNOWN_CODE";
    private static final String EXPIRED = "CODE_EXPIRED";

    private final transient UUID knownTeacherId;
    private final String auditCode;

    private CodeRejectedException(UUID knownTeacherId, String auditCode) {
        super(ErrorCode.INVALID_CODE);
        this.knownTeacherId = knownTeacherId;
        this.auditCode = auditCode;
    }

    /**
     * Código que não identifica professor ativo (inexistente, malformado, substituído, inativo).
     */
    public static CodeRejectedException unknown() {
        return new CodeRejectedException(null, UNKNOWN);
    }

    /** Código de professor ativo que já venceu. */
    public static CodeRejectedException expired(UUID teacherId) {
        return new CodeRejectedException(teacherId, EXPIRED);
    }

    /** Professor a quem o código pertence, quando ele é conhecido. */
    public Optional<UUID> knownTeacherId() {
        return Optional.ofNullable(knownTeacherId);
    }

    /** Código de erro do registro de autoria: {@code UNKNOWN_CODE} ou {@code CODE_EXPIRED}. */
    public String auditCode() {
        return auditCode;
    }
}
