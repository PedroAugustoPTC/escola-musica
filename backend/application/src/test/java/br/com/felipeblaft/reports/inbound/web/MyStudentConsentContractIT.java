package br.com.felipeblaft.reports.inbound.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod;
import br.com.felipeblaft.reports.domain.privacy.ConsentRelationship;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.inbound.web.admin.AdminWebBase;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.privacy.ConsentView;
import br.com.felipeblaft.reports.usecase.privacy.GetCurrentConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Contrato do consentimento nas rotas do professor ({@code /my-students}) contra {@code
 * api-privacy-operations.openapi.yaml}: termo vigente, situação e registro a partir do aviso. Os
 * casos de uso são dublês; o teste cobre a camada web (sessão do professor e forma dos corpos).
 */
class MyStudentConsentContractIT extends ContractApiBase {

    private static final UUID STUDENT = UUID.fromString("7f4c1d3e-2a5b-4c6d-8e9f-0a1b2c3d4e5f");

    private static final OpenApiInteractionValidator CONTRACT =
            OpenApiInteractionValidator.createForSpecificationUrl(
                            Path.of("../..", AdminWebBase.PRIVACY_CONTRACT_RELATIVE)
                                    .toAbsolutePath()
                                    .normalize()
                                    .toUri()
                                    .toString())
                    .withLevelResolver(
                            LevelResolver.create()
                                    .withLevel(
                                            "validation.request.security.missing",
                                            ValidationReport.Level.IGNORE)
                                    .withLevel(
                                            "validation.request.parameter.header.missing",
                                            ValidationReport.Level.IGNORE)
                                    .build())
                    .build();

    @MockitoBean RecordConsentUseCase recordConsent;
    @MockitoBean GetCurrentConsentTermUseCase currentTerm;

    private Cookie session;

    @BeforeEach
    void signInTeacher() throws Exception {
        scenario.clara();
        session = signIn(ScenarioIT.CODE_CLARA);
    }

    private static ResultMatcher followsPrivacyContract() {
        return OpenApiValidationMatchers.openApi().isValid(CONTRACT);
    }

    @Test
    void statusOfAMinorWithoutConsentIsRequiredAndMissing() throws Exception {
        given(recordConsent.statusOf(any(), any(), anyString()))
                .willReturn(Optional.of(ConsentStatus.MISSING));

        mvc.perform(get("/api/v1/my-students/{id}/consent-status", STUDENT).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.status").value("MISSING"));
    }

    @Test
    void anAdultDoesNotRequireConsent() throws Exception {
        given(recordConsent.statusOf(any(), any(), anyString())).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/my-students/{id}/consent-status", STUDENT).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.required").value(false))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void someoneElsesStudentIsNotFound() throws Exception {
        given(recordConsent.statusOf(any(), any(), anyString()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/api/v1/my-students/{id}/consent-status", STUDENT).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void currentTermFollowsTheContract() throws Exception {
        given(currentTerm.run(any()))
                .willReturn(
                        new ConsentTermVersion(
                                UUID.randomUUID(),
                                1,
                                LocalDate.of(2026, 9, 20),
                                "Institutional text",
                                false,
                                UUID.randomUUID(),
                                Instant.parse("2026-09-21T13:00:00Z")));

        mvc.perform(get("/api/v1/my-students/consent-term").cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void recordingFromTheBlockNoticeAnswers201WithoutTheGuardianName() throws Exception {
        given(recordConsent.run(any(), any()))
                .willReturn(
                        new ConsentView(
                                UUID.randomUUID(),
                                ConsentStatus.VALID,
                                "Maria Guardian",
                                ConsentRelationship.MOTHER,
                                LocalDate.of(2026, 9, 1),
                                1,
                                ConsentCollectionMethod.SIGNED_PAPER,
                                Instant.parse("2026-09-21T13:00:00Z"),
                                ActionOrigin.WEB,
                                null));

        mvc.perform(
                        authenticated(
                                post("/api/v1/my-students/{id}/consents", STUDENT)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"consentingName\":\"Maria Guardian\","
                                                        + "\"relationship\":\"MOTHER\","
                                                        + "\"consentedOn\":\"2026-09-01\","
                                                        + "\"termVersion\":1,"
                                                        + "\"collectionMethod\":\"SIGNED_PAPER\"}"),
                                session))
                .andExpect(status().isCreated())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.consentingName").doesNotExist());
    }
}
