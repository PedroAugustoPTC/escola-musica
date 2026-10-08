package br.com.felipeblaft.relatorios.dominio.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentoCanalTeste {

    private static final Instant AGORA = Instant.parse("2026-10-02T12:00:00Z");

    private DocumentoCanal documento(Integer versao) {
        return new DocumentoCanal(
                "a".repeat(64),
                UUID.randomUUID(),
                TipoRelatorio.AULA,
                UUID.randomUUID(),
                versao,
                "relatorio.pdf",
                AGORA,
                AGORA.plusSeconds(3600));
    }

    @Test
    void validoSoAteAExpiracao() {
        DocumentoCanal documento = documento(null);
        assertThat(documento.valido(AGORA)).isTrue();
        assertThat(documento.valido(AGORA.plusSeconds(3599))).isTrue();
        assertThat(documento.valido(AGORA.plusSeconds(3600))).isFalse();
    }

    @Test
    void versaoNulaESignificaPdfOficial() {
        assertThat(documento(null).oficial()).isTrue();
        assertThat(documento(2).oficial()).isFalse();
    }

    @Test
    void versaoNaoPositivaERecusada() {
        assertThatThrownBy(() -> documento(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void camposObrigatorios() {
        assertThatThrownBy(
                        () ->
                                new DocumentoCanal(
                                        null,
                                        UUID.randomUUID(),
                                        TipoRelatorio.AULA,
                                        UUID.randomUUID(),
                                        null,
                                        "x.pdf",
                                        AGORA,
                                        AGORA))
                .isInstanceOf(NullPointerException.class);
    }
}
