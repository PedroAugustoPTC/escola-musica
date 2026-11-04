package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.operations.AlertType;

/**
 * Abre alertas operacionais (FR-055, FR-056). O alerta não carrega aluno nem texto livre: só o
 * tipo, o componente (provedor, canal, armazenamento...) e um código do motivo. A regra completa
 * (abrir, atualizar, normalizar) vem com a história de custos e alertas; aqui fica a porta que a
 * verificação da trilha e a retenção usam desde já.
 */
public interface OperationalAlertService {

    /** Abre o alerta ou, se já está ativo, soma a ocorrência. */
    void report(AlertType type, String component, String detailCode);
}
