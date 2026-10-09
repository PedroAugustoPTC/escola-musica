package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.canal.EmitirDocumentoCanalCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.entrada.telegram.ReferenciaDocumento;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoCanalMapeador.AcaoMapeada;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import br.com.felipeblaft.relatorios.suporte.EsquemaAcoesV1;
import br.com.felipeblaft.relatorios.suporte.RelogioAjustavel;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AcaoCanalMapeadorTeste {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, DocumentoCanal> documentos = new HashMap<>();
    private final DocumentoCanalRepositorio repositorio =
            new DocumentoCanalRepositorio() {
                @Override
                public void emitir(DocumentoCanal documento) {
                    documentos.put(documento.tokenHash(), documento);
                }

                @Override
                public Optional<DocumentoCanal> buscarPorHash(String tokenHash) {
                    return Optional.ofNullable(documentos.get(tokenHash));
                }

                @Override
                public int expurgarVencidos(Instant agora) {
                    return 0;
                }
            };
    private final AcaoCanalMapeador mapeador =
            new AcaoCanalMapeador(
                    new EmitirDocumentoCanalCasoDeUso(
                            repositorio,
                            new RelogioAjustavel(),
                            new PoliticaRetentativa(
                                    Duration.ofSeconds(30),
                                    Duration.ofMinutes(15),
                                    Duration.ofHours(24))));

    private List<AcaoMapeada> mapear(SaidaTelegram... saidas) {
        List<AcaoMapeada> acoes = mapeador.mapear(List.of(saidas), 4242L, pos -> "u90211-" + pos);
        acoes.forEach(a -> EsquemaAcoesV1.exigir("Acao", a.noResposta()));
        return acoes;
    }

    // ---- mensagens

    @Test
    void mensagemSimplesSemTecladoNaoEmiteOCampo() {
        AcaoMapeada acao =
                mapear(new SaidaTelegram.Mensagem(7L, "Olá <b>Clara</b>", Teclado.NENHUM)).get(0);
        assertThat(acao.acaoId()).isEqualTo("u90211-0");
        assertThat(acao.tipo()).isEqualTo(TipoAcaoCanal.MENSAGEM);
        assertThat(acao.chatId()).isEqualTo(7L);
        assertThat(acao.noResposta().toString())
                .isEqualTo(
                        "{\"acaoId\":\"u90211-0\",\"tipo\":\"MENSAGEM\",\"chatId\":7,"
                                + "\"texto\":\"Olá <b>Clara</b>\"}");
        // o payload guardado não leva o acaoId (ele é do envelope e é o id da linha)
        assertThat(acao.payload()).doesNotContain("acaoId").contains("\"texto\"");
        assertThat(acao.compensacao()).isNull();
    }

    @Test
    void tecladoVaiComRotuloEDadosEFicaEmLinhas() {
        AcaoMapeada acao =
                mapear(
                                new SaidaTelegram.Mensagem(
                                        7L,
                                        "Escolha",
                                        new Teclado(
                                                List.of(
                                                        List.of(new Botao("Aprovar", "v1:aprov:2")),
                                                        List.of(
                                                                new Botao("PDF", "v1:pdf:"),
                                                                new Botao(
                                                                        "Cancelar", "v1:canc:"))))))
                        .get(0);
        JsonNode linhas = acao.noResposta().path("teclado").path("linhas");
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).get(0).path("rotulo").asString()).isEqualTo("Aprovar");
        assertThat(linhas.get(0).get(0).path("dados").asString()).isEqualTo("v1:aprov:2");
        assertThat(linhas.get(1)).hasSize(2);
    }

    @Test
    void textoLongoEDivididoComTecladoSoNaUltimaEIdsPorPosicaoDepoisDaExpansao() {
        String linha = "x".repeat(99) + "\n";
        String longo = linha.repeat(100); // 10.000 caracteres
        List<AcaoMapeada> acoes =
                mapear(
                        new SaidaTelegram.RespostaCallback("cb1"),
                        new SaidaTelegram.Mensagem(
                                7L, longo, Teclado.de(new Botao("OK", "v1:ok:"))),
                        new SaidaTelegram.Mensagem(7L, "fim", Teclado.NENHUM));

        assertThat(acoes).hasSizeGreaterThanOrEqualTo(5);
        assertThat(acoes).extracting(AcaoMapeada::acaoId).first().isEqualTo("u90211-0");
        for (int i = 0; i < acoes.size(); i++) {
            assertThat(acoes.get(i).acaoId()).isEqualTo("u90211-" + i);
        }
        List<AcaoMapeada> partes = acoes.subList(1, acoes.size() - 1);
        for (int i = 0; i < partes.size(); i++) {
            JsonNode no = partes.get(i).noResposta();
            assertThat(no.path("texto").asString().length()).isLessThanOrEqualTo(4096);
            assertThat(no.has("teclado")).isEqualTo(i == partes.size() - 1);
        }
        assertThat(
                        partes.stream()
                                .map(p -> p.noResposta().path("texto").asString())
                                .reduce("", (a, b) -> a + b)
                                .replace("\n", ""))
                .isEqualTo(longo.replace("\n", ""));
        assertThat(acoes.getLast().noResposta().path("texto").asString()).isEqualTo("fim");
    }

    // ---- edição e compensação

    @Test
    void edicaoComTecladoVazioEExplicitaEGeraCompensacaoComOMesmoConteudoSemAcaoId() {
        AcaoMapeada acao =
                mapear(new SaidaTelegram.Edicao(7L, 900L, "<i>Pronto</i>", Teclado.NENHUM)).get(0);
        assertThat(acao.tipo()).isEqualTo(TipoAcaoCanal.EDICAO);
        assertThat(acao.noResposta().path("mensagemId").asLong()).isEqualTo(900L);
        assertThat(acao.noResposta().path("teclado").path("linhas")).isEmpty();
        assertThat(acao.noResposta().path("teclado").isObject()).isTrue();

        JsonNode compensacao = JSON.readTree(acao.compensacao());
        assertThat(compensacao.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(compensacao.path("texto").asString()).isEqualTo("<i>Pronto</i>");
        assertThat(compensacao.path("chatId").asLong()).isEqualTo(7L);
        assertThat(compensacao.has("acaoId")).isFalse();
        assertThat(compensacao.has("teclado")).isFalse();
    }

    @Test
    void compensacaoDeEdicaoComTecladoLevaOTeclado() {
        AcaoMapeada acao =
                mapear(new SaidaTelegram.Edicao(7L, 900L, "t", Teclado.de(new Botao("A", "v1:a:"))))
                        .get(0);
        assertThat(JSON.readTree(acao.compensacao()).path("teclado").path("linhas")).hasSize(1);
    }

    // ---- documento

    @Test
    void documentoViraIdOpacoSemBytesNemCaminhoNemUrl() {
        UUID professor = UUID.randomUUID();
        UUID relatorio = UUID.randomUUID();
        byte[] pdf = "%PDF-conteudo-do-relatorio".getBytes();
        AcaoMapeada acao =
                mapear(
                                new SaidaTelegram.Documento(
                                        7L,
                                        pdf,
                                        "relatorio-aula-2026-03-10-v2.pdf",
                                        "Segue o relatório",
                                        ReferenciaDocumento.conferencia(
                                                professor, TipoRelatorio.AULA, relatorio, 2)))
                        .get(0);
        JsonNode no = acao.noResposta();
        assertThat(acao.tipo()).isEqualTo(TipoAcaoCanal.DOCUMENTO);
        assertThat(no.path("documentoId").asString()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(no.path("nomeArquivo").asString()).isEqualTo("relatorio-aula-2026-03-10-v2.pdf");
        assertThat(no.path("tipoConteudo").asString()).isEqualTo("application/pdf");
        assertThat(no.path("legenda").asString()).isEqualTo("Segue o relatório");
        assertThat(no.propertyNames())
                .containsExactlyInAnyOrder(
                        "acaoId",
                        "tipo",
                        "chatId",
                        "documentoId",
                        "nomeArquivo",
                        "tipoConteudo",
                        "legenda");
        assertThat(no.toString()).doesNotContain("PDF-conteudo");
        // só o hash do identificador foi guardado, com a referência e o dono
        DocumentoCanal guardado =
                documentos.get(DocumentoCanal.hashDoToken(no.path("documentoId").asString()));
        assertThat(guardado.professorId()).isEqualTo(professor);
        assertThat(guardado.relatorioId()).isEqualTo(relatorio);
        assertThat(guardado.versao()).isEqualTo(2);
        assertThat(guardado.nomeArquivo()).isEqualTo("relatorio-aula-2026-03-10-v2.pdf");
        assertThat(acao.compensacao()).isNull();
    }

    @Test
    void documentoGuardaACompensacaoComAvisoEBotaoSemDocumentoNemTextoDoRelatorio()
            throws Exception {
        AcaoMapeada acao =
                mapear(
                                new SaidaTelegram.Documento(
                                        7L,
                                        "%PDF-conteudo-do-relatorio".getBytes(),
                                        "relatorio-aula.pdf",
                                        "Segue o relatório do Matteo",
                                        ReferenciaDocumento.conferencia(
                                                UUID.randomUUID(),
                                                TipoRelatorio.AULA,
                                                UUID.randomUUID(),
                                                2),
                                        new SaidaTelegram.Mensagem(
                                                7L,
                                                "Não consegui enviar o PDF agora.",
                                                Teclado.de(new Botao("Ver PDF", "v1:pdf:")))))
                        .get(0);

        JsonNode compensacao = JSON.readTree(acao.compensacao());
        assertThat(compensacao.path("tipo").asString()).isEqualTo("MENSAGEM");
        assertThat(compensacao.path("chatId").asLong()).isEqualTo(7L);
        assertThat(compensacao.path("texto").asString())
                .isEqualTo("Não consegui enviar o PDF agora.");
        assertThat(
                        compensacao
                                .path("teclado")
                                .path("linhas")
                                .get(0)
                                .get(0)
                                .path("dados")
                                .asString())
                .isEqualTo("v1:pdf:");
        assertThat(
                        compensacao
                                .path("teclado")
                                .path("linhas")
                                .get(0)
                                .get(0)
                                .path("rotulo")
                                .asString())
                .isEqualTo("Ver PDF");
        assertThat(compensacao.has("acaoId")).isFalse();
        assertThat(acao.compensacao())
                .doesNotContain("documentoId", "nomeArquivo", "legenda", "Matteo", "PDF-conteudo");
        // a resposta ao orquestrador continua sendo só o documento
        assertThat(acao.noResposta().has("compensacao")).isFalse();
        assertThat(acao.noResposta().path("tipo").asString()).isEqualTo("DOCUMENTO");
    }

    @Test
    void documentoOficialNaoLevaVersaoENaoTemLegendaQuandoNula() {
        AcaoMapeada acao =
                mapear(
                                new SaidaTelegram.Documento(
                                        7L,
                                        new byte[] {1},
                                        "oficial.pdf",
                                        null,
                                        ReferenciaDocumento.oficial(
                                                UUID.randomUUID(),
                                                TipoRelatorio.SEMESTRAL,
                                                UUID.randomUUID())))
                        .get(0);
        assertThat(acao.noResposta().has("legenda")).isFalse();
        assertThat(documentos.values())
                .singleElement()
                .satisfies(d -> assertThat(d.oficial()).isTrue());
    }

    @Test
    void documentoSemReferenciaEErroDeProgramacao() {
        assertThatThrownBy(
                        () ->
                                mapeador.mapear(
                                        List.of(
                                                new SaidaTelegram.Documento(
                                                        7L, new byte[] {1}, "x.pdf", null, null)),
                                        7L,
                                        pos -> "u1-" + pos))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- callback e etapa

    @Test
    void respostaDeCallbackUsaOChatDoUpdateESaiPrimeiroNaOrdemRecebida() {
        List<AcaoMapeada> acoes =
                mapear(
                        new SaidaTelegram.RespostaCallback("cb-77"),
                        new SaidaTelegram.Mensagem(7L, "Confirma?", Teclado.NENHUM));
        assertThat(acoes.get(0).tipo()).isEqualTo(TipoAcaoCanal.RESPOSTA_CALLBACK);
        assertThat(acoes.get(0).chatId()).isEqualTo(4242L);
        assertThat(acoes.get(0).noResposta().path("callbackId").asString()).isEqualTo("cb-77");
        assertThat(acoes.get(0).noResposta().has("chatId")).isFalse();
        assertThat(acoes.get(1).acaoId()).isEqualTo("u90211-1");
    }

    @Test
    void etapaLevaOTrabalhoEOIdDaMensagemQuandoOInterpretadorOSabeEACompensacaoNaoLeva() {
        UUID trabalho = UUID.randomUUID();
        AcaoMapeada editando =
                mapear(new SaidaTelegram.MensagemEtapa(7L, 555L, "<i>Transcrevendo…</i>", trabalho))
                        .get(0);
        assertThat(editando.tipo()).isEqualTo(TipoAcaoCanal.MENSAGEM_ETAPA);
        assertThat(editando.trabalhoId()).isEqualTo(trabalho);
        assertThat(editando.noResposta().path("trabalhoId").asString())
                .isEqualTo(trabalho.toString());
        assertThat(editando.noResposta().path("editarMensagemId").asLong()).isEqualTo(555L);
        assertThat(editando.payload()).contains("\"editarMensagemId\":555");
        // a compensação (mensagem nova) é a mesma ação sem editarMensagemId e sem acaoId
        JsonNode compensacao = JSON.readTree(editando.compensacao());
        assertThat(compensacao.has("editarMensagemId")).isFalse();
        assertThat(compensacao.has("acaoId")).isFalse();
        assertThat(compensacao.path("texto").asString()).isEqualTo("<i>Transcrevendo…</i>");
        assertThat(compensacao.path("trabalhoId").asString()).isEqualTo(trabalho.toString());

        AcaoMapeada nova =
                mapear(new SaidaTelegram.MensagemEtapa(7L, null, "<i>Enviando…</i>", trabalho))
                        .get(0);
        assertThat(nova.noResposta().has("editarMensagemId")).isFalse();
        assertThat(JSON.readTree(nova.compensacao())).isEqualTo(JSON.readTree(nova.payload()));
    }

    // ---- reconstituição para reentrega

    @Test
    void comAcaoIdReconstituiAAcaoCompletaComOIdPrimeiro() {
        AcaoMapeada acao = mapear(new SaidaTelegram.Mensagem(7L, "oi", Teclado.NENHUM)).get(0);
        assertThat(AcaoCanalMapeador.comAcaoId("u90211-0", acao.payload()).toString())
                .isEqualTo(acao.noResposta().toString());
        assertThat(AcaoCanalMapeador.comAcaoId("u90211-0", "{\"a\":1}").toString())
                .isEqualTo("{\"acaoId\":\"u90211-0\",\"a\":1}");
    }

    @Test
    void ausenciaDeAudioNosNosGerados() {
        List<AcaoMapeada> acoes =
                mapear(
                        new SaidaTelegram.Mensagem(
                                7L, "Recebi o áudio de Matteo. É dele mesmo?", Teclado.NENHUM),
                        new SaidaTelegram.MensagemEtapa(7L, null, "etapa", UUID.randomUUID()));
        for (AcaoMapeada acao : acoes) {
            assertThat(acao.noResposta().toString())
                    .doesNotContain("file_id", "file_path", ".oga", ".ogg", "bytes", "audio");
        }
    }
}
