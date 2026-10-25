package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.teacher.ReissueAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.teacher.TeacherView;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code issue-code} é a emergência (FR-044): chama {@link ReissueAccessCodeUseCase} com o ator de
 * sistema da carga inicial, que encerra as sessões Web e <strong>não</strong> encerra o vínculo do
 * Telegram. O caso de uso é um dublê aqui; o efeito real sobre sessões e vínculo é testado no
 * núcleo (W-B) e integrado no Fecho (S5).
 */
@ActiveProfiles("seed")
class IssueCodeCommandIT extends IntegrationBase {

    @Autowired IssueCodeCommand command;
    @Autowired TeacherRepository teachers;

    @MockitoBean ReissueAccessCodeUseCase useCase;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void reissuesByTheAdministrationUseCaseKeepingTheTelegramLink() {
        Teacher clara = Teacher.create("Clara Souza", "clara@example.test", true, clock.now());
        teachers.save(clara);
        when(useCase.run(any(), any()))
                .thenReturn(
                        new TeacherWithCode(
                                new TeacherView(
                                        clara.id(), "Clara Souza", "clara@example.test", true, true),
                                new Secret("K7QM2XPA"),
                                Instant.parse("2026-09-28T13:00:00Z")));

        int code = command.run(List.of("issue-code", "--email", " Clara@Example.test "), output);

        assertThat(code).isZero();
        assertThat(text())
                .containsPattern("clara@example\\.test → K7QM2XPA \\(válido até 28/09/2026 10:00\\)");
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<ReissueAccessCodeUseCase.Command> reissue =
                ArgumentCaptor.forClass(ReissueAccessCodeUseCase.Command.class);
        verify(useCase).run(actor.capture(), reissue.capture());
        assertThat(actor.getValue().isSystem()).isTrue();
        assertThat(actor.getValue().origin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
        assertThat(reissue.getValue().teacherId()).isEqualTo(clara.id());
        assertThat(reissue.getValue().endTelegramLink()).isFalse();
    }

    @Test
    void unknownTeacherIsReportedWithoutCallingTheUseCase() {
        int code = command.run(List.of("issue-code", "--email", "x@y.z"), output);

        assertThat(code).isEqualTo(1);
        assertThat(text()).contains("Professor não encontrado");
        verify(useCase, never()).run(any(), any());
    }

    @Test
    void refusalOfTheUseCaseIsPrintedWithTheCatalogText() {
        Teacher clara = Teacher.create("Clara Souza", "clara@example.test", true, clock.now());
        teachers.save(clara);
        when(useCase.run(any(), any())).thenThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));

        int code = command.run(List.of("issue-code", "--email", "clara@example.test"), output);

        assertThat(code).isEqualTo(1);
        assertThat(text()).isNotBlank().doesNotContain("→");
    }

    @Test
    void missingEmailPrintsTheUsage() {
        assertThat(command.run(List.of("issue-code"), output)).isEqualTo(2);
        assertThat(text()).contains("Uso: issue-code");
        verify(useCase, never()).run(any(), any());
    }
}
