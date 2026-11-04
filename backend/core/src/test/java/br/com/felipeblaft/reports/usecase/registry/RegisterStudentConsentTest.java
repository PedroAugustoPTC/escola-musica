package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cadastro de aluno com consentimento (FR-030, FR-031, FR-081): menor sem consentimento é recusado,
 * com o consentimento é aceito e grava aluno, consentimento e os dois eventos na mesma transação;
 * os mesmos resultados para administrador, professor na Web e professor no Telegram.
 */
class RegisterStudentConsentTest {

    private final ConsentRegistryScenario c = new ConsentRegistryScenario();
    private final Teacher clara = c.teacher("Clara Souza");

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> fields(Throwable thrown) {
        DomainException error = (DomainException) thrown;
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        return (List<Map<String, String>>) error.details().get("fields");
    }

    private Actor[] actors() {
        return new Actor[] {
            ConsentRegistryScenario.administrator(),
            ActorFixtures.teacherWeb(clara.id()),
            ActorFixtures.teacherTelegram(clara.id())
        };
    }

    @Test
    void minorWithoutConsentIsRefusedOnTheConsentFieldForEveryActorAndNothingIsSaved() {
        int index = 0;
        for (Actor actor : actors()) {
            String cpf = index++ == 0 ? ConsentRegistryScenario.CPF_A : ConsentRegistryScenario.CPF_B;
            assertThatThrownBy(
                            () ->
                                    c.register.run(
                                            actor,
                                            c.registration(
                                                    ConsentRegistryScenario.minor("Matteo Mafra", cpf),
                                                    null,
                                                    clara.id())))
                    .satisfies(
                            error ->
                                    assertThat(fields(error))
                                            .containsExactly(
                                                    Map.of(
                                                            "field",
                                                            "consent",
                                                            "code",
                                                            "CONSENT_REQUIRED")));
        }
        assertThat(c.base.students.list(
                        br.com.felipeblaft.reports.domain.registry.StudentScope.all(),
                        br.com.felipeblaft.reports.domain.registry.StudentStatusFilter.ALL))
                .isEmpty();
        assertThat(c.base.consents.size()).isZero();
        assertThat(c.base.recorder.ofOperation("STUDENT_REGISTER")).isEmpty();
    }

    @Test
    void minorWithConsentIsSavedWithTheConsentAndBothEventsInTheSameTransaction() {
        UUID adminId = UUID.randomUUID();

        StudentView view =
                c.register.run(
                        ActorFixtures.administratorWeb(adminId),
                        c.registration(
                                ConsentRegistryScenario.minor("Matteo Mafra", ConsentRegistryScenario.CPF_A),
                                ConsentRegistryScenario.consent(),
                                clara.id()));

        assertThat(c.base.consents.findByStudent(view.id())).hasSize(1);
        assertThat(c.base.recording.status(view.id())).isEqualTo(ConsentStatus.VALID);
        assertThat(c.base.recorder.inTransaction())
                .extracting(AuthorshipRecord::operation)
                .containsExactly("STUDENT_REGISTER", "CONSENT_RECORD");
        assertThat(c.base.recorder.ofOperation("CONSENT_RECORD").getFirst().authorId())
                .isEqualTo(adminId);
    }

    @Test
    void teacherOnBothChannelsGetsTheSameResultAndTheEventKeepsTheChannel() {
        StudentView web =
                c.register.run(
                        ActorFixtures.teacherWeb(clara.id()),
                        c.registration(
                                ConsentRegistryScenario.minor("Matteo Mafra", ConsentRegistryScenario.CPF_A),
                                ConsentRegistryScenario.consent()));
        StudentView telegram =
                c.register.run(
                        ActorFixtures.teacherTelegram(clara.id()),
                        c.registration(
                                ConsentRegistryScenario.minor("Lia Souza", ConsentRegistryScenario.CPF_B),
                                ConsentRegistryScenario.consent()));

        assertThat(c.base.consents.findByStudent(web.id())).hasSize(1);
        assertThat(c.base.consents.findByStudent(telegram.id())).hasSize(1);
        assertThat(c.base.recorder.ofOperation("CONSENT_RECORD"))
                .extracting(AuthorshipRecord::origin)
                .extracting(Enum::name)
                .containsExactly("WEB", "TELEGRAM");
    }

    @Test
    void adultNeedsNoConsentAndAnAdultConsentIsNotRecorded() {
        StudentView adult =
                c.register.run(
                        ConsentRegistryScenario.administrator(),
                        c.registration(
                                ConsentRegistryScenario.adult("Adult Souza", ConsentRegistryScenario.CPF_A),
                                null,
                                clara.id()));
        StudentView adultWithConsent =
                c.register.run(
                        ConsentRegistryScenario.administrator(),
                        c.registration(
                                ConsentRegistryScenario.adult("Second Adult", ConsentRegistryScenario.CPF_B),
                                ConsentRegistryScenario.consent(),
                                clara.id()));

        assertThat(c.base.consents.findByStudent(adult.id())).isEmpty();
        assertThat(c.base.consents.findByStudent(adultWithConsent.id())).isEmpty();
        assertThat(c.base.recorder.ofOperation("CONSENT_RECORD")).isEmpty();
    }

    @Test
    void invalidConsentFieldsAreRefusedBeforeSavingTheStudent() {
        ConsentInput invalid = new ConsentInput("A", "AUNT", "2099-01-01", 9, "FAX");

        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        ConsentRegistryScenario.administrator(),
                                        c.registration(
                                                ConsentRegistryScenario.minor("Matteo Mafra", ConsentRegistryScenario.CPF_A),
                                                invalid,
                                                clara.id())))
                .satisfies(
                        error ->
                                assertThat(fields(error))
                                        .extracting(entry -> entry.get("field"))
                                        .containsExactlyInAnyOrder(
                                                "consent.consentingName",
                                                "consent.relationship",
                                                "consent.consentedOn",
                                                "consent.termVersion",
                                                "consent.collectionMethod"));

        assertThat(c.base.students.searchByCpfHash(
                        c.protection.hashCpf(
                                br.com.felipeblaft.reports.domain.registry.Cpf.of(ConsentRegistryScenario.CPF_A))))
                .isEmpty();
        assertThat(c.base.consents.size()).isZero();
    }

    @Test
    void initialLoadRegistersMinorsWithoutConsentBecauseTheConsentLoadComesAfter() {
        StudentView view =
                c.register.run(
                        ActorFixtures.initialLoad(),
                        c.registration(
                                ConsentRegistryScenario.minor("Matteo Mafra", ConsentRegistryScenario.CPF_A),
                                null));

        assertThat(c.base.recording.status(view.id())).isEqualTo(ConsentStatus.MISSING);
        assertThat(c.base.guard.blocks(view.id())).isTrue();
    }

    @Test
    void versionThatIsNotCurrentOrCompatibleIsRefused() {
        c.base.term(2, true);

        assertThatThrownBy(
                        () ->
                                c.register.run(
                                        ConsentRegistryScenario.administrator(),
                                        c.registration(
                                                ConsentRegistryScenario.minor("Matteo Mafra", ConsentRegistryScenario.CPF_A),
                                                ConsentRegistryScenario.consent(),
                                                clara.id())))
                .satisfies(
                        error ->
                                assertThat(fields(error))
                                        .containsExactly(
                                                Map.of(
                                                        "field",
                                                        "consent.termVersion",
                                                        "code",
                                                        "INVALID_DATA")));
    }
}
