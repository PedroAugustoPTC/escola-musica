package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.acesso.TentativaAcesso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import java.util.Optional;

/**
 * Tentativas de acesso por origem. A gravação acontece numa transação independente: uma falha de
 * código fica registrada mesmo que a operação que a detectou seja desfeita (FR-007, lição 4 do MVP).
 */
public interface TentativaAcessoRepositorio {

    Optional<TentativaAcesso> buscar(Canal canal, String origemHash);

    void salvar(TentativaAcesso tentativa);
}
