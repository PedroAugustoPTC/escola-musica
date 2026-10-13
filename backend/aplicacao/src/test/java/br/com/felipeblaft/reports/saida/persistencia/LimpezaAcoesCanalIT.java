package br.com.felipeblaft.reports.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.saida.fila.LimpezaAcoesCanalTarefa;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
import br.com.felipeblaft.reports.usecase.channel.CleanChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * R-11, FR-029: o conteúdo some depois de 24 h, o registro depois de 7 dias, o documento ao vencer.
 */
class LimpezaAcoesCanalIT extends IntegracaoBase {

    @Autowired SaveChannelActionsUseCase gravar;
    @Autowired ChannelActionRepository acoes;
    @Autowired ChannelDocumentRepository documentos;
    @Autowired CleanChannelActionsUseCase limpar;
    @Autowired LimpezaAcoesCanalTarefa tarefa;

    private void acaoFinalizada(String id) {
        gravar.run(
                "p:" + id,
                ChannelActionOrigin.PROATIVA,
                null,
                List.of(
                        new NewAction(
                                id,
                                ChannelActionType.MENSAGEM,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"texto\":\"conteúdo sigiloso\"}",
                                "{\"texto\":\"alternativa sigilosa\"}")));
        ChannelAction acao = acoes.searchById(id).orElseThrow();
        acao.confirmExecuted(relogio.now());
        assertThat(acoes.updateIfVersion(acao, acao.version())).isTrue();
    }

    @Test
    void conteudoZeradoApos24HERegistroRemovidoApos7DiasEDocumentoVencidoRemovido() {
        acaoFinalizada("p:a:0");
        gravar.run(
                "p:b",
                ChannelActionOrigin.PROATIVA,
                null,
                List.of(
                        new NewAction(
                                "p:b:0",
                                ChannelActionType.MENSAGEM,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"texto\":\"ainda pendente\"}",
                                null)));
        documentos.emit(
                new ChannelDocument(
                        "a".repeat(64),
                        UUID.randomUUID(),
                        ReportType.AULA,
                        UUID.randomUUID(),
                        null,
                        "x.pdf",
                        relogio.now(),
                        relogio.now().plus(Duration.ofHours(25))));

        // antes das 24 h nada muda
        relogio.avancar(Duration.ofHours(23));
        limpar.run();
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
        limpar.run();

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
        gravar.run(
                loteId,
                ChannelActionOrigin.RESPOSTA_UPDATE,
                900L,
                List.of(
                        new NewAction(
                                "u900-e0",
                                ChannelActionType.MENSAGEM,
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
        assertThat(acoes.searchByUpdate(900L)).hasSize(1);

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
        CleanChannelActionsUseCase.Result resultado = limpar.run();
        assertThat(resultado).isEqualTo(new CleanChannelActionsUseCase.Result(0, 0, 0, 0));
    }
}
