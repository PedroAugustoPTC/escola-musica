package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.privacy.PublishConsentTermUseCase.Command;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Publicar versão do termo: número sequencial, texto de 1 a 20 000, data não futura. */
class PublishConsentTermUseCaseTest {

    private final ConsentScenario c = new ConsentScenario();

    private static Command command(String text, boolean requiresNew) {
        return new Command(LocalDate.of(2026, 9, 20), text, requiresNew, "req-1");
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void numbersAreSequentialAndTheCurrentVersionIsTheLast() {
        ConsentTermVersion first = c.publish.run(c.administrator(), command("First text", false));
        ConsentTermVersion second = c.publish.run(c.administrator(), command("Second text", true));

        assertThat(first.number()).isEqualTo(1);
        assertThat(second.number()).isEqualTo(2);
        assertThat(c.currentTerm.run(c.administrator()).number()).isEqualTo(2);
        assertThat(c.listTerms.run(c.administrator()))
                .extracting(ConsentTermVersion::number)
                .containsExactly(2, 1);
        var event = c.recorder.ofOperation("CONSENT_TERM_PUBLISH").getLast();
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.CONSENT_TERM);
        assertThat(event.targetId()).isEqualTo(second.id());
    }

    @Test
    void textLimitsAndFutureDateAreRefused() {
        assertThatThrownBy(() -> c.publish.run(c.administrator(), command("  ", false)))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(
                        () -> c.publish.run(c.administrator(), command("x".repeat(20_001), false)))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(
                        () ->
                                c.publish.run(
                                        c.administrator(),
                                        new Command(
                                                LocalDate.of(2026, 9, 22), "Text", false, "req-1")))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(c.terms.listAll()).isEmpty();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void onlyTheAdministratorPublishesButBothRolesReadTheCurrentVersion() {
        c.term(1, false);

        assertThatThrownBy(() -> c.publish.run(ActorFixtures.teacherWeb(), command("Text", false)))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.currentTerm.run(ActorFixtures.teacherWeb()).number()).isEqualTo(1);
        assertThat(c.currentTerm.run(ActorFixtures.teacherTelegram()).number()).isEqualTo(1);
    }

    @Test
    void currentTermWithoutAnyVersionIsNotFound() {
        assertThatThrownBy(() -> c.currentTerm.run(c.administrator()))
                .satisfies(
                        error -> assertThat(codeOf(error)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void versionThatRequiresNewConsentSupersedesTheOlderConsents() {
        Teacher clara = c.teacher("Clara Souza");
        Student student = c.minor("Matteo Mafra", clara);
        c.consentTo(student);
        assertThat(c.guard.blocks(student.id())).isFalse();

        c.publish.run(c.administrator(), command("New text", true));

        assertThat(c.guard.blocks(student.id())).isTrue();
    }

    @Test
    void versionThatDoesNotRequireNewConsentKeepsThemValid() {
        Teacher clara = c.teacher("Clara Souza");
        Student student = c.minor("Matteo Mafra", clara);
        c.consentTo(student);

        c.publish.run(c.administrator(), command("Compatible text", false));

        assertThat(c.guard.blocks(student.id())).isFalse();
    }
}
