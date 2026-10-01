package br.com.felipeblaft.relatorios.casodeuso.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.SituacaoDocumento;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Responder, alterar, tentar de novo, aprovar, cancelar, obter relatório, consultar trabalho e
 * obter documento.
 */
class CasosDeUsoRelatorioAulaTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    private static void falhaCom(ThrowingCallable acao, CodigoErro codigo) {
        assertThatThrownBy(acao)
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(codigo);
    }

    @Test
    void responderExigeTextoEEtapaCerta() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        falhaCom(
                () -> c.responder.responderTexto(c.clara.id(), relatorio.id(), "  ", Canal.WEB),
                CodigoErro.RESPOSTA_VAZIA);
        falhaCom(
                () -> c.responder.responderTexto(c.bruno.id(), relatorio.id(), "x", Canal.WEB),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
        c.responder.responderTexto(c.clara.id(), relatorio.id(), "resposta", Canal.WEB);
        assertThat(relatorio.etapa()).isEqualTo(EtapaRelatorio.INCORPORANDO_RESPOSTAS);
        falhaCom(
                () ->
                        c.responder.responderTexto(
                                c.clara.id(), relatorio.id(), "de novo", Canal.WEB),
                CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        falhaCom(
                () ->
                        c.responder.responderAudio(
                                c.clara.id(),
                                relatorio.id(),
                                CenarioRelatorio.arquivo("r.ogg", 1),
                                Canal.WEB),
                CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        assertThat(c.normalizacao.apagados).contains(Path.of("/tmp/audios/r.ogg"));
    }

    @Test
    void responderAudioAcimaDoLimite() {
        RelatorioAula relatorio = c.relatorioComPerguntas();
        falhaCom(
                () ->
                        c.responder.responderAudio(
                                c.clara.id(),
                                relatorio.id(),
                                new ReferenciaArquivoCanal(
                                        "f", null, null, c.parametros.limiteAudioBytes() + 1),
                                Canal.TELEGRAM),
                CodigoErro.AUDIO_MUITO_GRANDE);
        assertThat(relatorio.etapa()).isNull();
    }

    @Test
    void alterarValidaVersaoTextoEAcessoCruzado() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        falhaCom(
                () -> c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), " ", 1, Canal.WEB),
                CodigoErro.ALTERACAO_VAZIA);
        assertThatThrownBy(
                        () ->
                                c.alterar.alterarPorTexto(
                                        c.clara.id(), relatorio.id(), "x", 3, Canal.WEB))
                .isInstanceOf(ExcecaoDominio.class)
                .satisfies(
                        e ->
                                assertThat(((ExcecaoDominio) e).detalhes())
                                        .containsEntry("versaoVigente", 1));
        falhaCom(
                () -> c.alterar.alterarPorTexto(c.bruno.id(), relatorio.id(), "x", 1, Canal.WEB),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
        falhaCom(
                () ->
                        c.alterar.alterarPorAudio(
                                c.clara.id(),
                                relatorio.id(),
                                CenarioRelatorio.arquivo("a.ogg", 1),
                                2,
                                Canal.WEB),
                CodigoErro.VERSAO_DESATUALIZADA);
        assertThat(c.normalizacao.apagados).contains(Path.of("/tmp/audios/a.ogg"));
        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorAudio(
                        c.clara.id(),
                        relatorio.id(),
                        CenarioRelatorio.arquivo("b.ogg", 1),
                        1,
                        Canal.WEB);
        assertThat(trabalho.parametros()).containsEntry("versaoBase", "1");
        assertThat(relatorio.etapa()).isEqualTo(EtapaRelatorio.TRANSCREVENDO_PEDIDO);
    }

    @Test
    void aprovarGeraPdfOficialEAprovacaoDuplaERecusada() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        c.relogio.avancar(Duration.ofMinutes(5));

        AprovarRelatorioAulaCasoDeUso.Aprovacao aprovacao =
                c.aprovar.executar(c.clara.id(), relatorio.id(), 1, Canal.TELEGRAM);

        assertThat(aprovacao.versaoAprovada()).isEqualTo(1);
        assertThat(aprovacao.aprovadoEm()).isEqualTo(c.relogio.agora());
        assertThat(relatorio.canalAprovacao()).isEqualTo(Canal.TELEGRAM);
        assertThat(c.pdf.modelos.getLast().situacao()).isEqualTo(SituacaoDocumento.OFICIAL);
        assertThat(c.pdf.modelos.getLast().aprovacao().professorNome()).isEqualTo("Clara Souza");
        assertThat(c.armazenamento.arquivos)
                .containsKey("relatorios/aula/" + relatorio.id() + "/oficial.pdf");
        falhaCom(
                () -> c.aprovar.executar(c.clara.id(), relatorio.id(), 1, Canal.WEB),
                CodigoErro.RELATORIO_JA_APROVADO);
        falhaCom(
                () -> c.cancelar.cancelar(c.clara.id(), relatorio.id()),
                CodigoErro.RELATORIO_JA_APROVADO);
    }

    @Test
    void aprovarVersaoDesatualizadaOuDeOutroProfessor() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        falhaCom(
                () -> c.aprovar.executar(c.clara.id(), relatorio.id(), 2, Canal.WEB),
                CodigoErro.VERSAO_DESATUALIZADA);
        falhaCom(
                () -> c.aprovar.executar(c.bruno.id(), relatorio.id(), 1, Canal.WEB),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
        falhaCom(
                () -> c.aprovar.executar(c.clara.id(), UUID.randomUUID(), 1, Canal.WEB),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
    }

    @Test
    void cancelarDescartaOTrabalhoEmCurso() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "mudar", 1, Canal.WEB);
        relatorio.concluirEtapa(c.relogio.agora());

        c.cancelar.cancelar(c.clara.id(), relatorio.id());

        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.CANCELADO);
        assertThat(c.fila.buscarPorId(trabalho.id()).orElseThrow().status())
                .isEqualTo(StatusTrabalho.DESCARTADO);
        falhaCom(
                () -> c.cancelar.cancelar(c.clara.id(), relatorio.id()),
                CodigoErro.RELATORIO_CANCELADO);
    }

    @Test
    void tentarDeNovoSoEmFalha() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        falhaCom(
                () -> c.tentar.executar(c.clara.id(), relatorio.id(), Canal.WEB),
                CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
    }

    @Test
    void obterRelatorioComInteracaoTrabalhoEPdf() {
        RelatorioAula comPerguntas = c.relatorioComPerguntasDeOutroAluno();
        RelatorioAula relatorio = c.relatorioEmConferencia();
        ObterRelatorioAulaCasoDeUso.VisaoRelatorioAula visao =
                c.obter.executar(c.clara.id(), relatorio.id());
        assertThat(visao.alunoNome()).isEqualTo("Matteo Mafra");
        assertThat(visao.professorNome()).isEqualTo("Clara Souza");
        assertThat(visao.pdfDisponivel()).isTrue();
        assertThat(visao.ultimaInteracao()).isEmpty();
        assertThat(visao.trabalhoEmCurso()).isEmpty();

        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorTexto(c.clara.id(), relatorio.id(), "x", 1, Canal.WEB);
        assertThat(c.obter.executar(c.clara.id(), relatorio.id()).trabalhoEmCurso())
                .contains(trabalho);
        falhaCom(
                () -> c.obter.executar(c.bruno.id(), relatorio.id()),
                CodigoErro.RECURSO_NAO_ENCONTRADO);

        assertThat(c.obter.executar(c.clara.id(), comPerguntas.id()).pdfDisponivel()).isFalse();
    }

    @Test
    void consultarTrabalhoMostraDemoraETextoSoParaODono() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        TrabalhoProcessamento trabalho =
                c.alterar.alterarPorTexto(
                        c.clara.id(), relatorio.id(), "texto digitado", 1, Canal.WEB);
        ConsultarTrabalhoCasoDeUso.VisaoTrabalho visao =
                c.consultarTrabalho.executar(c.clara.id(), trabalho.id());
        assertThat(visao.demorando()).isFalse();
        assertThat(visao.textoEntendido()).contains("texto digitado");
        c.relogio.avancar(Duration.ofMinutes(3));
        assertThat(c.consultarTrabalho.executar(c.clara.id(), trabalho.id()).demorando()).isTrue();
        falhaCom(
                () -> c.consultarTrabalho.executar(c.bruno.id(), trabalho.id()),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
    }

    @Test
    void documentosDeConferenciaEOficial() {
        RelatorioAula relatorio = c.relatorioEmConferencia();
        ObterDocumentoRelatorioCasoDeUso.DocumentoObtido conferencia =
                c.obterDocumento.conferencia(c.clara.id(), TipoRelatorio.AULA, relatorio.id(), 1);
        assertThat(new String(conferencia.conteudo(), StandardCharsets.UTF_8))
                .isEqualTo("PDF CONFERENCIA v1");
        assertThat(conferencia.nomeArquivo())
                .isEqualTo("relatorio-aula-2026-09-21-v1.pdf")
                .doesNotContain(relatorio.id().toString());

        falhaCom(
                () -> c.obterDocumento.oficial(c.clara.id(), TipoRelatorio.AULA, relatorio.id()),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
        falhaCom(
                () ->
                        c.obterDocumento.conferencia(
                                c.clara.id(), TipoRelatorio.AULA, relatorio.id(), 2),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
        falhaCom(
                () ->
                        c.obterDocumento.conferencia(
                                c.bruno.id(), TipoRelatorio.AULA, relatorio.id(), 1),
                CodigoErro.RECURSO_NAO_ENCONTRADO);

        c.aprovar.executar(c.clara.id(), relatorio.id(), 1, Canal.WEB);
        ObterDocumentoRelatorioCasoDeUso.DocumentoObtido oficial =
                c.obterDocumento.oficial(c.clara.id(), TipoRelatorio.AULA, relatorio.id());
        assertThat(new String(oficial.conteudo(), StandardCharsets.UTF_8))
                .isEqualTo("PDF OFICIAL v1");
        assertThat(oficial.nomeArquivo()).isEqualTo("relatorio-aula-2026-09-21.pdf");
        assertThat(c.documentosPdf.documentos.values())
                .extracting(d -> d.tipo())
                .containsExactlyInAnyOrder(TipoDocumento.CONFERENCIA, TipoDocumento.OFICIAL);

        falhaCom(
                () ->
                        c.obterDocumento.oficial(
                                c.clara.id(), TipoRelatorio.SEMESTRAL, relatorio.id()),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
        falhaCom(
                () ->
                        c.obterDocumento.conferencia(
                                c.clara.id(), TipoRelatorio.SEMESTRAL, relatorio.id(), 1),
                CodigoErro.RECURSO_NAO_ENCONTRADO);
    }

    @Test
    void fonteSemestralRegistrada() {
        var obtido =
                new ObterDocumentoRelatorioCasoDeUso.DocumentoObtido(
                        new byte[] {1}, "semestral.pdf");
        c.obterDocumento.registrarFonteSemestral(
                new ObterDocumentoRelatorioCasoDeUso.FonteDocumentos() {
                    @Override
                    public TipoRelatorio tipo() {
                        return TipoRelatorio.SEMESTRAL;
                    }

                    @Override
                    public ObterDocumentoRelatorioCasoDeUso.DocumentoObtido conferencia(
                            UUID p, UUID r, int n) {
                        return obtido;
                    }

                    @Override
                    public ObterDocumentoRelatorioCasoDeUso.DocumentoObtido oficial(
                            UUID p, UUID r) {
                        return obtido;
                    }
                });
        assertThat(
                        c.obterDocumento.oficial(
                                c.clara.id(), TipoRelatorio.SEMESTRAL, UUID.randomUUID()))
                .isSameAs(obtido);
        assertThat(
                        c.obterDocumento.conferencia(
                                c.clara.id(), TipoRelatorio.SEMESTRAL, UUID.randomUUID(), 1))
                .isSameAs(obtido);
        assertThat(List.of()).isEmpty();
        assertThat(TarefaLlm.values()).isNotEmpty();
    }
}
