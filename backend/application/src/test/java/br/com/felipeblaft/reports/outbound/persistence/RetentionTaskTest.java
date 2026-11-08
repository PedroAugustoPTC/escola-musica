package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

/** Agendamento da retenção (FR-015): cron e fuso da configuração, perfil e tolerância a falha. */
class RetentionTaskTest {

    private final RunRetentionUseCase useCase = mock(RunRetentionUseCase.class);
    private final ClockPort clock = mock(ClockPort.class);
    private final RetentionTask task = new RetentionTask(useCase, clock);

    @Test
    void scheduleReadsCronAndSchoolTimezoneFromConfiguration() throws Exception {
        Method run = RetentionTask.class.getDeclaredMethod("run");

        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${reports.retention.cron}");
        assertThat(scheduled.zone()).isEqualTo("${reports.school.timezone}");
    }

    @Test
    void taskDoesNotExistInTheSeedProfile() {
        Profile profile = RetentionTask.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("!seed");
    }

    @Test
    void runsTheUseCaseWithTheRetentionActor() {
        when(clock.now()).thenReturn(Instant.parse("2026-03-01T12:00:00Z"));
        when(useCase.run(any(), anyString()))
                .thenReturn(
                        new RunRetentionUseCase.Result(
                                RetentionRunStatus.SUCCEEDED, UUID.randomUUID(), false, Map.of(), 0));

        task.run();

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(useCase, times(1)).run(actor.capture(), anyString());
        assertThat(actor.getValue().origin()).isEqualTo(ActionOrigin.RETENTION);
    }

    @Test
    void anExceptionNeverBringsDownTheScheduler() {
        when(useCase.run(any(), anyString())).thenThrow(new IllegalStateException("failure"));

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void anAlreadyRunningExecutionIsNotAnError() {
        when(useCase.run(any(), anyString()))
                .thenReturn(
                        new RunRetentionUseCase.Result(
                                RetentionRunStatus.RUNNING, null, true, Map.of(), 0));

        assertThatCode(task::run).doesNotThrowAnyException();
    }
}
