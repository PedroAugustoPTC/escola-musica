package br.com.felipeblaft.relatorios.dominio.trabalho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrabalhoProcessamentoTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    private static TrabalhoProcessamento novo() {
        return TrabalhoProcessamento.novo(
                TipoTrabalho.REGISTRO_AULA,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                Canal.WEB,
                Meio.AUDIO,
                EtapaTrabalho.ENVIANDO,
                T0);
    }

    @Test
    void ciclo() {
        TrabalhoProcessamento trabalho = novo();
        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.PENDENTE);
        trabalho.iniciar(T0.plusSeconds(1));
        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.EM_EXECUCAO);
        assertThat(trabalho.tentativas()).isEqualTo(1);
        assertThatThrownBy(() -> trabalho.iniciar(T0)).isInstanceOf(IllegalStateException.class);
        trabalho.avancar(EtapaTrabalho.TRANSCREVENDO);
        UUID relatorio = UUID.randomUUID();
        trabalho.vincularRelatorio(TipoRelatorio.AULA, relatorio);
        assertThat(trabalho.relatorioResultanteId()).isEqualTo(relatorio);
        trabalho.concluir(ResultadoTrabalho.PRONTO_PARA_CONFERENCIA, T0.plusSeconds(31));
        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.CONCLUIDO);
        assertThat(trabalho.etapa()).isEqualTo(EtapaTrabalho.PRONTO);
        assertThat(trabalho.duracaoMs()).isEqualTo(30_000);
        assertThat(trabalho.descartar(T0)).isFalse();
        assertThat(trabalho.toString()).contains("REGISTRO_AULA");
    }

    @Test
    void falhaEDescarte() {
        TrabalhoProcessamento falhou = novo();
        falhou.iniciar(T0);
        falhou.concluir(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL, T0);
        assertThat(falhou.status()).isEqualTo(StatusTrabalho.FALHOU);
        assertThat(falhou.etapa()).isEqualTo(EtapaTrabalho.ENVIANDO);

        TrabalhoProcessamento descartado = novo();
        assertThat(descartado.descartar(T0)).isTrue();
        assertThat(descartado.status()).isEqualTo(StatusTrabalho.DESCARTADO);
        assertThat(descartado.resultado()).isEqualTo(ResultadoTrabalho.DESCARTADO);
        assertThat(descartado.duracaoMs()).isZero();

        TrabalhoProcessamento concluidoDescartado = novo();
        concluidoDescartado.concluir(ResultadoTrabalho.DESCARTADO, T0);
        assertThat(concluidoDescartado.status()).isEqualTo(StatusTrabalho.DESCARTADO);
    }

    @Test
    void demoraAosTresMinutosEAviso() {
        TrabalhoProcessamento trabalho = novo();
        assertThat(trabalho.demorando(T0.plus(Duration.ofSeconds(179)))).isFalse();
        assertThat(trabalho.demorando(T0.plus(Duration.ofMinutes(3)))).isTrue();
        trabalho.marcarAvisoDemora();
        assertThat(trabalho.avisoDemoraEnviado()).isTrue();
        trabalho.concluir(ResultadoTrabalho.NOVA_VERSAO, T0);
        assertThat(trabalho.demorando(T0.plus(Duration.ofMinutes(5)))).isFalse();
    }

    @Test
    void parametrosEArquivoEDevolucaoParaFila() {
        TrabalhoProcessamento trabalho = novo();
        trabalho.definirParametro(TrabalhoProcessamento.TEXTO, "a");
        trabalho.definirParametro(TrabalhoProcessamento.TEXTO, null);
        assertThat(trabalho.parametro(TrabalhoProcessamento.TEXTO)).isEmpty();
        trabalho.definirParametro(TrabalhoProcessamento.VERSAO_BASE, "2");
        assertThat(trabalho.parametros()).containsEntry("versaoBase", "2");
        trabalho.definirArquivoTemporario("/tmp/audios/a.ogg");
        assertThat(trabalho.arquivoTemporario()).isEqualTo("/tmp/audios/a.ogg");
        trabalho.devolverParaFila();
        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.PENDENTE);
        trabalho.iniciar(T0);
        trabalho.devolverParaFila();
        assertThat(trabalho.status()).isEqualTo(StatusTrabalho.PENDENTE);
        trabalho.iniciar(T0.plusSeconds(10));
        assertThat(trabalho.tentativas()).isEqualTo(2);
        assertThat(trabalho.iniciadoEm()).isEqualTo(T0);
        assertThat(ResultadoTrabalho.NOVA_VERSAO.sucesso()).isTrue();
        assertThat(StatusTrabalho.FALHOU.emCurso()).isFalse();
    }
}
