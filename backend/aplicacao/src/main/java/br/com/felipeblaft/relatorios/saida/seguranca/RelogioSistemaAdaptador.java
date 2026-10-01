package br.com.felipeblaft.relatorios.saida.seguranca;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Relógio do sistema com o fuso da escola ({@code ESCOLA_FUSO_HORARIO}). */
@Component
public class RelogioSistemaAdaptador implements RelogioPorta {

    private final Clock relogio = Clock.systemUTC();
    private final ZoneId fuso;

    public RelogioSistemaAdaptador(PropriedadesAplicacao propriedades) {
        this.fuso = propriedades.escola().fusoHorario();
    }

    @Override
    public Instant agora() {
        return relogio.instant();
    }

    @Override
    public ZoneId fusoDaEscola() {
        return fuso;
    }
}
