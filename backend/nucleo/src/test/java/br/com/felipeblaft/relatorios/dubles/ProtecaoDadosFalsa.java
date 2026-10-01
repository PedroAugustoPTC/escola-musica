package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Cpf;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import java.nio.charset.StandardCharsets;

/** Proteção determinística e legível para os testes (sem criptografia real). */
public class ProtecaoDadosFalsa implements ProtecaoDadosPorta {

    private int sequenciaToken;

    @Override
    public byte[] cifrarCpf(Cpf cpf) {
        return ("cifrado:" + new StringBuilder(cpf.digitos()).reverse())
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String hashCpf(Cpf cpf) {
        return "cpf:" + cpf.digitos();
    }

    @Override
    public String hashCodigoAcesso(CodigoAcesso codigo) {
        return "codigo:" + codigo.valor();
    }

    @Override
    public String hashOrigem(Canal canal, String origem) {
        return "origem:" + canal + ":" + origem;
    }

    @Override
    public String gerarTokenSessao() {
        sequenciaToken++;
        return "token-" + sequenciaToken;
    }

    @Override
    public String hashTokenSessao(String token) {
        return "hash:" + token;
    }
}
