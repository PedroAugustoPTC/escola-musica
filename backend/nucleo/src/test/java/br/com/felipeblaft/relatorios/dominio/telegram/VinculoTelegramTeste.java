package br.com.felipeblaft.relatorios.dominio.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VinculoTelegramTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    @Test
    void novoVinculoComConversaVazia() {
        UUID professor = UUID.randomUUID();
        VinculoTelegram vinculo = VinculoTelegram.novo(professor, 111L, 222L, T0);
        assertThat(vinculo.professorId()).isEqualTo(professor);
        assertThat(vinculo.telegramUsuarioId()).isEqualTo(111L);
        assertThat(vinculo.chatId()).isEqualTo(222L);
        assertThat(vinculo.vinculadoEm()).isEqualTo(T0);
        assertThat(vinculo.conversa().fluxo()).isEqualTo(FluxoConversa.NENHUM);
        assertThat(vinculo.versao()).isZero();
        assertThat(vinculo.id()).isNotNull();

        vinculo.atualizarChat(333L, T0.plusSeconds(1));
        vinculo.tocar(T0.plusSeconds(2));
        assertThat(vinculo.chatId()).isEqualTo(333L);
        assertThat(vinculo.atualizadoEm()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void conversaNulaViraVazia() {
        VinculoTelegram vinculo =
                new VinculoTelegram(UUID.randomUUID(), UUID.randomUUID(), 1, 1, T0, null, T0, 4);
        assertThat(vinculo.conversa()).isNotNull();
        assertThat(vinculo.versao()).isEqualTo(4);
        vinculo.registrarVersaoPersistida(5);
        assertThat(vinculo.versao()).isEqualTo(5);
    }

    @Test
    void audioPendenteExigeReferencia() {
        assertThatThrownBy(() -> new AudioPendente(null, null, null, 0, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RelatorioFoco(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
    }
}
