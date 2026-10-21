package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;

/**
 * Cifragem e hashes com segredo do servidor (Princípio IV, R-12). O núcleo nunca vê chaves nem
 * algoritmos; só recebe os valores protegidos.
 */
public interface DataProtectionPort {

    byte[] encryptCpf(Cpf cpf);

    /** HMAC do CPF normalizado, usado na unicidade do aluno. */
    String hashCpf(Cpf cpf);

    /** HMAC do código de acesso normalizado. */
    String hashAccessCode(AccessCode code);

    /**
     * HMAC do código de ativação do administrador, com subchave do {@code AUTH_HMAC_SECRET}
     * (distinto do segredo do CPF).
     */
    String hashActivationCode(ActivationCode code);

    /** Hash não reversível da origem de uma tentativa de acesso (IP ou conta do Telegram). */
    String originHash(Channel channel, String origin);

    /**
     * Hash não reversível da origem por escopo (administrador, ativação, sondagem de CPF), com
     * subchave do {@code AUTH_HMAC_SECRET}; o endereço de rede nunca é guardado.
     */
    String originHash(AttemptScope scope, String key);

    /** Token opaco de sessão da Web (256 bits), entregue só no cookie. */
    String generateSessionToken();

    /** Hash do token de sessão; só ele fica no banco. */
    String sessionTokenHash(String token);
}
