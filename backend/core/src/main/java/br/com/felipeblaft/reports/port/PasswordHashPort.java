package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.Secret;

/**
 * Hash adaptativo de senha (o algoritmo fica atrás desta porta; o núcleo não conhece algoritmo nem
 * parâmetros). A senha nunca é guardada nem registrada em claro.
 */
public interface PasswordHashPort {

    /** Hash codificado, com sal único e os parâmetros dentro. */
    String hash(Secret password);

    boolean matches(Secret password, String encodedHash);

    /** Os parâmetros do hash guardado ficaram abaixo dos atuais: regravar no próximo acesso. */
    boolean needsRehash(String encodedHash);

    /**
     * Executa a verificação completa contra um hash "isca", para que e-mail desconhecido e senha
     * errada tenham o mesmo custo e o mesmo tempo de resposta (FR-004).
     */
    void verifyAgainstDecoy(Secret password);
}
