package br.com.felipeblaft.relatorios.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.casodeuso.canal.LimparAcoesCanalCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import br.com.felipeblaft.relatorios.saida.fila.LimpezaAcoesCanalTarefa;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * R-11, FR-029: o conteúdo some depois de 24 h, o registro depois de 7 dias, o documento ao vencer.
 */
class LimpezaAcoesCanalIT extends IntegracaoBase {

    @Autowired GravarAcoesCanalCasoDeUso gravar;
    @Autowired AcaoCanalRepositorio acoes;
    @Autowired DocumentoCanalRepositorio documentos;
    @Autowired LimparAcoesCanalCasoDeUso limpar;
    @Autowired LimpezaAcoesCanalTarefa tarefa;

    private void acaoFinalizada(String id) {
        gravar.executar(
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
                                "{\"texto\":\"conteúdo sigiloso\"}",
                                "{\"texto\":\"alternativa sigilosa\"}")));
        AcaoCanal acao = acoes.buscarPorId(id).orElseThrow();
        acao.confirmarExecutada(relogio.agora());
        assertThat(acoes.atualizarSeVersao(acao, acao.versao())).isTrue();
    }

    @Test
    void conteudoZeradoApos24HERegistroRemovidoApos7DiasEDocumentoVencidoRemovido() {
        acaoFinalizada("p:a:0");
        gravar.executar(
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
                                "{\"texto\":\"ainda pendente\"}",
                                null)));
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

        // antes das 24 h nada muda
        relogio.avancar(Duration.ofHours(23));
        limpar.executar();
        assertThat(
                        jdbc.queryForObject(
                                "select payload is not null from acao_canal where id = 'p:a:0'",
                                Boolean.class))
                .isTrue();

        relogio.avancar(Duration.ofHours(2));
        tarefa.limpar();

        assertThat(
                        jdbc.queryForObject(
                                "select payload is null and compensacao is null from acao_canal where id = 'p:a:0'",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select estado from acao_canal where id = 'p:a:0'", String.class))
                .isEqualTo("EXECUTADA");
        // a pendente não é tocada; o documento venceu (25 h)
        assertThat(
                        jdbc.queryForObject(
                                "select payload is not null from acao_canal where id = 'p:b:0'",
                                Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from documento_canal", Integer.class))
                .isZero();

        relogio.avancar(Duration.ofDays(7));
        limpar.executar();

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where id = 'p:a:0'",
                                Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where id = 'p:b:0'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void entregueNuncaConfirmadaExpiraNoPrazoEOConteudoSomeNaRetencao() {
        String loteId = "u900";
        gravar.executar(
                loteId,
                OrigemAcaoCanal.RESPOSTA_UPDATE,
                900L,
                List.of(
                        new NovaAcao(
                                "u900-e0",
                                TipoAcaoCanal.MENSAGEM,
                                1L,
                                null,
                                null,
                                "{\"texto\":\"Matteo foi bem\"}",
                                "{\"texto\":\"alternativa sigilosa\"}")));

        // dentro do prazo continua entregue e reentregável pelo update repetido
        relogio.avancar(Duration.ofHours(23));
        tarefa.limpar();
        assertThat(
                        jdbc.queryForObject(
                                "select estado from acao_canal where id = 'u900-e0'", String.class))
                .isEqualTo("ENTREGUE");
        assertThat(acoes.buscarPorUpdate(900L)).hasSize(1);

        // vencido o prazo: estado final SEM_CONFIRMACAO, ainda com o conteúdo (retenção de 24 h)
        relogio.avancar(Duration.ofHours(1));
        tarefa.limpar();
        assertThat(
                        jdbc.queryForObject(
                                "select estado || '/' || codigo_motivo from acao_canal where id = 'u900-e0'",
                                String.class))
                .isEqualTo("FALHA_DEFINITIVA/SEM_CONFIRMACAO");
        assertThat(
                        jdbc.queryForObject(
                                "select payload is not null from acao_canal where id = 'u900-e0'",
                                Boolean.class))
                .isTrue();

        // depois da retenção do conteúdo: payload e compensação zerados
        relogio.avancar(Duration.ofHours(25));
        tarefa.limpar();
        assertThat(
                        jdbc.queryForObject(
                                "select payload is null and compensacao is null from acao_canal where id = 'u900-e0'",
                                Boolean.class))
                .isTrue();

        // depois da retenção do registro: a linha some
        relogio.avancar(Duration.ofDays(7));
        tarefa.limpar();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from acao_canal where id = 'u900-e0'",
                                Integer.class))
                .isZero();
    }

    @Test
    void semNadaParaLimparNaoFalha() {
        LimparAcoesCanalCasoDeUso.Resultado resultado = limpar.executar();
        assertThat(resultado).isEqualTo(new LimparAcoesCanalCasoDeUso.Resultado(0, 0, 0, 0));
    }
}
