package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import java.util.ArrayList;
import java.util.List;

/** Dublê que guarda os alertas reportados, para os testes dos casos de uso. */
public class RecordingAlertService implements OperationalAlertService {

    /** Um alerta reportado. */
    public record Reported(AlertType type, String component, String detailCode) {}

    private final List<Reported> reported = new ArrayList<>();

    @Override
    public void report(AlertType type, String component, String detailCode) {
        reported.add(new Reported(type, component, detailCode));
    }

    public List<Reported> reported() {
        return List.copyOf(reported);
    }
}
