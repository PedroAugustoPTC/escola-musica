package br.com.felipeblaft.reports.inbound.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.config.ApplicationProperties.Bootstrap;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase.Outcome;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Recuperação do acesso (FR-034b): o mantenedor remove as linhas de {@code administrator}, preenche
 * o {@code .env} e reinicia; o primeiro administrador volta com senha provisória. Não existe
 * variável, rota nem comando que redefina a senha de um administrador existente. Aqui ficam as
 * garantias do adaptador de subida; a preservação de {@code authorship_record} e a cascata das
 * sessões (banco real) entram no Fecho (S5), com os adaptadores de persistência integrados.
 */
class AdministratorRecoveryIT {

    private final BootstrapFirstAdministratorUseCase useCase =
            mock(BootstrapFirstAdministratorUseCase.class);

    private FirstAdministratorBootstrap bootstrap(String password) {
        return new FirstAdministratorBootstrap(
                useCase,
                new Bootstrap("Administrador Fictício", "admin@example.test", password),
                new MessageCatalog());
    }

    @Test
    void restartingAfterRemovingTheAdministratorsRecreatesTheFirstOne() {
        when(useCase.run(any(), any()))
                .thenReturn(Outcome.ALREADY_EXISTS, Outcome.CREATED, Outcome.ALREADY_EXISTS);

        bootstrap("senha-fictícia-de-teste-1").bootstrap(); // já existe: nada muda
        bootstrap("senha-fictícia-de-teste-2").bootstrap(); // linhas removidas: recria
        bootstrap("senha-fictícia-de-teste-3").bootstrap(); // existe de novo: ignora

        verify(useCase, times(3)).run(any(), any());
    }

    @Test
    void thereIsNoWayToResetThePasswordOfAnExistingAdministrator() {
        // a subida só chama o caso de uso de bootstrap, que ignora tudo se já existe administrador
        when(useCase.run(any(), any())).thenReturn(Outcome.ALREADY_EXISTS);

        bootstrap("outra-senha-fictícia-9").bootstrap();

        verify(useCase, times(1)).run(any(), any());
        // o adaptador não expõe nenhuma operação além de subir
        assertThat(
                        Arrays.stream(FirstAdministratorBootstrap.class.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .map(Method::getName))
                .containsExactlyInAnyOrder("run", "bootstrap");
    }
}
