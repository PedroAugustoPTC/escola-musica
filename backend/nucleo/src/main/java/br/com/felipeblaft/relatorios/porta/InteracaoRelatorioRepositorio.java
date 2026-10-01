package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import java.util.Optional;
import java.util.UUID;

public interface InteracaoRelatorioRepositorio {

    void salvar(InteracaoRelatorio interacao);

    Optional<InteracaoRelatorio> buscarPorId(UUID id);

    Optional<InteracaoRelatorio> ultima(TipoRelatorio tipo, UUID relatorioId);
}
