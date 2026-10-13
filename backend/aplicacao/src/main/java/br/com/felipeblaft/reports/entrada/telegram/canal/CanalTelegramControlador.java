package br.com.felipeblaft.reports.entrada.telegram.canal;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.entrada.interno.seguranca.ComparadorSegredo;
import br.com.felipeblaft.reports.entrada.telegram.ProcessadorUpdateTelegramServico;
import br.com.felipeblaft.reports.entrada.telegram.RegistroUpdatesTelegram;
import br.com.felipeblaft.reports.entrada.telegram.RegistroUpdatesTelegram.SituacaoUpdate;
import br.com.felipeblaft.reports.entrada.telegram.ResultadoUpdateTelegram;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.reports.entrada.telegram.UpdateTelegram;
import br.com.felipeblaft.reports.entrada.telegram.canal.AcaoCanalMapeador.AcaoMapeada;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Result;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Status;
import br.com.felipeblaft.reports.usecase.channel.GetChannelDocumentUseCase;
import br.com.felipeblaft.reports.usecase.channel.GetUnconfirmedActionsOfUpdateUseCase;
import br.com.felipeblaft.reports.usecase.channel.QueryChannelActionUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API interna do canal Telegram para o orquestrador (contracts/canal-telegram-interno.md). Só
 * adaptação de formato: recebe o update bruto, usa o <strong>mesmo</strong> interpretador do
 * webhook, devolve as ações v1 e recebe as confirmações. Não tem regra de conversa (FR-005) e
 * <strong>nunca</strong> envia nada ao Telegram (FR-010). Logs só com {@code updateId} e {@code
 * acaoId} (FR-029). {@code 401} e {@code 404} saem sem corpo.
 */
@RestController
@RequestMapping("/interno/v1/canal/telegram")
public class CanalTelegramControlador {

    public static final String CABECALHO_SEGREDO = "X-Telegram-Bot-Api-Secret-Token";

    private static final Logger LOG = LoggerFactory.getLogger(CanalTelegramControlador.class);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final Pattern FORMATO_DOCUMENTO = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern FORMATO_ACAO_ID = Pattern.compile("^[A-Za-z0-9:_.-]{3,120}$");
    private static final int MAXIMO_RESULTADOS = 100;
    private static final Duration ESPERA_PROCESSAMENTO = Duration.ofSeconds(3);
    private static final long INTERVALO_ESPERA_MS = 100;
    private static final Duration IDADE_DE_LINHA_PERDIDA = Duration.ofSeconds(30);

    private final String segredoWebhook;
    private final ComparadorSegredo comparador = new ComparadorSegredo();
    private final ProcessadorUpdateTelegramServico processador;
    private final RegistroUpdatesTelegram registroUpdates;
    private final AcaoCanalMapeador mapeador;
    private final SaveChannelActionsUseCase gravar;
    private final GetUnconfirmedActionsOfUpdateUseCase obterNaoConfirmadas;
    private final ConfirmChannelActionsExecutionUseCase confirmar;
    private final QueryChannelActionUseCase consultar;
    private final GetChannelDocumentUseCase obterDocumento;
    private final TransactionPort transacao;
    private final ClockPort relogio;
    private final MeterRegistry metricas;

    @SuppressWarnings("java:S107")
    public CanalTelegramControlador(
            PropriedadesAplicacao propriedades,
            ProcessadorUpdateTelegramServico processador,
            RegistroUpdatesTelegram registroUpdates,
            AcaoCanalMapeador mapeador,
            SaveChannelActionsUseCase gravar,
            GetUnconfirmedActionsOfUpdateUseCase obterNaoConfirmadas,
            ConfirmChannelActionsExecutionUseCase confirmar,
            QueryChannelActionUseCase consultar,
            GetChannelDocumentUseCase obterDocumento,
            TransactionPort transacao,
            ClockPort relogio,
            MeterRegistry metricas) {
        this.segredoWebhook = propriedades.telegram().segredoWebhook();
        this.processador = processador;
        this.registroUpdates = registroUpdates;
        this.mapeador = mapeador;
        this.gravar = gravar;
        this.obterNaoConfirmadas = obterNaoConfirmadas;
        this.confirmar = confirmar;
        this.consultar = consultar;
        this.obterDocumento = obterDocumento;
        this.transacao = transacao;
        this.relogio = relogio;
        this.metricas = metricas;
    }

