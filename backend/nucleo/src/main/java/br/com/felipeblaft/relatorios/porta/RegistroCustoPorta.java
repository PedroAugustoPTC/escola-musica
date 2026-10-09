package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;
import java.util.UUID;

/** Registro de custo e tempo das chamadas de IA (FR-056). */
public interface RegistroCustoPorta {

    void registrar(ChamadaIa chamada);

    /**
     * Vincula ao relatório recém-criado as chamadas do trabalho feitas antes de ele existir (a
     * transcrição inicial e a consolidação); as já vinculadas não mudam.
     */
    void vincularRelatorio(UUID trabalhoId, TipoRelatorio tipo, UUID relatorioId);
}
