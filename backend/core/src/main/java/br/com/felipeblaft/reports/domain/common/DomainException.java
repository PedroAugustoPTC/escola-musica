package br.com.felipeblaft.reports.domain.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Violação de regra de negócio. Carrega apenas o código estável e dados estruturados (nunca texto
 * para o professor nem detalhe interno).
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public DomainException(ErrorCode code) {
        this(code, Map.of());
    }

    public DomainException(ErrorCode code, Map<String, ?> details) {
        super(code.name(), null, false, false);
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
