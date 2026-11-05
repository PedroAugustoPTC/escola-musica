package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.GetErasureReceiptUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListErasureRequestsUseCase.RequestView;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

/**
 * Contrato da eliminação a pedido (feature 007, US4) contra {@code
 * api-privacy-operations.openapi.yaml}: registro, lista, detalhe, prévia, confirmação e
 * comprovante.
 */
class AdminErasureContractIT extends AdminWebBase {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    private static final String REGISTER_BODY =
            "{\"requesterRole\":\"MOTHER\",\"requesterName\":\"Maria Guardian\","
                    + "\"requestedOn\":\"2026-11-01\",\"receivedVia\":\"EMAIL\","
                    + "\"protocolReference\":\"PROT-2026/01\"}";

    private static ErasureScope scope() {
        return new ErasureScope(Map.of("APPROVED", 2), Map.of("APPROVED", 1), 3, 2, 1);
    }

    private static ErasureRequest request(ErasureStatus status) {
        return new ErasureRequest(
                REQUEST,
                STUDENT,
                "MOTHER",
                status == ErasureStatus.COMPLETED ? null : "Maria Guardian",
                LocalDate.of(2026, 11, 1),
                "EMAIL",
                "PROT-2026/01",
                status,
                UUID.randomUUID(),
                null,
                null,
                null,
                status == ErasureStatus.COMPLETED ? NOW : null,
                status == ErasureStatus.RECEIVED ? null : scope(),
                NOW);
    }

    @Test
    void registerCreatesTheRequestAndIsNotCached() throws Exception {
        given(registerErasure.run(any(), any())).willReturn(request(ErasureStatus.RECEIVED));

        mvc.perform(
                        asAdmin(post(BASE + "/students/" + STUDENT + "/erasure-requests"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        ArgumentCaptor<RegisterErasureRequestUseCase.Command> command =
                ArgumentCaptor.forClass(RegisterErasureRequestUseCase.Command.class);
        verify(registerErasure).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(command.getValue().requestedOn()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(command.getValue().protocolReference()).isEqualTo("PROT-2026/01");
    }

    @Test
    void invalidInputIsA400() throws Exception {
        given(registerErasure.run(any(), any()))
                .willThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "protocolReference",
                                                        "code",
                                                        "INVALID_DATA")))));

        mvc.perform(
                        asAdmin(post(BASE + "/students/" + STUDENT + "/erasure-requests"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void badDateNeverReachesTheUseCase() throws Exception {
        mvc.perform(
                        asAdmin(post(BASE + "/students/" + STUDENT + "/erasure-requests"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_BODY.replace("2026-11-01", "ontem")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownStudentIs404AndOpenOrErasedIs409() throws Exception {
        given(registerErasure.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND))
                .willThrow(new DomainException(ErrorCode.ERASURE_ALREADY_OPEN))
                .willThrow(new DomainException(ErrorCode.STUDENT_ALREADY_ERASED));

        String url = BASE + "/students/" + STUDENT + "/erasure-requests";
        mvc.perform(
                        asAdmin(post(url))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_BODY))
                .andExpect(status().isNotFound());
        mvc.perform(
                        asAdmin(post(url))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERASURE_ALREADY_OPEN"));
        mvc.perform(
                        asAdmin(post(url))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_ERASED"));
    }

    @Test
    void listIsPagedAndFilteredByStatus() throws Exception {
        given(listErasures.list(any(), eq(ErasureStatus.COMPLETED)))
                .willReturn(
                        List.of(
                                new RequestView(
                                        request(ErasureStatus.COMPLETED),
                                        "Aluno removido · #E1E2E3")));

        mvc.perform(
                        get(BASE + "/erasure-requests?status=COMPLETED")
                                .cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].requesterName").doesNotExist())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void detailAndUnknownRequest() throws Exception {
        given(listErasures.get(any(), eq(REQUEST)))
                .willReturn(new RequestView(request(ErasureStatus.IN_PROGRESS), "Ana"));

        mvc.perform(get(BASE + "/erasure-requests/" + REQUEST).cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.scope.pdfs").value(3));

        given(listErasures.get(any(), eq(REQUEST)))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(get(BASE + "/erasure-requests/" + REQUEST).cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewReturnsTheScopeAndTheToken() throws Exception {
        given(previewErasure.run(any(), eq(REQUEST), any()))
                .willReturn(
                        new PreviewErasureUseCase.Preview(
                                scope(),
                                List.of(new PreviewErasureUseCase.TeacherView(UUID.randomUUID(), "Prof")),
                                "t".repeat(40),
                                NOW.plusSeconds(600),
                                new PrivacyOfficer("Encarregada", "dpo@example.test")));

        mvc.perform(asAdmin(post(BASE + "/erasure-requests/" + REQUEST + "/preview")))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.confirmationToken").value("t".repeat(40)))
                .andExpect(jsonPath("$.scope.assignments").value(2));
    }

    @Test
    void previewOfACompletedRequestIs409() throws Exception {
        given(previewErasure.run(any(), eq(REQUEST), any()))
                .willThrow(new DomainException(ErrorCode.ERASURE_ALREADY_COMPLETED));

        mvc.perform(asAdmin(post(BASE + "/erasure-requests/" + REQUEST + "/preview")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERASURE_ALREADY_COMPLETED"));
    }

    @Test
    void confirmPassesTokenAndWordAndReturnsTheRequest() throws Exception {
        given(confirmErasure.run(any(), any())).willReturn(request(ErasureStatus.COMPLETED));

        mvc.perform(
                        asAdmin(post(BASE + "/erasure-requests/" + REQUEST + "/confirmation"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirmationToken\":\""
                                                + "t".repeat(40)
                                                + "\",\"confirmationWord\":\"ELIMINAR\"}"))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        ArgumentCaptor<ConfirmErasureUseCase.Command> command =
                ArgumentCaptor.forClass(ConfirmErasureUseCase.Command.class);
        verify(confirmErasure).run(any(), command.capture());
        assertThat(command.getValue().requestId()).isEqualTo(REQUEST);
        assertThat(command.getValue().confirmationWord()).isEqualTo("ELIMINAR");
    }

    @Test
    void expiredConfirmationIs409() throws Exception {
        given(confirmErasure.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.CONFIRMATION_EXPIRED));

        mvc.perform(
                        asAdmin(post(BASE + "/erasure-requests/" + REQUEST + "/confirmation"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirmationToken\":\""
                                                + "t".repeat(40)
                                                + "\",\"confirmationWord\":\"ELIMINAR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_EXPIRED"));
    }

    @Test
    void receiptHasNoStudentData() throws Exception {
        given(getErasureReceipt.run(any(), eq(REQUEST), any()))
                .willReturn(
                        new GetErasureReceiptUseCase.Receipt(
                                REQUEST,
                                "e1e2e3",
                                "PROT-2026/01",
                                LocalDate.of(2026, 11, 1),
                                NOW,
                                scope(),
                                new PrivacyOfficer("Encarregada", "dpo@example.test")));

        mvc.perform(get(BASE + "/erasure-requests/" + REQUEST + "/receipt").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsPrivacyContract())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.studentShortId").value("e1e2e3"))
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.requesterName").doesNotExist());
    }

    @Test
    void receiptOfAnOpenRequestIs404() throws Exception {
        given(getErasureReceipt.run(any(), eq(REQUEST), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get(BASE + "/erasure-requests/" + REQUEST + "/receipt").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isNotFound());
    }
}
