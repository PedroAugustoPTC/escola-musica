package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

/**
 * O executor dos comandos operacionais só pode rodar no perfil que o {@code docker-entrypoint.sh}
 * ativa ({@code seed}) e nunca nos testes de integração ({@code test}). Os testes chamam os
 * comandos direto, então sem este teste um perfil trocado de nome passaria despercebido e o
 * contêiner subiria sem executar nada.
 */
class SeedCommandsExecutorProfileTest {

    private static boolean activeWith(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        Profile profile = SeedCommandsExecutor.class.getAnnotation(Profile.class);
        return environment.acceptsProfiles(Profiles.of(profile.value()));
    }

    @Test
    void runsInTheSeedProfileTheEntrypointActivates() {
        assertThat(activeWith("seed")).isTrue();
    }

    @Test
    void staysOutOfTheIntegrationTests() {
        assertThat(activeWith("seed", "test")).isFalse();
        assertThat(activeWith("test")).isFalse();
    }

    @Test
    void staysOutOfTheServer() {
        assertThat(activeWith()).isFalse();
    }
}
