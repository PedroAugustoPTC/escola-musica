package br.com.felipeblaft.relatorios.dominio.canal;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ação de canal na outbox (data-model.md §1.2 e §2): o que o orquestrador deve executar no chat. O
 * {@code payload} e a {@code compensacao} são texto opaco para o núcleo (o formato v1 é
 * conhecimento do adaptador de entrada). Estado final nunca volta; confirmação repetida e idêntica
 * é inócua.
 */
public class AcaoCanal {

    public static final String CODIGO_VINCULO_DESFEITO = "VINCULO_DESFEITO";
    public static final String CODIGO_PRAZO_VENCIDO = "PRAZO_VENCIDO";
    public static final String SUFIXO_COMPENSACAO = "-c";

    private final String id;
    private final String loteId;
    private final Canal canal;
    private final OrigemAcaoCanal origem;
    private final Long updateId;
    private final long chatId;
    private final UUID professorId;
    private final UUID trabalhoId;
    private final TipoAcaoCanal tipo;
    private String payload;
    private String compensacao;
    private EstadoAcaoCanal estado;
    private int tentativas;
    private Instant proximaTentativaEm;
    private final Instant prazoEm;
    private final Instant criadoEm;
    private Instant entregueEm;
    private Instant finalizadoEm;
    private String codigoMotivo;
    private int versao;

    @SuppressWarnings("java:S107")
    public AcaoCanal(
            String id,
            String loteId,
            Canal canal,
            OrigemAcaoCanal origem,
            Long updateId,
            long chatId,
            UUID professorId,
            UUID trabalhoId,
            TipoAcaoCanal tipo,
            String payload,
            String compensacao,
            EstadoAcaoCanal estado,
            int tentativas,
            Instant proximaTentativaEm,
            Instant prazoEm,
            Instant criadoEm,
            Instant entregueEm,
            Instant finalizadoEm,
            String codigoMotivo,
            int versao) {
        this.id = Objects.requireNonNull(id);
        this.loteId = Objects.requireNonNull(loteId);
        this.canal = Objects.requireNonNull(canal);
        this.origem = Objects.requireNonNull(origem);
        this.updateId = updateId;
        this.chatId = chatId;
        this.professorId = professorId;
        this.trabalhoId = trabalhoId;
        this.tipo = Objects.requireNonNull(tipo);
        this.payload = payload;
        this.compensacao = compensacao;
        this.estado = Objects.requireNonNull(estado);
        this.tentativas = tentativas;
        this.proximaTentativaEm = proximaTentativaEm;
        this.prazoEm = Objects.requireNonNull(prazoEm);
        this.criadoEm = Objects.requireNonNull(criadoEm);
        this.entregueEm = entregueEm;
        this.finalizadoEm = finalizadoEm;
        this.codigoMotivo = codigoMotivo;
        this.versao = versao;
    }

    /**
     * Cria a ação no estado inicial da sua origem: {@code ENTREGUE} para respostas a update (o
     * orquestrador já a tem na resposta HTTP) e {@code PENDENTE} para proativas e compensações.
     */
    @SuppressWarnings("java:S107")
    public static AcaoCanal nova(
            String id,
            String loteId,
            OrigemAcaoCanal origem,
            Long updateId,
            long chatId,
            UUID professorId,
            UUID trabalhoId,
            TipoAcaoCanal tipo,
            String payload,
            String compensacao,
            Instant agora,
            PoliticaRetentativa politica) {
        boolean resposta = origem == OrigemAcaoCanal.RESPOSTA_UPDATE;
        if (resposta && updateId == null) {
            throw new IllegalArgumentException("resposta a update exige updateId");
        }
        return new AcaoCanal(
                id,
                loteId,
                Canal.TELEGRAM,
                origem,
                updateId,
                chatId,
                professorId,
                trabalhoId,
                tipo,
                payload,
                compensacao,
                resposta ? EstadoAcaoCanal.ENTREGUE : EstadoAcaoCanal.PENDENTE,
                0,
                resposta ? null : agora,
                politica.prazoDe(agora),
                agora,
                resposta ? agora : null,
                null,
                null,
                0);
    }

    // ---- transições

    /**
     * Falha de entrega ao orquestrador (recusa, timeout, erro de conexão). Reagenda a tentativa
     * conforme a política ou, se o prazo venceu, encerra em {@code FALHA_DEFINITIVA}. Só age sobre
     * {@code PENDENTE}.
     */
    public void registrarFalhaEntrega(Instant agora, PoliticaRetentativa politica) {
        if (estado != EstadoAcaoCanal.PENDENTE) {
            return;
        }
        tentativas++;
        if (vencida(agora)) {
            finalizar(EstadoAcaoCanal.FALHA_DEFINITIVA, CODIGO_PRAZO_VENCIDO, agora);
            return;
        }
        proximaTentativaEm = politica.proximaTentativa(tentativas, agora);
    }

    /**
     * Reserva (lease) do despacho: empurra a próxima tentativa para o futuro; se o despachante
     * morrer, a ação volta a ser devida quando o lease vence. Só age sobre {@code PENDENTE}.
     */
    public void reservarAte(Instant leaseAte) {
        if (estado == EstadoAcaoCanal.PENDENTE) {
            proximaTentativaEm = leaseAte;
        }
    }

