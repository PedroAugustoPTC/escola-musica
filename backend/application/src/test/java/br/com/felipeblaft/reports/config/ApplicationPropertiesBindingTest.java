package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Feature 005 (T005): as propriedades de acesso do administrador, de limite contra sondagem de CPF
 * e do rascunho de aluno no Telegram são ligadas pelo {@code application.yaml} real, com os padrões
 * da spec, e as variáveis de ambiente do {@code .env.example} chegam às propriedades certas. Nenhum
 * padrão traz credencial.
 */
class ApplicationPropertiesBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(PropertiesConfig.class);

    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfig {}

    @Test
    @DisplayName("sem variáveis de ambiente, valem os padrões da spec 005")
    void defaultsMatchTheSpecification() {
        runner.run(
                context -> {
                    ApplicationProperties properties = context.getBean(ApplicationProperties.class);

                    ApplicationProperties.Administrator administrator = properties.administrator();
                    assertThat(administrator.sessionHours()).isEqualTo(2);
                    assertThat(administrator.provisionalPasswordDays()).isEqualTo(7);
                    assertThat(administrator.password().memoryKib()).isEqualTo(19456);
                    assertThat(administrator.password().iterations()).isEqualTo(2);

                    ApplicationProperties.CpfProbe probe = properties.access().cpfProbe();
                    assertThat(probe.maxRefusals()).isEqualTo(5);
                    assertThat(probe.windowMinutes()).isEqualTo(15);
                    assertThat(probe.lockMinutes()).isEqualTo(15);

                    ApplicationProperties.CpfLookup lookup = properties.access().cpfLookup();
                    assertThat(lookup.maxLookups()).isEqualTo(30);
                    assertThat(lookup.windowMinutes()).isEqualTo(60);
                    assertThat(lookup.lockMinutes()).isEqualTo(60);

                    assertThat(properties.telegram().studentDraftMinutes()).isEqualTo(30);
                    assertThat(properties.school().timezone().getId())
                            .isEqualTo("America/Sao_Paulo");
                });
    }

    @Test
    @DisplayName("o primeiro administrador e o segredo de autenticação não têm valor padrão")
    void noDefaultCredential() {
        runner.run(
                context -> {
                    ApplicationProperties properties = context.getBean(ApplicationProperties.class);

                    ApplicationProperties.Bootstrap bootstrap =
                            properties.administrator().bootstrap();
                    assertThat(bootstrap.name()).isNullOrEmpty();
                    assertThat(bootstrap.email()).isNullOrEmpty();
                    assertThat(bootstrap.password()).isNullOrEmpty();
                    assertThat(properties.security().authHmacSecret()).isNullOrEmpty();
                });
    }

    @Test
    @DisplayName("as variáveis do .env.example chegam às propriedades certas")
    void environmentVariablesReachTheirProperties() {
        runner.withSystemProperties(
                        "ADMIN_SESSION_HOURS=4",
                        "ADMIN_PROVISIONAL_PASSWORD_DAYS=3",
                        "ADMIN_PASSWORD_ARGON2_MEMORY_KIB=32768",
                        "ADMIN_PASSWORD_ARGON2_ITERATIONS=3",
                        "ACCESS_CPF_PROBE_MAX_REFUSALS=6",
                        "ACCESS_CPF_PROBE_WINDOW_MINUTES=20",
                        "ACCESS_CPF_PROBE_LOCK_MINUTES=25",
                        "ACCESS_CPF_LOOKUP_MAX=40",
                        "ACCESS_CPF_LOOKUP_WINDOW_MINUTES=90",
                        "ACCESS_CPF_LOOKUP_LOCK_MINUTES=120",
                        "TELEGRAM_STUDENT_DRAFT_MINUTES=45",
                        "ADMIN_BOOTSTRAP_NAME=Nome de Teste",
                        "ADMIN_BOOTSTRAP_EMAIL=admin@example.test",
                        "ADMIN_BOOTSTRAP_PASSWORD=bootstrap-password-for-test-only",
                        "AUTH_HMAC_SECRET=auth-secret-for-tests-with-over-32-bytes")
                .run(
                        context -> {
                            ApplicationProperties properties =
                                    context.getBean(ApplicationProperties.class);

                            ApplicationProperties.Administrator administrator =
                                    properties.administrator();
                            assertThat(administrator.sessionHours()).isEqualTo(4);
                            assertThat(administrator.provisionalPasswordDays()).isEqualTo(3);
                            assertThat(administrator.password().memoryKib()).isEqualTo(32768);
                            assertThat(administrator.password().iterations()).isEqualTo(3);
                            assertThat(administrator.bootstrap().name()).isEqualTo("Nome de Teste");
                            assertThat(administrator.bootstrap().email())
                                    .isEqualTo("admin@example.test");

                            assertThat(properties.access().cpfProbe().maxRefusals()).isEqualTo(6);
                            assertThat(properties.access().cpfProbe().windowMinutes())
                                    .isEqualTo(20);
                            assertThat(properties.access().cpfProbe().lockMinutes()).isEqualTo(25);
                            assertThat(properties.access().cpfLookup().maxLookups()).isEqualTo(40);
                            assertThat(properties.access().cpfLookup().windowMinutes())
                                    .isEqualTo(90);
                            assertThat(properties.access().cpfLookup().lockMinutes())
                                    .isEqualTo(120);
                            assertThat(properties.telegram().studentDraftMinutes()).isEqualTo(45);
                            assertThat(properties.security().authHmacSecret())
                                    .isEqualTo("auth-secret-for-tests-with-over-32-bytes");
                        });
    }

    @Test
    @DisplayName("a representação textual nunca expõe senha nem segredo")
    void toStringNeverExposesSecrets() {
        runner.withSystemProperties(
                        "ADMIN_BOOTSTRAP_PASSWORD=bootstrap-password-for-test-only",
                        "AUTH_HMAC_SECRET=auth-secret-for-tests-with-over-32-bytes")
                .run(
                        context -> {
                            ApplicationProperties properties =
                                    context.getBean(ApplicationProperties.class);

                            assertThat(properties.administrator().toString())
                                    .doesNotContain("bootstrap-password-for-test-only");
                            assertThat(properties.administrator().bootstrap().toString())
                                    .doesNotContain("bootstrap-password-for-test-only");
                            assertThat(properties.security().toString())
                                    .doesNotContain("auth-secret-for-tests-with-over-32-bytes");
                        });
    }
}
