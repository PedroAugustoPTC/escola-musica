package br.com.felipeblaft.reports.inbound.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.CostAggregate;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport.StudentCost;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato do custo e dos alertas de operação (feature 007, US5) contra {@code
 * api-privacy-operations.openapi.yaml}: mês inválido → 400, lista com {@code effectivePolicy},
 * resumo sem evento na trilha e reconhecimento recusado (409) para o tipo que normaliza sozinho.
 */
class AdminOperationsContractIT extends AdminWebBase {

    private static final String URL = BASE + "/operations";
    private static final UUID ALERT = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    private static OperationalAlert alert(AlertStatus status) {
        return new OperationalAlert(
                ALERT,
                AlertType.AUDIT_CHAIN_BROKEN,
                "audit-trail",
                "CHAIN_BROKEN",
                status,
                T0,
                T0.plusSeconds(60),
                status == AlertStatus.NORMALIZED ? T0.plusSeconds(120) : null,
                2);
    }

    private static MonthlyCostReport report() {
        CostAggregate some = new CostAggregate(2, new BigDecimal("0.012"), 6000, 1);
        return new MonthlyCostReport(
                YearMonth.of(2026, 9),
                new BigDecimal("12.50"),
                some,
                some,
                some,
                CostAggregate.EMPTY,
                List.of(new StudentCost(STUDENT, "Aluno Fictício", some)),
                Map.of("WEB", some),
                Map.of("provider-a", some));
    }

    @Test
    void costsFollowTheContract() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(getMonthlyCost.run(any(), eq("2026-09"), any())).willReturn(report());

        mvc.perform(get(URL + "/costs").cookie(adminCookie(ADMIN_TOKEN)).param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.infrastructureUsd").value(12.5))
                .andExpect(jsonPath("$.grandTotalUsd").value(12.512))
                .andExpect(jsonPath("$.perStudent[0].studentLabel").value("Aluno Fictício"))
                .andExpect(jsonPath("$.perStudent[0].count").value(2))
                .andExpect(jsonPath("$.byChannel.WEB.totalDurationMs").value(6000))
                .andExpect(jsonPath("$.byProvider['provider-a'].failedCalls").value(1));
    }

    @Test
    void invalidMonthIsA400() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(getMonthlyCost.run(any(), any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "month",
                                                        "code",
                                                        "INVALID_VALUE")))));

        mvc.perform(get(URL + "/costs").cookie(adminCookie(ADMIN_TOKEN)).param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void alertsListCarriesTheEffectivePolicy() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(listAlerts.run(any(), eq(AlertStatus.ACTIVE), any(), any()))
                .willReturn(new PageResult<>(List.of(alert(AlertStatus.ACTIVE)), 0, 20, 1));

        mvc.perform(get(URL + "/alerts").cookie(adminCookie(ADMIN_TOKEN)).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("AUDIT_CHAIN_BROKEN"))
                .andExpect(jsonPath("$.items[0].component").value("audit-trail"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectivePolicy.queueMaxItems.value").value(50))
                .andExpect(jsonPath("$.effectivePolicy.queueMaxItems.source").value("DEFAULT"))
                .andExpect(jsonPath("$.effectivePolicy.providerWindowMinutes.value").value(15));
    }

    @Test
    void unknownStatusIsRefusedBeforeTheUseCase() throws Exception {
        authenticate(ADMIN_TOKEN, false);

        mvc.perform(get(URL + "/alerts").cookie(adminCookie(ADMIN_TOKEN)).param("status", "GONE"))
                .andExpect(status().isBadRequest());

        verify(listAlerts, never()).run(any(), any(), any(), any());
    }

    @Test
    void summaryCountsActiveAlertsAndDoesNotUseTheEventfulList() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(listAlerts.activeCount(any())).willReturn(3);

        mvc.perform(get(URL + "/alerts/summary").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.activeCount").value(3));

        verify(listAlerts, never()).run(any(), any(), any(), any());
    }

    @Test
    void acknowledgementNormalizesTheChainAlert() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(acknowledgeAlert.run(any(), eq(ALERT), any()))
                .willReturn(alert(AlertStatus.NORMALIZED));

        mvc.perform(asAdmin(post(URL + "/alerts/{id}/acknowledgement", ALERT)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.status").value("NORMALIZED"));
    }

    @Test
    void acknowledgingATypeThatNormalizesByItselfIsA409() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(acknowledgeAlert.run(any(), eq(ALERT), any()))
                .willThrow(new DomainException(ErrorCode.ALERT_NOT_ACKNOWLEDGEABLE));

        mvc.perform(asAdmin(post(URL + "/alerts/{id}/acknowledgement", ALERT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALERT_NOT_ACKNOWLEDGEABLE"));
    }

    @Test
    void unknownAlertIsA404() throws Exception {
        authenticate(ADMIN_TOKEN, false);
        given(acknowledgeAlert.run(any(), eq(ALERT), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(asAdmin(post(URL + "/alerts/{id}/acknowledgement", ALERT)))
                .andExpect(status().isNotFound());
    }
}
