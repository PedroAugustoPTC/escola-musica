package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.port.CostReportQueryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Linhas de custo programadas pelo teste; guarda o intervalo pedido. */
public class FakeCostReportQuery implements CostReportQueryPort {

    public final List<CostLine> lines = new ArrayList<>();
    public Instant lastFrom;
    public Instant lastTo;

    @Override
    public List<CostLine> lines(Instant from, Instant to) {
        lastFrom = from;
        lastTo = to;
        return List.copyOf(lines);
    }
}
