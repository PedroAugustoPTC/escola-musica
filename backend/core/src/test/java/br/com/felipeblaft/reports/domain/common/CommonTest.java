package br.com.felipeblaft.reports.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommonTest {

    @Test
    void generatesUuidVersionSevenUniqueAndSortable() throws InterruptedException {
        Set<UUID> ids = new HashSet<>();
        UUID previous = GeneratorId.create();
        Thread.sleep(2);
        for (int i = 0; i < 1000; i++) {
            UUID id = GeneratorId.create();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            ids.add(id);
        }
        UUID later = GeneratorId.create();
        assertThat(ids).hasSize(1000);
        assertThat(
                        Long.compareUnsigned(
                                previous.getMostSignificantBits() >>> 16,
                                later.getMostSignificantBits() >>> 16))
                .isNegative();
    }

    @Test
    void domainExceptionCarriesCodeAndImmutableDetails() {
        DomainException error =
                new DomainException(ErrorCode.VERSAO_DESATUALIZADA, Map.of("versaoVigente", 3));
        assertThat(error.code()).isEqualTo(ErrorCode.VERSAO_DESATUALIZADA);
        assertThat(error.getMessage()).isEqualTo("VERSAO_DESATUALIZADA");
        assertThat(error.details()).containsEntry("versaoVigente", 3);
        assertThatThrownBy(() -> error.details().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new DomainException(ErrorCode.ERRO_INTERNO).details()).isEmpty();
    }

    @Test
    void codeMessageKey() {
        assertThat(ErrorCode.CODIGO_INVALIDO.messageKey()).isEqualTo("CODIGO_INVALIDO");
        assertThat(ErrorCode.RELATORIO_CANCELADO.messageKey())
                .isEqualTo("RELATORIO_CANCELADO_ERRO");
    }

    @Test
    void specDefaultParameters() {
        BusinessParameters defaults = BusinessParameters.defaults();
        assertThat(defaults.codeValidity()).isEqualTo(Duration.ofDays(7));
        assertThat(defaults.maxAccessAttempts()).isEqualTo(5);
        assertThat(defaults.lockDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.webSessionInactivity()).isEqualTo(Duration.ofHours(12));
        assertThat(defaults.pendingAudioExpiry()).isEqualTo(Duration.ofMinutes(30));
        assertThat(defaults.audioLimitMb()).isEqualTo(50);
    }

    @Test
    void invalidParametersAreRefused() {
        Duration d = Duration.ofMinutes(1);
        assertThatThrownBy(() -> new BusinessParameters(d, 0, d, d, d, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessParameters(d, 1, d, d, d, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessParameters(null, 1, d, d, d, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void channelEnumerations() {
        assertThat(Channel.values()).containsExactly(Channel.WEB, Channel.TELEGRAM);
        assertThat(Medium.valueOf("AUDIO")).isEqualTo(Medium.AUDIO);
        assertThat(ReportType.valueOf("SEMESTRAL")).isEqualTo(ReportType.SEMESTRAL);
    }
}
