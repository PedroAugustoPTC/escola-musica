package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import java.util.Optional;
import java.util.UUID;

/** Versões imutáveis: só inserção e leitura. */
public interface VersaoRelatorioRepositorio {

    void salvar(VersaoRelatorio versao);

    Optional<VersaoRelatorio> buscar(TipoRelatorio tipo, UUID relatorioId, int numero);
}
