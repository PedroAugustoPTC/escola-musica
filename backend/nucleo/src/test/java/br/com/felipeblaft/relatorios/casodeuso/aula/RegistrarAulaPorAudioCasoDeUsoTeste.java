package br.com.felipeblaft.relatorios.casodeuso.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrarAulaPorAudioCasoDeUsoTeste {

    private final CenarioRelatorio c = new CenarioRelatorio();

    @Test
    void enfileiraORegistroComOCanalEOArquivo() {
        ArquivoTemporario arquivo = CenarioRelatorio.arquivo("web.webm", 2048);

        TrabalhoProcessamento trabalho =
                c.registrar.executar(c.clara.id(), c.matteo.id(), arquivo, Canal.WEB);

        assertThat(trabalho.tipo()).isEqualTo(TipoTrabalho.REGISTRO_AULA);
        assertThat(trabalho.canalSolicitacao()).isEqualTo(Canal.WEB);
        assertThat(trabalho.etapa()).isEqualTo(EtapaTrabalho.ENVIANDO);
        assertThat(trabalho.arquivoTemporario())
                .isEqualTo(Path.of("/tmp/audios/web.webm").toString());
        assertThat(trabalho.parametros()).containsEntry("arquivoTamanho", "2048");
        assertThat(c.fila.trabalhos).containsKey(trabalho.id());
    }

    @Test
    void referenciaDoTelegramNaoTrazBytes() {
        ReferenciaArquivoCanal voz =
                new ReferenciaArquivoCanal("file-1", "u-1", "audio/ogg", 30_000);

        TrabalhoProcessamento trabalho =
                c.registrar.executar(c.clara.id(), c.matteo.id(), voz, Canal.TELEGRAM);

        assertThat(trabalho.arquivoTemporario()).isNull();
        assertThat(trabalho.parametros())
                .containsEntry("arquivoCanal", "file-1")
                .containsEntry("arquivoCanalUnico", "u-1")
                .containsEntry("arquivoMime", "audio/ogg");
    }

    @Test
    void alunoDeOutroProfessorDescartaOAudio() {
        ArquivoTemporario arquivo = CenarioRelatorio.arquivo("x.ogg", 10);
        assertThatThrownBy(
                        () -> c.registrar.executar(c.clara.id(), c.helena.id(), arquivo, Canal.WEB))
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(CodigoErro.ALUNO_NAO_ASSOCIADO);
        assertThat(c.normalizacao.apagados).contains(arquivo.caminho());
        assertThat(c.fila.trabalhos).isEmpty();
    }

    @Test
    void relatorioEmAbertoComDetalhes() {
        Instant quando = Instant.parse("2026-09-20T12:57:00Z");
        c.emAberto.adicionar(
                c.clara.id(),
                new RelatorioEmAberto(
                        TipoRelatorio.AULA,
                        null,
                        java.util.UUID.randomUUID(),
                        c.matteo.id(),
                        "Matteo Mafra",
                        null,
                        EtapaTrabalho.TRANSCREVENDO,
                        null,
                        Canal.TELEGRAM,
                        quando));

        assertThatThrownBy(
                        () ->
                                c.registrar.executar(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        CenarioRelatorio.arquivo("x", 1),
                                        Canal.WEB))
                .isInstanceOf(ExcecaoDominio.class)
                .satisfies(
                        e -> {
                            ExcecaoDominio erro = (ExcecaoDominio) e;
                            assertThat(erro.codigo()).isEqualTo(CodigoErro.RELATORIO_EM_ABERTO);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> aberto =
                                    (Map<String, Object>) erro.detalhes().get("relatorioEmAberto");
                            assertThat(aberto)
                                    .containsEntry("canalOrigem", Canal.TELEGRAM)
                                    .containsEntry("atualizadoEm", quando);
                        });
    }

    @Test
    void registroAindaEmTranscricaoContaComoEmAberto() {
        c.registrar.executar(
                c.clara.id(), c.matteo.id(), CenarioRelatorio.arquivo("a", 1), Canal.WEB);
        assertThatThrownBy(
                        () ->
                                c.registrar.executar(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        CenarioRelatorio.arquivo("b", 1),
                                        Canal.TELEGRAM))
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(CodigoErro.RELATORIO_EM_ABERTO);
        assertThat(c.normalizacao.apagados).contains(Path.of("/tmp/audios/b"));
    }

    @Test
    void audioMuitoGrandeInformaOLimite() {
        long acima = c.parametros.limiteAudioBytes() + 1;
        assertThatThrownBy(
                        () ->
                                c.registrar.executar(
                                        c.clara.id(),
                                        c.matteo.id(),
                                        new ReferenciaArquivoCanal("f", null, "video/mp4", acima),
                                        Canal.TELEGRAM))
                .isInstanceOf(ExcecaoDominio.class)
                .satisfies(
                        e -> {
                            assertThat(((ExcecaoDominio) e).codigo())
                                    .isEqualTo(CodigoErro.AUDIO_MUITO_GRANDE);
                            assertThat(((ExcecaoDominio) e).detalhes())
                                    .containsEntry("limiteMb", 50);
                        });
        assertThat(c.fila.trabalhos).isEmpty();
    }

    @Test
    void dataDaAulaPertoDaMeiaNoiteNoFusoDaEscola() {
        // 02:30 UTC de 22/09 ainda é 21/09 em São Paulo
        c.relogio.definir(Instant.parse("2026-09-22T02:30:00Z"));
        c.llm.programar(
                br.com.felipeblaft.relatorios.porta.TarefaLlm.ESTRUTURACAO_AULA,
                CenarioRelatorio.saida("x", List.of()));
        c.registrar.executar(
                c.clara.id(), c.matteo.id(), CenarioRelatorio.arquivo("a", 1), Canal.WEB);
        c.processarFila();
        assertThat(c.relatorios.aulas.values())
                .singleElement()
                .satisfies(
                        a ->
                                assertThat(a.dataAula())
                                        .isEqualTo(java.time.LocalDate.of(2026, 9, 21)));
    }
}
