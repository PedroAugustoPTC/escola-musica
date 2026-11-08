package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase.Result;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Filtros da lista da visão da escola (feature 006, US3): professor, tipo, período e página, com
 * recusa {@code VALIDATION_FAILED} e {@code details.fields[]} sem repetir o valor recebido.
 */
class AdminSchoolHistoryFiltersContractIT extends AdminWebBase {

    private static final String URL = BASE + "/school-history";
    private static final UUID TEACHER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    private static Result emptyResult() {
        return new Result(null, new PageResult<>(List.of(), 0, 20, 0));
    }

    @Test
    void validFiltersReachTheUseCaseAndFollowTheContract() throws Exception {
        given(querySchoolHistory.run(any(), any())).willReturn(emptyResult());

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("teacherId", TEACHER.toString())
                                .param("type", "SEMESTER")
                                .param("from", "2026-01-01")
                                .param("to", "2026-06-30")
                                .param("page", "2")
                                .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(followsSchoolHistoryContract());

        ArgumentCaptor<QuerySchoolHistoryUseCase.Command> command =
                ArgumentCaptor.forClass(QuerySchoolHistoryUseCase.Command.class);
        verify(querySchoolHistory).run(any(), command.capture());
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
        assertThat(command.getValue().type()).isEqualTo(ReportType.SEMESTER);
        assertThat(command.getValue().from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(command.getValue().to()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(command.getValue().page().page()).isEqualTo(2);
        assertThat(command.getValue().page().size()).isEqualTo(50);
    }

    private MvcResult refused(String name, String value, String field, String code)
            throws Exception {
        return mvc.perform(get(URL).cookie(adminCookie(ADMIN_TOKEN)).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(followsSchoolHistoryContract())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields[0].field").value(field))
                .andExpect(jsonPath("$.details.fields[0].code").value(code))
                .andReturn();
    }

    @Test
    void malformedDatesAreRefusedPerFieldWithoutEchoingTheValue() throws Exception {
        MvcResult from = refused("from", "10/03/2026", "from", "INVALID_DATE");
        MvcResult to = refused("to", "2026-02-30", "to", "INVALID_DATE");

        assertThat(from.getResponse().getContentAsString()).doesNotContain("10/03/2026");
        assertThat(to.getResponse().getContentAsString()).doesNotContain("2026-02-30");
        verify(querySchoolHistory, never()).run(any(), any());
    }

    @Test
    void unknownTypeIsRefused() throws Exception {
        MvcResult result = refused("type", "ANNUAL", "type", "INVALID_REPORT_TYPE");

        assertThat(result.getResponse().getContentAsString()).doesNotContain("ANNUAL");
        verify(querySchoolHistory, never()).run(any(), any());
    }

    @Test
    void negativePageAndOversizedSizeAreRefused() throws Exception {
        refused("page", "-1", "page", "INVALID_DATA");
        refused("size", "101", "size", "INVALID_DATA");
        verify(querySchoolHistory, never()).run(any(), any());
    }

    @Test
    void periodStartAfterEndComesFromTheUseCaseAsValidationFailed() throws Exception {
        given(querySchoolHistory.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "from",
                                                        "code",
                                                        "PERIOD_START_AFTER_END")))));

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("from", "2026-05-02")
                                .param("to", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(followsSchoolHistoryContract())
                .andExpect(jsonPath("$.details.fields[0].field").value("from"))
                .andExpect(jsonPath("$.details.fields[0].code").value("PERIOD_START_AFTER_END"));
    }
}
