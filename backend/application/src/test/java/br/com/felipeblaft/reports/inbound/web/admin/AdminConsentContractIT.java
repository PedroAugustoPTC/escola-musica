package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod;
import br.com.felipeblaft.reports.domain.privacy.ConsentRelationship;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.usecase.privacy.ConsentView;
import br.com.felipeblaft.reports.usecase.privacy.ListPendingConsentsUseCase.Pending;
import br.com.felipeblaft.reports.usecase.privacy.PublishConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RevokeConsentUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

/**
 * Contrato do consentimento do responsável na área administrativa (feature 007, US2) contra {@code
 * api-privacy-operations.openapi.yaml}: histórico, registro, revogação, pendências e termos.
 */
class AdminConsentContractIT extends AdminWebBase {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID CONSENT = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    private static final String CONSENT_BODY =
            "{\"consentingName\":\"Maria Guardian\",\"relationship\":\"MOTHER\","
                    + "\"consentedOn\":\"2026-09-01\",\"termVersion\":1,"
                    + "\"collectionMethod\":\"SIGNED_PAPER\"}";

    private static ConsentView view(ConsentStatus status, LocalDate revokedOn) {
        return new ConsentView(
                CONSENT,
                status,
                "Maria Guardian",
                ConsentRelationship.MOTHER,
                LocalDate.of(2026, 9, 1),
                1,
                ConsentCollectionMethod.SIGNED_PAPER,
                Instant.parse("2026-09-21T13:00:00Z"),
                ActionOrigin.WEB,
                revokedOn);
    }

    @Test
    void historyFollowsTheContractAndIsNotCached() throws Exception {
        given(listStudentConsents.run(any(), any()))
                .willReturn(List.of(view(ConsentStatus.VALID, null)));

        mvc.perform(
                        get(BASE + "/students/" + STUDENT + "/consents")
                                .cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$[0].status").value("VALID"))
                .andExpect(jsonPath("$[0].termVersion").value(1))
                .andExpect(jsonPath("$[0].recordedOrigin").value("WEB"));
    }

    @Test
    void recordCreatesTheConsentAndPassesTheInputToTheUseCase() throws Exception {
        given(recordConsent.run(any(), any())).willReturn(view(ConsentStatus.VALID, null));

        mvc.perform(
                        asAdmin(post(BASE + "/students/" + STUDENT + "/consents"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CONSENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.id").value(CONSENT.toString()));

        ArgumentCaptor<RecordConsentUseCase.Command> command =
                ArgumentCaptor.forClass(RecordConsentUseCase.Command.class);
        verify(recordConsent).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(command.getValue().input().termVersion()).isEqualTo(1);
        assertThat(command.getValue().input().relationship()).isEqualTo("MOTHER");
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void invalidConsentIsA400WithTheFieldsOfTheCore() throws Exception {
        given(recordConsent.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                java.util.Map.of(
                                        "fields",
                                        List.of(
                                                java.util.Map.of(
                                                        "field",
                                                        "consent.termVersion",
                                                        "code",
                                                        "INVALID_DATA")))));

        mvc.perform(
                        asAdmin(post(BASE + "/students/" + STUDENT + "/consents"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CONSENT_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields[0].field").value("consent.termVersion"));
    }

    @Test
    void revocationReturnsTheRevokedConsentAndAFutureOrBadDateNeverReachesTheUseCase()
            throws Exception {
        given(revokeConsent.run(any(), any()))
                .willReturn(view(ConsentStatus.REVOKED, LocalDate.of(2026, 9, 10)));

        mvc.perform(
                        asAdmin(post(BASE + "/consents/" + CONSENT + "/revocation"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"revokedOn\":\"2026-09-10\",\"revokedBy\":\"MOTHER\"}"))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedOn").value("2026-09-10"));

        ArgumentCaptor<RevokeConsentUseCase.Command> command =
                ArgumentCaptor.forClass(RevokeConsentUseCase.Command.class);
        verify(revokeConsent).run(any(), command.capture());
        assertThat(command.getValue().revokedBy()).isEqualTo("MOTHER");

        mvc.perform(
                        asAdmin(post(BASE + "/consents/" + CONSENT + "/revocation"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"revokedOn\":\"not-a-date\",\"revokedBy\":\"MOTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void revokingTwiceIsA409() throws Exception {
        given(revokeConsent.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.CONSENT_ALREADY_REVOKED));

        mvc.perform(
                        asAdmin(post(BASE + "/consents/" + CONSENT + "/revocation"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"revokedOn\":\"2026-09-10\",\"revokedBy\":\"MOTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_ALREADY_REVOKED"));
    }

    @Test
    void pendingListFollowsTheContract() throws Exception {
        given(listPendingConsents.run(any(), any()))
                .willReturn(
                        new PageResult<>(
                                List.of(new Pending(STUDENT, "Aluno Fictício", ConsentStatus.MISSING)),
                                0,
                                20,
                                1));

        mvc.perform(get(BASE + "/consents/pending").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.items[0].reason").value("MISSING"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void termImpactCountsTheStudentsWhoWouldBecomePending() throws Exception {
        given(listPendingConsents.countValid(any())).willReturn(7);

        mvc.perform(get(BASE + "/consent-terms/impact").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.studentsAffected").value(7));
    }

    @Test
    void termsAreListedNewestFirstWithTheCurrentOneMarkedAndPublishedWith201() throws Exception {
        ConsentTermVersion second = term(2, true);
        ConsentTermVersion first = term(1, false);
        given(listConsentTerms.run(any())).willReturn(List.of(second, first));
        given(publishConsentTerm.run(any(), any())).willReturn(second);

        mvc.perform(get(BASE + "/consent-terms").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$[0].number").value(2))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false));

        mvc.perform(
                        asAdmin(post(BASE + "/consent-terms"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"publishedOn\":\"2026-09-20\",\"text\":\"Institutional text\","
                                                + "\"requiresNewConsent\":true}"))
                .andExpect(status().isCreated())
                .andExpect(followsPrivacyContract());

        ArgumentCaptor<PublishConsentTermUseCase.Command> command =
                ArgumentCaptor.forClass(PublishConsentTermUseCase.Command.class);
        verify(publishConsentTerm).run(any(), command.capture());
        assertThat(command.getValue().requiresNewConsent()).isTrue();
    }

    private static ConsentTermVersion term(int number, boolean requiresNew) {
        return new ConsentTermVersion(
                UUID.randomUUID(),
                number,
                LocalDate.of(2026, 9, 20),
                "Institutional text " + number,
                requiresNew,
                UUID.randomUUID(),
                Instant.parse("2026-09-21T13:00:00Z"));
    }
}
