package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdministratorFixtureTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final AdministratorRepository administrators = mock(AdministratorRepository.class);
    private final PasswordHashPort hasher = mock(PasswordHashPort.class);

    private Administrator inserted() {
        ArgumentCaptor<Administrator> captor = ArgumentCaptor.forClass(Administrator.class);
        verify(administrators).insertIfAbsent(captor.capture());
        return captor.getValue();
    }

    @Test
    void insertsAnActiveActivatedAdministratorWithTheKnownPassword() {
        when(administrators.insertIfAbsent(any())).thenReturn(true);
        when(hasher.hash(any())).thenReturn("password-hash");

        AdministratorFixture fixture =
                AdministratorFixture.insertActive(administrators, hasher, NOW);

        Administrator saved = inserted();
        assertThat(saved).isEqualTo(fixture.administrator());
        assertThat(saved.active()).isTrue();
        assertThat(saved.passwordHash()).isEqualTo("password-hash");
        assertThat(saved.passwordProvisional()).isFalse();
        assertThat(saved.passwordProvisionalExpiresAt()).isNull();
        assertThat(saved.activationCodeHash()).isNull();
        assertThat(saved.activatedAt()).isEqualTo(NOW);
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.email()).isEqualTo(AdministratorFixture.DEFAULT_EMAIL);
        verify(hasher).hash(new Secret(AdministratorFixture.DEFAULT_PASSWORD));
    }

    @Test
    void exposesTheCredentialsForTheSignInOfTheTest() {
        when(administrators.insertIfAbsent(any())).thenReturn(true);

        AdministratorFixture fixture =
                AdministratorFixture.insertActive(administrators, hasher, NOW);

        assertThat(fixture.email()).isEqualTo(AdministratorFixture.DEFAULT_EMAIL);
        assertThat(fixture.password()).isEqualTo(new Secret(AdministratorFixture.DEFAULT_PASSWORD));
    }

    @Test
    void defaultPasswordObeysThePasswordRules() {
        assertThat(
                        AdministratorFixture.DEFAULT_PASSWORD.codePointCount(
                                0, AdministratorFixture.DEFAULT_PASSWORD.length()))
                .isBetween(12, 128);
        assertThat(AdministratorFixture.DEFAULT_PASSWORD)
                .isNotEqualToIgnoringCase(AdministratorFixture.DEFAULT_EMAIL);
    }

    @Test
    void acceptsOtherNameAndEmailAndNormalizesTheEmail() {
        when(administrators.insertIfAbsent(any())).thenReturn(true);

        AdministratorFixture fixture =
                AdministratorFixture.insertActive(
                        administrators, hasher, NOW, "Beatriz Souza", "  Beatriz@School.TEST ");

        assertThat(fixture.administrator().name()).isEqualTo("Beatriz Souza");
        assertThat(fixture.administrator().email()).isEqualTo("beatriz@school.test");
        assertThat(fixture.email()).isEqualTo("beatriz@school.test");
    }

    @Test
    void failsWhenTheRepositoryRefusesTheInsert() {
        when(administrators.insertIfAbsent(any())).thenReturn(false);

        assertThatThrownBy(() -> AdministratorFixture.insertActive(administrators, hasher, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AdministratorFixture.DEFAULT_EMAIL);
    }

    @Test
    void toStringNeverRevealsThePassword() {
        when(administrators.insertIfAbsent(any())).thenReturn(true);

        AdministratorFixture fixture =
                AdministratorFixture.insertActive(administrators, hasher, NOW);

        assertThat(fixture.toString()).doesNotContain(AdministratorFixture.DEFAULT_PASSWORD);
    }
}
