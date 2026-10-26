package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CallbackDataCodecTest {

    @Test
    void maximumArgumentSizeLeavesRoomForVersionAndAction() {
        // "v1:" + "reg-confirm" + ":" ocupam 15 bytes dos 64
        assertThat(CallbackDataCodec.maxArgumentBytes("reg-confirm")).isEqualTo(49);
        assertThat(CallbackDataCodec.maxArgumentBytes("home")).isEqualTo(64 - "v1:home:".length());
    }

    @Test
    void anArgumentOfTheMaximumSizeEncodesAndNeverExceeds64Bytes() {
        String argument = "A".repeat(CallbackDataCodec.maxArgumentBytes("reg-confirm"));

        assertThat(CallbackDataCodec.fits("reg-confirm", argument)).isTrue();
        String data = CallbackDataCodec.encode("reg-confirm", argument);

        assertThat(data.getBytes(StandardCharsets.UTF_8)).hasSize(CallbackDataCodec.LIMIT_BYTES);
        assertThat(CallbackDataCodec.decode(data))
                .hasValueSatisfying(decoded -> assertThat(decoded.argument()).isEqualTo(argument));
    }

    @Test
    void anArgumentAboveTheLimitDoesNotFitAndEncodingItIsRefused() {
        String argument = "A".repeat(CallbackDataCodec.maxArgumentBytes("reg-confirm") + 1);

        assertThat(CallbackDataCodec.fits("reg-confirm", argument)).isFalse();
        assertThatThrownBy(() -> CallbackDataCodec.encode("reg-confirm", argument))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiByteCharactersCountInBytesNotInCharacters() {
        String argument = "é".repeat(25);

        assertThat(argument).hasSize(25);
        assertThat(CallbackDataCodec.fits("reg-confirm", argument)).isFalse();
    }

    @Test
    void nullAndEmptyArgumentsAlwaysFit() {
        assertThat(CallbackDataCodec.fits("reg-confirm", null)).isTrue();
        assertThat(CallbackDataCodec.fits("reg-confirm", "")).isTrue();
    }
}
