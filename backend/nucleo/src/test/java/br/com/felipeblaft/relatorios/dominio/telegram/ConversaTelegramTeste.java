package br.com.felipeblaft.relatorios.dominio.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversaTelegramTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration TRINTA = Duration.ofMinutes(30);

    @Test
    void conversaVaziaComecaSemFluxo() {
        ConversaTelegram conversa = ConversaTelegram.vazia();
        assertThat(conversa.fluxo()).isEqualTo(FluxoConversa.NENHUM);
        assertThat(conversa.etapa()).isEqualTo(EtapaFluxoConversa.NENHUMA);
        assertThat(conversa.dados()).isEmpty();
        assertThat(conversa.audioPendente()).isEmpty();
        assertThat(conversa.relatorioFoco()).isEmpty();
        assertThat(conversa.alunoSelecionadoId()).isNull();
        assertThat(conversa.versaoApresentada()).isNull();
    }

    @Test
    void selecaoEFluxo() {
        ConversaTelegram conversa = ConversaTelegram.vazia();
        UUID aluno = UUID.randomUUID();
        conversa.selecionarAluno(aluno);
        conversa.iniciarFluxo(FluxoConversa.REGISTRO_AULA, EtapaFluxoConversa.ESCOLHENDO_ALUNO);
        conversa.guardar("opcoes", "a,b");
        assertThat(conversa.dado("opcoes")).contains("a,b");
        conversa.guardar("opcoes", null);
        assertThat(conversa.dado("opcoes")).isEmpty();
        conversa.guardar("x", "1");
        conversa.definirEtapa(EtapaFluxoConversa.AGUARDANDO_AUDIO);
        assertThat(conversa.etapa()).isEqualTo(EtapaFluxoConversa.AGUARDANDO_AUDIO);
        conversa.iniciarFluxo(FluxoConversa.SEMESTRAL, EtapaFluxoConversa.ESCOLHENDO_SEMESTRE);
        assertThat(conversa.dados()).isEmpty();
        assertThat(conversa.alunoSelecionadoId()).isEqualTo(aluno);
        conversa.limparSelecao();
        assertThat(conversa.alunoSelecionadoId()).isNull();
        assertThatThrownBy(() -> conversa.dados().put("y", "2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void confirmacaoPendente() {
        ConversaTelegram conversa = ConversaTelegram.vazia();
        conversa.pedirConfirmacao("aprovar:2");
        assertThat(conversa.confirmacaoPendente()).contains("aprovar:2");
        conversa.limparConfirmacao();
        assertThat(conversa.confirmacaoPendente()).isEmpty();
    }

    @Test
    void novoAudioSubstituiEExpiraEmTrintaMinutos() {
        ConversaTelegram conversa = ConversaTelegram.vazia();
        conversa.receberAudio(new AudioPendente("f1", "u1", "audio/ogg", 100, T0));
        conversa.receberAudio(new AudioPendente("f2", "u2", "audio/ogg", 200, T0.plusSeconds(60)));
        assertThat(conversa.audioPendente()).map(AudioPendente::fileId).contains("f2");

        assertThat(conversa.descartarAudioSeExpirado(T0.plus(TRINTA), TRINTA)).isFalse();
        assertThat(conversa.descartarAudioSeExpirado(T0.plusSeconds(60).plus(TRINTA), TRINTA))
                .isTrue();
        assertThat(conversa.audioPendente()).isEmpty();
        assertThat(conversa.descartarAudioSeExpirado(T0, TRINTA)).isFalse();

        conversa.receberAudio(new AudioPendente("f3", null, null, 1, T0));
        conversa.descartarAudio();
        assertThat(conversa.audioPendente()).isEmpty();
    }

    @Test
    void focoEVersaoApresentada() {
        ConversaTelegram conversa = ConversaTelegram.vazia();
        RelatorioFoco foco = new RelatorioFoco(TipoRelatorio.AULA, UUID.randomUUID());
        conversa.focar(foco, 1);
        conversa.registrarVersaoApresentada(2);
        assertThat(conversa.relatorioFoco()).contains(foco);
        assertThat(conversa.versaoApresentada()).isEqualTo(2);
        conversa.limparFoco();
        assertThat(conversa.relatorioFoco()).isEmpty();
        assertThat(conversa.versaoApresentada()).isNull();
    }

    @Test
    void limparTudoVoltaAoInicio() {
        ConversaTelegram conversa =
                new ConversaTelegram(
                        UUID.randomUUID(),
                        FluxoConversa.HISTORICO,
                        EtapaFluxoConversa.ESCOLHENDO_ITEM_HISTORICO,
                        Map.of("pagina", "2"),
                        new AudioPendente("f", "u", "audio/ogg", 1, T0),
                        new RelatorioFoco(TipoRelatorio.SEMESTRAL, UUID.randomUUID()),
                        3);
        conversa.limparTudo();
        assertThat(conversa.fluxo()).isEqualTo(FluxoConversa.NENHUM);
        assertThat(conversa.dados()).isEmpty();
        assertThat(conversa.audioPendente()).isEmpty();
        assertThat(conversa.relatorioFoco()).isEmpty();
        assertThat(conversa.alunoSelecionadoId()).isNull();
    }
}
