package br.com.felipeblaft.reports.inbound.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.config.ApplicationProperties.Bootstrap;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase.Command;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase.Outcome;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * As situações da subida (contracts/initial-load.md §4) no adaptador {@code inbound.bootstrap}. A
 * decisão é do núcleo; aqui o caso de uso é um dublê que devolve o que o núcleo real devolve em
 * cada situação. As garantias que dependem do banco (criação atômica por duas instâncias, hash
 * Argon2id gravado, autoria "inicialização do sistema") entram no Fecho (S5), quando os adaptadores
 * de persistência dos fluxos W-E e W-F estiverem integrados.
 */
class FirstAdministratorBootstrapIT {

    private static final String PASSWORD = "senha-fictícia-de-teste-1";
    private static final String EMAIL = "admin@example.test";
    private static final String NAME = "Administrador Fictício";

    private final BootstrapFirstAdministratorUseCase useCase =
            mock(BootstrapFirstAdministratorUseCase.class);
    private final MessageCatalog catalog = new MessageCatalog();

    private FirstAdministratorBootstrap bootstrap(String name, String email, String password) {
        return new FirstAdministratorBootstrap(
                useCase, new Bootstrap(name, email, password), catalog);
    }

    /** Cada item é {@code campo:CODIGO}. */
    private static DomainException invalid(String... fields) {
        List<Map<String, String>> list =
                Arrays.stream(fields)
                        .map(
                                item ->
                                        Map.of(
                                                "field",
                                                item.split(":")[0],
                                                "code",
                                                item.split(":")[1]))
                        .toList();
        return new DomainException(ErrorCode.VALIDATION_FAILED, Map.of("fields", list));
    }

    @Test
    void noAdministratorAndValidVariablesCreatesTheFirstWithTheStartupActor() {
        when(useCase.run(any(), any())).thenReturn(Outcome.CREATED);

        try (LogCapture log = LogCapture.start()) {
            bootstrap(NAME, EMAIL, PASSWORD).bootstrap();

            assertThat(log.text())
                    .contains("Primeiro administrador criado")
                    .doesNotContain(PASSWORD);
        }

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(useCase).run(actor.capture(), command.capture());
        assertThat(actor.getValue().isSystem()).isTrue();
        assertThat(actor.getValue().origin()).isEqualTo(ActionOrigin.SYSTEM_STARTUP);
        assertThat(command.getValue().name()).isEqualTo(NAME);
        assertThat(command.getValue().email()).isEqualTo(EMAIL);
        assertThat(command.getValue().password().value()).isEqualTo(PASSWORD);
    }

    @Test
    void existingAdministratorIgnoresEvenInvalidVariablesWithoutWarning() {
        when(useCase.run(any(), any())).thenReturn(Outcome.ALREADY_EXISTS);

        try (LogCapture log = LogCapture.start()) {
            bootstrap("x", "inválido", "curta").bootstrap();

            assertThat(log.text()).contains("foram ignoradas").doesNotContain("curta", "inválido");
            assertThat(log.events()).noneMatch(e -> e.getLevel().toString().equals("WARN"));
        }
    }

    @Test
    void existingAdministratorWithoutVariablesIsSilent() {
        when(useCase.run(any(), any())).thenReturn(Outcome.ALREADY_EXISTS);

        try (LogCapture log = LogCapture.start()) {
            bootstrap("", null, "").bootstrap();

            assertThat(log.messages()).noneMatch(m -> m.contains("ADMIN_BOOTSTRAP"));
        }
    }

    @Test
    void noAdministratorAndNoVariablesStartsWithAPortugueseWarning() {
        when(useCase.run(any(), any()))
                .thenThrow(
                        invalid(
                                "name:INVALID_NAME",
                                "email:INVALID_EMAIL",
                                "password:PASSWORD_LENGTH"));

        try (LogCapture log = LogCapture.start()) {
            bootstrap("", "  ", null).bootstrap();

            assertThat(log.events())
                    .anyMatch(
                            e ->
                                    e.getLevel().toString().equals("WARN")
                                            && e.getFormattedMessage()
                                                    .contains("Nenhum administrador existe")
                                            && e.getFormattedMessage()
                                                    .contains("ADMIN_BOOTSTRAP_PASSWORD"));
        }
    }

    @Test
    void invalidPasswordRefusesToStartNamingTheVariableAndNeverTheValue() {
        when(useCase.run(any(), any())).thenThrow(invalid("password:PASSWORD_LENGTH"));

        try (LogCapture log = LogCapture.start()) {
            assertThatThrownBy(() -> bootstrap(NAME, EMAIL, "curta-demais").bootstrap())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD inválida")
                    .hasMessageContaining("A senha deve ter de 12 a 128 caracteres.")
                    .hasMessageNotContaining("curta-demais")
                    .hasMessageNotContaining(EMAIL)
                    .hasMessageNotContaining(NAME);
            assertThat(log.text()).doesNotContain("curta-demais");
        }
    }

    @Test
    void invalidEmailRefusesToStartNamingTheVariableOnly() {
        when(useCase.run(any(), any())).thenThrow(invalid("email:INVALID_EMAIL"));

        assertThatThrownBy(() -> bootstrap(NAME, "sem-arroba", PASSWORD).bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_EMAIL inválida")
                .hasMessageNotContaining("sem-arroba")
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void partialVariablesRefuseToStartListingEveryMissingOne() {
        when(useCase.run(any(), any()))
                .thenThrow(invalid("name:INVALID_NAME", "password:PASSWORD_LENGTH"));

        assertThatThrownBy(() -> bootstrap("", EMAIL, "").bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_NAME não informada")
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD não informada")
                .hasMessageContaining(".env");
    }

    @Test
    void validationWithoutFieldsStillRefusesWithAGenericButSafeMessage() {
        when(useCase.run(any(), any())).thenThrow(new DomainException(ErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> bootstrap(NAME, EMAIL, PASSWORD).bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_*")
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void unknownFieldCodeFallsBackToAGenericReason() {
        when(useCase.run(any(), any())).thenThrow(invalid("password:CODIGO_QUE_NAO_EXISTE"));

        assertThatThrownBy(() -> bootstrap(NAME, EMAIL, PASSWORD).bootstrap())
                .hasMessageContaining("valor recusado");
    }

    @Test
    void otherDomainErrorsPropagateUntouched() {
        DomainException other = new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        when(useCase.run(any(), any())).thenThrow(other);

        assertThatThrownBy(() -> bootstrap(NAME, EMAIL, PASSWORD).bootstrap()).isSameAs(other);
    }

    @Test
    void missingConfigurationBehavesAsNoVariables() {
        when(useCase.run(any(), any())).thenReturn(Outcome.ALREADY_EXISTS);

        new FirstAdministratorBootstrap(useCase, (Bootstrap) null, catalog).bootstrap();

        verify(useCase).run(any(), any());
    }

    @Test
    void runnerDelegatesToTheBootstrap() {
        when(useCase.run(any(), any())).thenReturn(Outcome.ALREADY_EXISTS);

        bootstrap(NAME, EMAIL, PASSWORD).run(null);

        verify(useCase).run(any(), any());
    }
}
