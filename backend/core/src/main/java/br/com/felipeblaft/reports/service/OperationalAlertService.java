package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.operations.AlertType;

/**
 * Abre e normaliza alertas operacionais (FR-055, FR-056). O alerta não carrega aluno nem texto
 * livre: só o tipo, o componente (provedor, canal, armazenamento...) e um código do motivo.
 */
public interface OperationalAlertService {

    /** Abre o alerta ou, se já está ativo, soma a ocorrência. */
    void report(AlertType type, String component, String detailCode);

    /** A condição sumiu: normaliza o alerta ativo do tipo e componente, se houver. */
    default void resolve(AlertType type, String component) {
        // implementações que não guardam alerta não têm o que normalizar
    }
}
