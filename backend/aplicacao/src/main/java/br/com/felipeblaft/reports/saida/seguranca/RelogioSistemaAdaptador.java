package br.com.felipeblaft.reports.saida.seguranca;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Relógio do sistema com o fuso da escola ({@code ESCOLA_FUSO_HORARIO}). */
@Component
public class RelogioSistemaAdaptador implements ClockPort {

    private final Clock relogio = Clock.systemUTC();
    private final ZoneId fuso;

    public RelogioSistemaAdaptador(PropriedadesAplicacao propriedades) {
        this.fuso = propriedades.escola().fusoHorario();
    }

    @Override
    public Instant now() {
        return relogio.instant();
    }

    @Override
    public ZoneId schoolTimezone() {
        return fuso;
    }
}
