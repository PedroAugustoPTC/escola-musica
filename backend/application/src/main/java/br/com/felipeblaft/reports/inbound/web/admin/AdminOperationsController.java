package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.inbound.web.admin.AdminOperationsDtos.AlertBody;
import br.com.felipeblaft.reports.inbound.web.admin.AdminOperationsDtos.AlertPage;
import br.com.felipeblaft.reports.inbound.web.admin.AdminOperationsDtos.AlertSummary;
import br.com.felipeblaft.reports.inbound.web.admin.AdminOperationsDtos.MonthlyCostBody;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.operations.AcknowledgeOperationalAlertUseCase;
import br.com.felipeblaft.reports.usecase.operations.GetMonthlyCostReportUseCase;
import br.com.felipeblaft.reports.usecase.operations.ListOperationalAlertsUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custo e alertas de operação na área administrativa (feature 007, US5). Sem regra de negócio: a
 * autorização, a leitura e o registro na trilha são do núcleo. Toda resposta leva {@code
 * Cache-Control: no-store, private}.
 */
@RestController
@RequestMapping("/api/v1/admin/operations")
public class AdminOperationsController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final GetMonthlyCostReportUseCase costs;
    private final ListOperationalAlertsUseCase alerts;
    private final AcknowledgeOperationalAlertUseCase acknowledge;
    private final AlertThresholds thresholds;

    public AdminOperationsController(
            GetMonthlyCostReportUseCase costs,
            ListOperationalAlertsUseCase alerts,
            AcknowledgeOperationalAlertUseCase acknowledge,
            AlertThresholds thresholds) {
        this.costs = costs;
        this.alerts = alerts;
        this.acknowledge = acknowledge;
        this.thresholds = thresholds;
    }

    @GetMapping("/costs")
    public ResponseEntity<MonthlyCostBody> costs(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) String month,
            @RequestAttribute(CORRELATION) String correlationId) {
        MonthlyCostBody body =
                AdminOperationsDtos.cost(costs.run(admin.actor(), month, correlationId));
        return noStore(body);
    }

    @GetMapping("/alerts")
    public ResponseEntity<AlertPage> alerts(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestAttribute(CORRELATION) String correlationId) {
        var found =
                alerts.run(
                        admin.actor(), status(status), PageRequest.of(page, size), correlationId);
        return noStore(AdminOperationsDtos.alerts(found, thresholds));
    }

    @GetMapping("/alerts/summary")
    public ResponseEntity<AlertSummary> summary(
            @AuthenticationPrincipal AuthenticatedAdministrator admin) {
        return noStore(new AlertSummary(alerts.activeCount(admin.actor())));
    }

    @PostMapping("/alerts/{alertId}/acknowledgement")
    public ResponseEntity<AlertBody> acknowledge(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID alertId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return noStore(
                AdminOperationsDtos.alert(acknowledge.run(admin.actor(), alertId, correlationId)));
    }

    private static AlertStatus status(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return AlertStatus.valueOf(text.strip());
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("fields", List.of(Map.of("field", "status", "code", "INVALID_VALUE"))));
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
