package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dubles.AcaoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DocumentoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimparAcoesCanalCasoDeUsoTeste {

    private final RelogioFixo relogio = new RelogioFixo();
    private final AcaoCanalRepositorioEmMemoria acoes = new AcaoCanalRepositorioEmMemoria();
    private final DocumentoCanalRepositorioEmMemoria documentos =
            new DocumentoCanalRepositorioEmMemoria();
    private final LimparAcoesCanalCasoDeUso limpar =
            new LimparAcoesCanalCasoDeUso(
                    acoes, documentos, relogio, Duration.ofHours(24), Duration.ofDays(7));

    private void acaoFinalizada(String id) {
        new GravarAcoesCanalCasoDeUso(
                        acoes,
                        relogio,
                        new PoliticaRetentativa(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofHours(24)))
                .executar(
                        "p:" + id,
                        OrigemAcaoCanal.PROATIVA,
                        null,
                        List.of(
                                new NovaAcao(
                                        id,
                                        TipoAcaoCanal.MENSAGEM,
                                        1L,
                                        UUID.randomUUID(),
                                        null,
                                        "{\"texto\":\"segredo\"}",
                                        "{\"texto\":\"alt\"}")));
        acoes.guardada(id).confirmarExecutada(relogio.agora());
    }

    @Test
    void zeraOConteudoDepoisDe24HEApagaORegistroDepoisDe7Dias() {
        acaoFinalizada("p:a:0");
        limpar.executar();
        assertThat(acoes.guardada("p:a:0").payload()).isNotNull();

        relogio.avancar(Duration.ofHours(24).plusSeconds(1));
        LimparAcoesCanalCasoDeUso.Resultado primeiro = limpar.executar();
        assertThat(primeiro.payloadsZerados()).isEqualTo(1);
        assertThat(acoes.guardada("p:a:0").payload()).isNull();
        assertThat(acoes.guardada("p:a:0").compensacao()).isNull();
        assertThat(acoes.guardada("p:a:0")).isNotNull();

        relogio.avancar(Duration.ofDays(7));
        LimparAcoesCanalCasoDeUso.Resultado segundo = limpar.executar();
        assertThat(segundo.registrosRemovidos()).isEqualTo(1);
        assertThat(acoes.guardada("p:a:0")).isNull();
    }

    @Test
    void naoMexeEmAcaoNaoFinalizada() {
        acaoFinalizada("p:a:0");
        new GravarAcoesCanalCasoDeUso(
                        acoes,
                        relogio,
                        new PoliticaRetentativa(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofDays(30)))
                .executar(
                        "p:b",
                        OrigemAcaoCanal.PROATIVA,
                        null,
                        List.of(
                                new NovaAcao(
                                        "p:b:0",
                                        TipoAcaoCanal.MENSAGEM,
                                        1L,
                                        UUID.randomUUID(),
                                        null,
                                        "{}",
                                        null)));
        relogio.avancar(Duration.ofDays(10));
        limpar.executar();
        assertThat(acoes.guardada("p:b:0")).isNotNull();
        assertThat(acoes.guardada("p:b:0").payload()).isNotNull();
    }

    @Test
    void removeDocumentosVencidos() {
        documentos.emitir(
                new DocumentoCanal(
                        "a".repeat(64),
                        UUID.randomUUID(),
                        TipoRelatorio.AULA,
                        UUID.randomUUID(),
                        null,
                        "x.pdf",
                        relogio.agora(),
                        relogio.agora().plus(Duration.ofHours(25))));
        assertThat(limpar.executar().documentosRemovidos()).isZero();
        relogio.avancar(Duration.ofHours(26));
        assertThat(limpar.executar().documentosRemovidos()).isEqualTo(1);
        assertThat(documentos.dados).isEmpty();
    }

    @Test
    void semNadaParaLimparDevolveZeros() {
        assertThat(limpar.executar()).isEqualTo(new LimparAcoesCanalCasoDeUso.Resultado(0, 0, 0));
    }
}
