package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import java.util.Objects;

/** "Encerrar sessão": apaga a sessão no servidor (FR-008); a próxima requisição recebe 401. */
public class EncerrarSessaoWebCasoDeUso {

    private final SessaoWebRepositorio sessoes;
    private final ProtecaoDadosPorta protecao;

    public EncerrarSessaoWebCasoDeUso(SessaoWebRepositorio sessoes, ProtecaoDadosPorta protecao) {
        this.sessoes = Objects.requireNonNull(sessoes);
        this.protecao = Objects.requireNonNull(protecao);
    }

    public void executar(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessoes.buscarPorTokenHash(protecao.hashTokenSessao(token))
                .ifPresent(sessao -> sessoes.remover(sessao.id()));
    }
}