    // ------------------------------------------------------------------ POST /atualizacao

    @PostMapping("/atualizacao")
    public ResponseEntity<AtualizacaoResposta> atualizacao(
            @RequestHeader(name = CABECALHO_SEGREDO, required = false) String segredoRecebido,
            @RequestBody(required = false) String corpo) {
        if (!comparador.igual(segredoRecebido, segredoWebhook)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<UpdateTelegram> lido = processador.ler(corpo);
        if (lido.isEmpty()) {
            return ResponseEntity.ok(AtualizacaoResposta.vazia());
        }
        UpdateTelegram update = lido.get();
        ResultadoUpdateTelegram resultado = processador.processarInterno(update);
        if (!resultado.repetido()) {
            return ResponseEntity.ok(registrarAcoes(update, resultado.saidas(), false));
        }
        return ResponseEntity.ok(reentregar(update));
    }

    /** Mapeia, grava na outbox e marca o update como concluído, tudo na mesma transação. */
    private AtualizacaoResposta registrarAcoes(
            UpdateTelegram update, List<SaidaTelegram> saidas, boolean perdido) {
        long id = update.updateId();
        long chat = chatDe(update);
        List<AcaoMapeada> acoes;
        try {
            acoes = mapeador.mapear(saidas, chat, idsDeResposta(id, perdido));
        } catch (RuntimeException erro) {
            // toda mensagem recebida traz ao menos uma ação, mesmo se o mapeamento falhar (FR-039)
            LOG.error(
                    "canal-erro-mapeamento updateId={} tipo={}",
                    id,
                    erro.getClass().getSimpleName());
            acoes =
                    mapeador.mapear(
                            processador.respostaDeErro(update), chat, idsDeResposta(id, true));
        }
        List<AcaoMapeada> finais = acoes;
        transacao.run(
                () -> {
                    gravar.run(
                            "u" + id,
                            ChannelActionOrigin.RESPOSTA_UPDATE,
                            id,
                            finais.stream().map(a -> a.paraNovaAcao(null)).toList());
                    registroUpdates.concluir(id, finais.size());
                });
        return AtualizacaoResposta.de(
                id, finais.stream().map(a -> (JsonNode) a.noResposta()).toList());
    }

    /**
     * Update repetido (FR-007): só as ações ainda não confirmadas, com os mesmos ids. Se o primeiro
     * processamento ainda está em curso, espera um pouco; se a linha ficou sem conclusão por muito
     * tempo, o processamento se perdeu e o professor recebe {@code ERRO_INTERNO}.
     */
    private AtualizacaoResposta reentregar(UpdateTelegram update) {
        long id = update.updateId();
        Optional<SituacaoUpdate> situacao = registroUpdates.situacao(id);
        if (situacao.isEmpty() || !situacao.get().interno()) {
            return AtualizacaoResposta.de(id, List.of());
        }
        SituacaoUpdate atual = situacao.get();
        long limite = System.nanoTime() + ESPERA_PROCESSAMENTO.toNanos();
        while (atual.totalAcoes() == null && System.nanoTime() < limite) {
            if (!dormir()) {
                break;
            }
            Optional<SituacaoUpdate> nova = registroUpdates.situacao(id);
            if (nova.isEmpty()) {
                break;
            }
            atual = nova.get();
        }
        if (atual.totalAcoes() == null) {
            boolean perdida =
                    Duration.between(atual.recebidoEm(), relogio.now())
                                    .compareTo(IDADE_DE_LINHA_PERDIDA)
                            > 0;
            return perdida
                    ? registrarAcoes(update, processador.respostaDeErro(update), true)
                    : AtualizacaoResposta.de(id, List.of());
        }
        List<ChannelAction> pendentes = obterNaoConfirmadas.run(id);
        return AtualizacaoResposta.de(
                id,
                pendentes.stream()
                        .map(a -> (JsonNode) AcaoCanalMapeador.comAcaoId(a.id(), a.payload()))
                        .toList());
    }

    private static boolean dormir() {
        try {
            Thread.sleep(INTERVALO_ESPERA_MS);
            return true;
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static IntFunction<String> idsDeResposta(long updateId, boolean perdido) {
        return perdido ? pos -> "u" + updateId + "-e" + pos : pos -> "u" + updateId + "-" + pos;
    }

    private static long chatDe(UpdateTelegram update) {
        if (update.message() != null && update.message().chat() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null) {
            if (update.callbackQuery().message() != null
                    && update.callbackQuery().message().chat() != null) {
                return update.callbackQuery().message().chat().id();
            }
            if (update.callbackQuery().from() != null) {
                return update.callbackQuery().from().id();
            }
        }
        return 0L;
    }

    // ------------------------------------------------------------------ POST /confirmacoes

    @PostMapping("/confirmacoes")
    public ResponseEntity<Object> confirmacoes(@RequestBody(required = false) String corpo) {
        ConfirmacaoRequisicao requisicao;
        try {
            requisicao = corpo == null ? null : JSON.readValue(corpo, ConfirmacaoRequisicao.class);
        } catch (RuntimeException ilegivel) {
            return invalida();
        }
        if (requisicao == null || requisicao.versao() == null) {
            return invalida();
        }
        if (!AtualizacaoResposta.VERSAO.equals(requisicao.versao())) {
            return ResponseEntity.badRequest().body(ErroCanalResposta.versaoNaoSuportada());
        }
        List<ConfirmacaoRequisicao.ResultadoAcaoDto> itens = requisicao.resultados();
        if (itens == null || itens.isEmpty() || itens.size() > MAXIMO_RESULTADOS) {
            return invalida();
        }
        List<Result> resultados =
                itens.stream()
                        .map(
                                item ->
                                        item == null
                                                ? new Result(null, null, null, null)
                                                : new Result(
                                                        item.acaoId(),
                                                        situacaoDe(item.situacao()),
                                                        item.codigoMotivo(),
                                                        item.mensagemId()))
                        .toList();
        ConfirmChannelActionsExecutionUseCase.Response resposta = confirmar.run(resultados);
        contarConfirmacoes(resultados);
        if (resposta.contractViolation()) {
            LOG.warn("canal-confirmacao-sem-mensagem-id-em-etapa");
            return invalida();
        }
        return ResponseEntity.ok(
                new ConfirmacaoResposta(
                        AtualizacaoResposta.VERSAO, resposta.accepted(), resposta.ignored()));
    }

    /**
     * Métrica sem dado de aluno: só a situação e o código (valor desconhecido vira ERRO_TELEGRAM).
     */
    private void contarConfirmacoes(List<Result> resultados) {
        for (Result resultado : resultados) {
            if (resultado.status() == null) {
                continue;
            }
            String codigo =
                    resultado.status() == Status.EXECUTADA || resultado.reasonCode() == null
                            ? "NENHUM"
                            : ConfirmChannelActionsExecutionUseCase.normalize(
                                    resultado.reasonCode());
            metricas.counter(
                            "canal_confirmacoes_total",
                            "situacao",
                            resultado.status().name(),
                            "codigo",
                            codigo)
                    .increment();
        }
    }

    private static Status situacaoDe(String texto) {
        if (texto == null) {
            return null;
        }
        return switch (texto) {
            case "EXECUTADA" -> Status.EXECUTADA;
            case "FALHOU" -> Status.FALHOU;
            default -> null;
        };
    }

    private static ResponseEntity<Object> invalida() {
        return ResponseEntity.badRequest().body(ErroCanalResposta.requisicaoInvalida());
    }

    // ------------------------------------------------------------------ GET /acoes/{acaoId}

    @GetMapping("/acoes/{acaoId}")
    public ResponseEntity<EstadoAcaoResposta> estadoDaAcao(@PathVariable String acaoId) {
        if (!FORMATO_ACAO_ID.matcher(acaoId).matches()) {
            return ResponseEntity.notFound().build();
        }
        return consultar
                .run(acaoId)
                .map(
                        acao ->
                                ResponseEntity.ok(
                                        new EstadoAcaoResposta(
                                                AtualizacaoResposta.VERSAO,
                                                acao.id(),
                                                acao.state().name(),
                                                acao.reasonCode())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------ GET /documentos/{id}

    @GetMapping("/documentos/{documentoId}")
    public ResponseEntity<byte[]> documento(@PathVariable String documentoId) {
        // URL, caminho, tamanho ou alfabeto errado: 404 antes de tocar no banco (FR-023)
        if (!FORMATO_DOCUMENTO.matcher(documentoId).matches()) {
            return ResponseEntity.notFound().build();
        }
        Optional<ObtainedDocument> documento = obterDocumento.run(documentoId);
        if (documento.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(documento.get().fileName())
                                .build()
                                .toString())
                .body(documento.get().content());
    }
}
