package br.com.felipeblaft.relatorios.dominio.comum;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Violação de regra de negócio. Carrega apenas o código estável e dados estruturados (nunca texto
 * para o professor nem detalhe interno).
 */
public class ExcecaoDominio extends RuntimeException {

    private final CodigoErro codigo;
    private final Map<String, Object> detalhes;

    public ExcecaoDominio(CodigoErro codigo) {
        this(codigo, Map.of());
    }

    public ExcecaoDominio(CodigoErro codigo, Map<String, ?> detalhes) {
        super(codigo.name(), null, false, false);
        this.codigo = codigo;
        this.detalhes = Collections.unmodifiableMap(new LinkedHashMap<>(detalhes));
    }

    public CodigoErro codigo() {
        return codigo;
    }

    public Map<String, Object> detalhes() {
        return detalhes;
    }
}
