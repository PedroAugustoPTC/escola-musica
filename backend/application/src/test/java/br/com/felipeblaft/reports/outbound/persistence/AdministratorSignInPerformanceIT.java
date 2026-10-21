package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Meta de desempenho da entrada do administrador (plano.md, Performance Goals): 50 entradas
 * sequenciais com os parâmetros padrão do Argon2id e o PostgreSQL real; p95 ≤ 500 ms. Compõe as
 * portas reais da entrada (busca por e-mail, verificação da senha, token de sessão e gravação da
 * sessão), sem o caso de uso, que é do núcleo e chega na integração da S5.
 */
class AdministratorSignInPerformanceIT extends IntegrationBase {

    private static final int SIGN_INS = 50;
    private static final long P95_LIMIT_MILLIS = 500;

    @Autowired AdministratorRepository administrators;
    @Autowired AdministratorSessionRepository sessions;
    @Autowired PasswordHashPort hasher;
    @Autowired DataProtectionPort protection;

    @Test
    void p95OfFiftySequentialSignInsIsAtMost500Milliseconds() {
        Instant now = clock.now();
        AdministratorFixture fixture = AdministratorFixture.insertActive(administrators, hasher, now);
        // o hash padrão precisa estar com os parâmetros padrão (memória 19 MiB, 2 iterações)
        assertThat(administrators.searchByEmail(fixture.email()).orElseThrow().passwordHash())
                .contains("m=19456,t=2,p=1");

        signInOnce(fixture, now); // aquecimento: carga de classes, pool e plano de consulta
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < SIGN_INS; i++) {
            long start = System.nanoTime();
            signInOnce(fixture, now);
            millis.add((System.nanoTime() - start) / 1_000_000);
        }

        Collections.sort(millis);
        long p95 = millis.get((int) Math.ceil(SIGN_INS * 0.95) - 1);
        assertThat(p95)
                .as("p95 das %d entradas (ms), ordenadas: %s", SIGN_INS, millis)
                .isLessThanOrEqualTo(P95_LIMIT_MILLIS);
        assertThat(jdbc.queryForObject("select count(*) from administrator_session", Integer.class))
                .isEqualTo(SIGN_INS + 1);
    }

    private void signInOnce(AdministratorFixture fixture, Instant now) {
        Administrator account = administrators.searchByEmail(fixture.email()).orElseThrow();
        boolean matches = hasher.matches(new Secret(AdministratorFixture.DEFAULT_PASSWORD), account.passwordHash());
        assertThat(matches).isTrue();
        String token = protection.generateSessionToken();
        sessions.save(
                new AdministratorSession(
                        GeneratorId.create(),
                        account.id(),
                        protection.sessionTokenHash(token),
                        now,
                        now.plus(Duration.ofHours(2)),
                        null,
                        null));
    }
}
