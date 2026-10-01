package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.acesso.SessaoWeb;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessaoWebRepositorio {

    Optional<SessaoWeb> buscarPorTokenHash(String tokenHash);

    void salvar(SessaoWeb sessao);

    void remover(UUID id);

    /** Varredura diária de sessões vencidas; devolve quantas foram apagadas. */
    int removerVencidas(Instant agora);
}
