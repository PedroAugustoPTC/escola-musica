package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelDocumentRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmitChannelDocumentUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryChannelDocumentRepository repository =
            new InMemoryChannelDocumentRepository();
    private final EmitChannelDocumentUseCase emit =
            new EmitChannelDocumentUseCase(
                    repository,
                    clock,
                    new RetryPolicy(
                            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)));

    @Test
    void tokenHas43Base64urlCharactersAndSavesOnlyHash() {
        UUID teacher = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        String token = emit.run(teacher, ReportType.LESSON, report, 2, "r-v2.pdf");

        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(repository.data).doesNotContainKey(token);
        ChannelDocument saved =
                repository.searchByHash(ChannelDocument.hashDoToken(token)).orElseThrow();
        assertThat(saved.tokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(saved.teacherId()).isEqualTo(teacher);
        assertThat(saved.reportId()).isEqualTo(report);
        assertThat(saved.reportType()).isEqualTo(ReportType.LESSON);
        assertThat(saved.version()).isEqualTo(2);
        assertThat(saved.fileName()).isEqualTo("r-v2.pdf");
    }

    @Test
    void validityAndDeliveryDeadlinePlusOneHour() {
        String token =
                emit.run(UUID.randomUUID(), ReportType.SEMESTER, UUID.randomUUID(), null, "s.pdf");
        ChannelDocument saved =
                repository.searchByHash(ChannelDocument.hashDoToken(token)).orElseThrow();
        assertThat(saved.official()).isTrue();
        assertThat(saved.expiresAt()).isEqualTo(clock.now().plus(Duration.ofHours(25)));
        assertThat(saved.valid(clock.now().plus(Duration.ofHours(24)))).isTrue();
        assertThat(saved.valid(clock.now().plus(Duration.ofHours(25)))).isFalse();
    }

    @Test
    void tokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(
                    emit.run(
                            UUID.randomUUID(),
                            ReportType.LESSON,
                            UUID.randomUUID(),
                            null,
                            "x.pdf"));
        }
        assertThat(tokens).hasSize(200);
    }

    @Test
    void hashDoTokenEHexSha256() {
        assertThat(ChannelDocument.hashDoToken("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
