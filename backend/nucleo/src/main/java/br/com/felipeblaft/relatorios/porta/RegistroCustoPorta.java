package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;

/** Registro de custo e tempo das chamadas de IA (FR-056). */
public interface RegistroCustoPorta {

    void registrar(ChamadaIa chamada);
}
