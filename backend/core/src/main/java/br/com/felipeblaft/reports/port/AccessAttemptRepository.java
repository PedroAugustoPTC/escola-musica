package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.access.AttemptPolicy;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import java.time.Instant;
import java.util.Optional;

/**
 * Tentativas de acesso por origem. A gravação acontece numa transação independente: uma falha de
 * código fica registrada mesmo que a operação que a detectou seja desfeita (FR-007, lição 4 do
 * MVP). A chave é {@code (scope, channel, originHash)}: cada escopo conta à parte (R-05).
 */
public interface AccessAttemptRepository {

    Optional<AccessAttempt> search(AttemptScope scope, Channel channel, String originHash);

    /**
     * Registra uma falha de forma atômica (transação independente, com bloqueio da linha): a regra
     * continua no domínio ({@code AccessAttempt}), o adaptador só serializa. Falha durante um
     * bloqueio ativo não conta nem prorroga. Devolve o estado depois da falha.
     */
    AccessAttempt registerFailure(
            AttemptScope scope,
            Channel channel,
            String originHash,
            Instant now,
            AttemptPolicy policy);

    /**
     * Registra um sucesso sob o mesmo bloqueio de linha: zera a contagem, exceto se houver bloqueio
     * ativo (a senha correta que chega depois da última falha é recusada).
     */
    void confirmSuccess(AttemptScope scope, Channel channel, String originHash, Instant now);

    void save(AccessAttempt attempt);
}
