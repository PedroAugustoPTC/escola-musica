package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Cpf;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;

/**
 * Cifragem e hashes com segredo do servidor (Princípio IV, R-12). O núcleo nunca vê chaves nem
 * algoritmos; só recebe os valores protegidos.
 */
public interface ProtecaoDadosPorta {

    byte[] cifrarCpf(Cpf cpf);

    /** HMAC do CPF normalizado, usado na unicidade do aluno. */
    String hashCpf(Cpf cpf);

    /** HMAC do código de acesso normalizado. */
    String hashCodigoAcesso(CodigoAcesso codigo);

    /** Hash não reversível da origem de uma tentativa de acesso (IP ou conta do Telegram). */
    String hashOrigem(Canal canal, String origem);

    /** Token opaco de sessão da Web (256 bits), entregue só no cookie. */
    String gerarTokenSessao();

    /** Hash do token de sessão; só ele fica no banco. */
    String hashTokenSessao(String token);
}