    /** Aceite HTTP do orquestrador. Só age sobre {@code PENDENTE}. */
    public void marcarEntregue(Instant agora) {
        if (estado != EstadoAcaoCanal.PENDENTE) {
            return;
        }
        estado = EstadoAcaoCanal.ENTREGUE;
        entregueEm = agora;
        proximaTentativaEm = null;
        tentativas++;
    }

    public ResultadoConfirmacao confirmarExecutada(Instant agora) {
        if (estado == EstadoAcaoCanal.EXECUTADA) {
            return ResultadoConfirmacao.REPETIDA;
        }
        if (estado.finalizado()) {
            return ResultadoConfirmacao.DIVERGENTE;
        }
        finalizar(EstadoAcaoCanal.EXECUTADA, null, agora);
        return ResultadoConfirmacao.APLICADA;
    }

    public ResultadoConfirmacao confirmarFalha(String codigo, Instant agora) {
        Objects.requireNonNull(codigo);
        if (estado == EstadoAcaoCanal.FALHOU) {
            return codigo.equals(codigoMotivo)
                    ? ResultadoConfirmacao.REPETIDA
                    : ResultadoConfirmacao.DIVERGENTE;
        }
        if (estado.finalizado()) {
            return ResultadoConfirmacao.DIVERGENTE;
        }
        finalizar(EstadoAcaoCanal.FALHOU, codigo, agora);
        return ResultadoConfirmacao.APLICADA;
    }

    /** Descarte por vínculo desfeito: só age sobre {@code PENDENTE}. */
    public void descartar(Instant agora) {
        if (estado == EstadoAcaoCanal.PENDENTE) {
            finalizar(EstadoAcaoCanal.DESCARTADA, CODIGO_VINCULO_DESFEITO, agora);
        }
    }

    /** Encerra uma ação não confirmada cujo prazo venceu. */
    public void expirar(Instant agora) {
        if (vencida(agora)) {
            finalizar(EstadoAcaoCanal.FALHA_DEFINITIVA, CODIGO_PRAZO_VENCIDO, agora);
        }
    }

    public boolean vencida(Instant agora) {
        return !estado.finalizado() && !agora.isBefore(prazoEm);
    }

    // ---- compensação

    /** Só ações que não são elas mesmas compensação e que têm alternativa gravada. */
    public boolean temCompensacao() {
        return origem != OrigemAcaoCanal.COMPENSACAO && compensacao != null;
    }

    public String idDaCompensacao() {
        return id + SUFIXO_COMPENSACAO;
    }

    /**
     * Ação alternativa para quando esta não pôde ser executada: no máximo uma por ação (id {@code
     * <id>-c}), no mesmo chat e com o mesmo professor/trabalho.
     */
    public AcaoCanal criarCompensacao(Instant agora, PoliticaRetentativa politica) {
        if (!temCompensacao()) {
            throw new IllegalStateException("ação sem compensação");
        }
        TipoAcaoCanal tipoCompensacao =
                tipo == TipoAcaoCanal.MENSAGEM_ETAPA
                        ? TipoAcaoCanal.MENSAGEM_ETAPA
                        : TipoAcaoCanal.MENSAGEM;
        return nova(
                idDaCompensacao(),
                "c:" + id,
                OrigemAcaoCanal.COMPENSACAO,
                null,
                chatId,
                professorId,
                trabalhoId,
                tipoCompensacao,
                compensacao,
                null,
                agora,
                politica);
    }

    /** Retenção (R-11): o conteúdo some depois do prazo, o registro fica para auditoria. */
    public void zerarPayload() {
        payload = null;
        compensacao = null;
    }

    private void finalizar(EstadoAcaoCanal novo, String codigo, Instant agora) {
        estado = novo;
        codigoMotivo = codigo;
        finalizadoEm = agora;
        proximaTentativaEm = null;
    }

    // ---- consulta

    public String id() {
        return id;
    }

    public String loteId() {
        return loteId;
    }

    public Canal canal() {
        return canal;
    }

    public OrigemAcaoCanal origem() {
        return origem;
    }

    public Long updateId() {
        return updateId;
    }

    public long chatId() {
        return chatId;
    }

    public UUID professorId() {
        return professorId;
    }

    public UUID trabalhoId() {
        return trabalhoId;
    }

    public TipoAcaoCanal tipo() {
        return tipo;
    }

    public String payload() {
        return payload;
    }

    public String compensacao() {
        return compensacao;
    }

    public EstadoAcaoCanal estado() {
        return estado;
    }

    public int tentativas() {
        return tentativas;
    }

    public Instant proximaTentativaEm() {
        return proximaTentativaEm;
    }

    public Instant prazoEm() {
        return prazoEm;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant entregueEm() {
        return entregueEm;
    }

    public Instant finalizadoEm() {
        return finalizadoEm;
    }

    public String codigoMotivo() {
        return codigoMotivo;
    }

    /** Versão lida do armazenamento (controle otimista). */
    public int versao() {
        return versao;
    }

    /** Chamado pelo repositório depois de uma gravação condicional bem-sucedida. */
    public void definirVersao(int novaVersao) {
        this.versao = novaVersao;
    }
}
