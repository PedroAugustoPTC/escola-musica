package br.com.felipeblaft.relatorios.porta;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Tempo corrente e fuso da escola (FR-016: a data da aula é a de hoje no fuso da escola). */
public interface RelogioPorta {

    Instant agora();

    ZoneId fusoDaEscola();

    default LocalDate hojeNoFusoDaEscola() {
        return dataNoFusoDaEscola(agora());
    }

    default LocalDate dataNoFusoDaEscola(Instant instante) {
        return instante.atZone(fusoDaEscola()).toLocalDate();
    }
}
