package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.AccessAttempt;
import br.com.felipeblaft.reports.domain.common.Channel;
import java.util.Optional;

/**
 * Tentativas de acesso por origem. A gravação acontece numa transação independente: uma falha de
 * código fica registrada mesmo que a operação que a detectou seja desfeita (FR-007, lição 4 do
 * MVP).
 */
public interface AccessAttemptRepository {

    Optional<AccessAttempt> search(Channel channel, String originHash);

    void save(AccessAttempt attempt);
}
