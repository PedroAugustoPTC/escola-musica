package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.telegram.ConsentDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.privacy.GetCurrentConsentTermUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase.Result;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase.Summary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Passos C1 a C5 do consentimento no cadastro guiado do Telegram (contrato {@code
 * telegram-consent.md}), só para aluno menor: o termo vigente é apresentado, "não" descarta o
 * rascunho, data futura repete a pergunta, termo publicado no meio reapresenta o C1 e o resumo
 * final nunca repete o nome de quem consentiu. O núcleo é dublê; o adaptador só encaminha e
 * apresenta.
 */
@ConversationState({10})
class TelegramConsentFlowIT extends RegistryConversationBase {

    private static final String GUARDIAN_NAME = "Marta Lima";
    private static final String CONSENTING_NAME = "Roberto Lima";

    @MockitoBean GetCurrentConsentTermUseCase currentTerm;

    @BeforeEach
    void prepare() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        when(currentTerm.run(any()))
                .thenReturn(
                        new ConsentTermVersion(
                                UUID.randomUUID(),
                                2,
                                LocalDate.of(2026, 9, 1),
                                "Institutional consent text",
                                true,
                                UUID.randomUUID(),
                                Instant.parse("2026-09-01T12:00:00Z")));
    }

    private static Result consentStep(ConsentDraft.Part part) {
        return new Result(Field.CONSENT, true, null, null, part);
    }

    /** Percorre nome, nascimento, CPF e responsável até o C1. */
    private void reachFirstConsentQuestion() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Davi Lima");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/2014");
        when(collect.run(any(), any()))
                .thenReturn(new Result(Field.GUARDIAN, true, null, null));
        send(CPF);
        send(GUARDIAN_NAME);
        send("(11) 98888-7777");
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.ACKNOWLEDGE));
        send("marta@example.test");
    }

    @Test
    void firstQuestionShowsTheCurrentTermAndOffersYesNoAndCancel() {
        reachFirstConsentQuestion();

        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT");
        assertThat(lastText())
                .contains("versão 2", "Institutional consent text", "O responsável consentiu?")
                .doesNotContain("2014");
        assertThat(buttonData(lastMessage()))
                .containsExactly("v1:consent-yes:", "v1:consent-no:", "v1:reg-cancel:");
    }

    @Test
    void fullGuidedConsentEndsInASummaryThatNeverRepeatsTheConsentingName() {
        reachFirstConsentQuestion();

        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.NAME));
        tap("v1:consent-yes:");
        assertThat(lastText()).startsWith("Qual o nome de quem consentiu?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT_NAME");

        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.RELATIONSHIP));
        send(CONSENTING_NAME);
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT_RELATIONSHIP");
        assertThat(buttonData(lastMessage()))
                .contains(
                        "v1:consent-rel:MOTHER",
                        "v1:consent-rel:FATHER",
                        "v1:consent-rel:LEGAL_GUARDIAN",
                        "v1:consent-rel:OTHER");

        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.DATE));
        tap("v1:consent-rel:MOTHER");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT_DATE");
        assertThat(buttonData(lastMessage())).contains("v1:consent-date:today");

        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.METHOD));
        tap("v1:consent-date:today");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT_METHOD");
        assertThat(buttonData(lastMessage()))
                .contains(
                        "v1:consent-method:SIGNED_PAPER",
                        "v1:consent-method:DIGITAL",
                        "v1:consent-method:OTHER");

        when(collect.run(any(), any()))
                .thenReturn(
                        new Result(
                                null,
                                true,
                                new Summary(
                                        "Davi Lima",
                                        LocalDate.of(2014, 3, 5),
                                        null,
                                        GUARDIAN_NAME,
                                        2,
                                        LocalDate.of(2026, 9, 21)),
                                TOKEN,
                                null));
        tap("v1:consent-method:DIGITAL");
        assertThat(lastText())
                .contains("Consentimento: registrado (termo v2, 21/09/2026)")
                .doesNotContain(CONSENTING_NAME);
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");

        ArgumentCaptor<CollectStudentRegistrationFieldUseCase.Command> commands =
                ArgumentCaptor.forClass(CollectStudentRegistrationFieldUseCase.Command.class);
        verify(collect, org.mockito.Mockito.atLeast(1)).run(any(), commands.capture());
        List<CollectStudentRegistrationFieldUseCase.Command> consentCommands =
                commands.getAllValues().stream().filter(c -> c.field() == Field.CONSENT).toList();
        assertThat(consentCommands)
                .extracting(CollectStudentRegistrationFieldUseCase.Command::text)
                .containsExactly("", CONSENTING_NAME, "MOTHER", "today", "DIGITAL");
        assertThat(persistedData()).doesNotContain(CONSENTING_NAME);
    }

    @Test
    void answeringNoDiscardsTheDraftAndTheRegistrationIsNotCompleted() {
        reachFirstConsentQuestion();

        tap("v1:consent-no:");

        verify(cancel).run(any(), any());
        verify(confirm, never()).run(any(), any());
        assertThat(lastText()).contains("O cadastro foi cancelado");
        assertThat(persistedFlow()).isNotEqualTo("STUDENT_REGISTRY");
    }

    @Test
    void futureOrInvalidDateRepeatsTheSameQuestion() {
        reachFirstConsentQuestion();
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.NAME));
        tap("v1:consent-yes:");
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.RELATIONSHIP));
        send(CONSENTING_NAME);
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.DATE));
        tap("v1:consent-rel:MOTHER");

        when(collect.run(any(), any()))
                .thenThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "consent.consentedOn",
                                                        "code",
                                                        "INVALID_DATA")))));
        send("01/01/2099");

        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT_DATE");
        assertThat(lastText()).contains("Qual a data do consentimento?");
    }

    @Test
    void termPublishedInTheMiddleMakesTheConfirmationReshowTheFirstQuestion() {
        reachFirstConsentQuestion();
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.NAME));
        tap("v1:consent-yes:");
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.RELATIONSHIP));
        send(CONSENTING_NAME);
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.DATE));
        tap("v1:consent-rel:MOTHER");
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.METHOD));
        tap("v1:consent-date:today");
        when(collect.run(any(), any()))
                .thenReturn(
                        new Result(
                                null,
                                true,
                                new Summary(
                                        "Davi Lima",
                                        LocalDate.of(2014, 3, 5),
                                        null,
                                        GUARDIAN_NAME,
                                        1,
                                        LocalDate.of(2026, 9, 21)),
                                TOKEN,
                                null));
        tap("v1:consent-method:DIGITAL");

        when(confirm.run(any(), any()))
                .thenThrow(
                        new DomainException(
                                ErrorCode.VALIDATION_FAILED,
                                Map.of(
                                        "fields",
                                        List.of(
                                                Map.of(
                                                        "field",
                                                        "consent.termVersion",
                                                        "code",
                                                        "INVALID_DATA")))));
        when(collect.run(any(), any())).thenReturn(consentStep(ConsentDraft.Part.ACKNOWLEDGE));
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CONSENT");
        assertThat(lastText()).contains("O responsável consentiu?");
        verify(cancel, never()).run(any(), any());
    }

    @Test
    void consentButtonsOutsideTheirStepAreExpired() {
        send("cadastrar aluno");

        tap("v1:consent-yes:");

        assertThat(lastText()).isNotBlank();
        verify(collect, never()).run(any(), any());
    }
}
