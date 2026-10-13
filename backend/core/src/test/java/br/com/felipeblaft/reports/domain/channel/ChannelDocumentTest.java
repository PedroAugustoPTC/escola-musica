package br.com.felipeblaft.reports.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelDocumentTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private ChannelDocument document(Integer version) {
        return new ChannelDocument(
                "a".repeat(64),
                UUID.randomUUID(),
                ReportType.AULA,
                UUID.randomUUID(),
                version,
                "relatorio.pdf",
                NOW,
                NOW.plusSeconds(3600));
    }

    @Test
    void validOnlyUntilExpiry() {
        ChannelDocument document = document(null);
        assertThat(document.valid(NOW)).isTrue();
        assertThat(document.valid(NOW.plusSeconds(3599))).isTrue();
        assertThat(document.valid(NOW.plusSeconds(3600))).isFalse();
    }

    @Test
    void nullVersionMeansOfficialPdf() {
        assertThat(document(null).official()).isTrue();
        assertThat(document(2).official()).isFalse();
    }

    @Test
    void nonPositiveVersionIsRefused() {
        assertThatThrownBy(() -> document(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredFields() {
        assertThatThrownBy(
                        () ->
                                new ChannelDocument(
                                        null,
                                        UUID.randomUUID(),
                                        ReportType.AULA,
                                        UUID.randomUUID(),
                                        null,
                                        "x.pdf",
                                        NOW,
                                        NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
