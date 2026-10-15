package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.AccessCode;
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

    /** Hash não reversível da origem de uma tentativa de acesso (IP ou conta do Telegram). */
    String originHash(Channel channel, String origin);

    /** Token opaco de sessão da Web (256 bits), entregue só no cookie. */
    String generateSessionToken();

    /** Hash do token de sessão; só ele fica no banco. */
    String sessionTokenHash(String token);
}
