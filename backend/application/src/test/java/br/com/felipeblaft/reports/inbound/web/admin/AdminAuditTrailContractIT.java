package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Contrato da trilha de auditoria (feature 007, US1) contra {@code
 * api-privacy-operations.openapi.yaml}: rotas, filtros, paginação, rótulos resolvidos na exibição,
 * "Aluno removido" para o eliminado, {@code Cache-Control} e a recusa de período.
 */
class AdminAuditTrailContractIT extends AdminWebBase {

    private static final String URL = BASE + "/audit-trail";
    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID EVENT = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    private static AuditTrailEntry entry(boolean erased) {
        return new AuditTrailEntry(
                EVENT,
                42,
                Instant.parse("2026-09-21T13:00:00Z"),
                UUID.fromString("00000000-0000-4000-8000-0000000000a1"),
                Role.ADMINISTRATOR,
                "Administrador Local",
                ActionOrigin.WEB,
                "STUDENT_REGISTER",
                "STUDENT",
                STUDENT,
                erased ? null : "Aluno Fictício",
                erased,
                null,
                null,
                AuthorshipResult.SUCCESS,
                null,
                false);
    }

    @Test
    void listsEventsFollowingTheContractWithResolvedLabels() throws Exception {
        given(queryAuditTrail.run(any(), any()))
                .willReturn(new PageResult<>(List.of(entry(false)), 0, 25, 1));

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("from", "2026-09-15")
                                .param("to", "2026-09-21")
                                .param("targetId", STUDENT.toString()))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sequence").value(42))
                .andExpect(jsonPath("$.items[0].actorRole").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.items[0].actorLabel").value("Administrador Local"))
                .andExpect(jsonPath("$.items[0].targetLabel").value("Aluno Fictício"))
                .andExpect(jsonPath("$.items[0].hasOriginHash").value(false));

        ArgumentCaptor<QueryAuditTrailUseCase.Command> command =
                ArgumentCaptor.forClass(QueryAuditTrailUseCase.Command.class);
        verify(queryAuditTrail).run(any(), command.capture());
        assertThat(command.getValue().from()).hasToString("2026-09-15");
        assertThat(command.getValue().targetId()).isEqualTo(STUDENT);
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void erasedStudentIsShownAsRemovedWithTheShortIdentifier() throws Exception {
        given(queryAuditTrail.run(any(), any()))
                .willReturn(new PageResult<>(List.of(entry(true)), 0, 25, 1));

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("from", "2026-09-21")
                                .param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.items[0].targetLabel").value("Aluno removido · #000000"));
    }

    @Test
    void periodRefusalIsA400WithTheFieldPeriod() throws Exception {
        given(queryAuditTrail.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "period",
                                                        "code",
                                                        "PERIOD_TOO_LONG")))));

        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("from", "2026-01-01")
                                .param("to", "2026-09-21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownEnumerationValueIsRefusedBeforeTheUseCase() throws Exception {
        mvc.perform(
                        get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                .param("from", "2026-09-21")
                                .param("to", "2026-09-21")
                                .param("origin", "FAX"))
                .andExpect(status().isBadRequest());

        verify(queryAuditTrail, never()).run(any(), any());
    }

    @Test
    void integrityRouteVerifiesOnDemandAndReadsTheLastResultOtherwise() throws Exception {
        given(verifyAuditChain.run(any(), any()))
                .willReturn(
                        new VerifyAuditChainUseCase.Result(
                                VerifyAuditChainUseCase.Status.BROKEN, 120, 77L));

        mvc.perform(
                        get(URL + "/integrity")
                                .cookie(adminCookie(ADMIN_TOKEN))
                                .param("verifyNow", "true"))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.status").value("BROKEN"))
                .andExpect(jsonPath("$.checkedEvents").value(120))
                .andExpect(jsonPath("$.firstBrokenSequence").value(77));

        // sem verificação pedida, devolve a última (aqui, nenhuma ainda)
        mvc.perform(get(URL + "/integrity").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.status").value("NEVER_VERIFIED"));
    }

    @Test
    void responsesNeverCarryAStudentNameOfTheCanary() throws Exception {
        given(queryAuditTrail.run(any(), any()))
                .willReturn(new PageResult<>(List.of(entry(false)), 0, 25, 1));

        String body =
                mvc.perform(
                                get(URL).cookie(adminCookie(ADMIN_TOKEN))
                                        .param("from", "2026-09-21")
                                        .param("to", "2026-09-21"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body).doesNotContain(CanaryStudent.GUARDIAN_NAME).doesNotContain("cpf");
    }
}
