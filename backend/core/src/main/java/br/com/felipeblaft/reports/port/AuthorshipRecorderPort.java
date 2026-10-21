package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;

/**
 * Registro de autoria, somente de acréscimo: a porta não tem método de alteração nem de remoção
 * (FR-040). Dois modos de gravação.
 */
public interface AuthorshipRecorderPort {

    /**
     * Grava na mesma transação do caso de uso: gravação e registro valem juntos, e a falha do
     * registro desfaz a operação.
     */
    void record(AuthorshipRecord record);

    /**
     * Grava em transação independente, para falhas, bloqueios e negativas cuja transação será
     * desfeita. Falha de gravação vai ao log técnico sem dado sensível e o acesso continua
     * recusado.
     */
    void recordIndependent(AuthorshipRecord record);
}
