package br.com.felipeblaft.reports.domain.channel;

import br.com.felipeblaft.reports.domain.common.Channel;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ação de canal na outbox (data-model.md §1.2 e §2): o que o orquestrador deve executar no chat. O
 * {@code payload} e a {@code compensacao} são texto opaco para o núcleo (o formato v1 é
 * conhecimento do adaptador de entrada). Estado final nunca volta; confirmação repetida e idêntica
 * é inócua.
 */
public class ChannelAction {

    public static final String LINK_UNDONE_CODE = "LINK_UNDONE";
    public static final String DEADLINE_EXPIRED_CODE = "DEADLINE_EXPIRED";
    public static final String CODE_WITHOUT_CONFIRMATION = "NO_CONFIRMATION";
    public static final String MESSAGE_NOT_EDITABLE_CODE = "MESSAGE_NOT_EDITABLE";
    public static final String MESSAGE_NOT_DELETABLE_CODE = "MESSAGE_NOT_DELETABLE";
    public static final String DOCUMENT_UNAVAILABLE_CODE = "DOCUMENT_UNAVAILABLE";
    public static final String COMPENSATION_SUFFIX = "-c";

    private final String id;
    private final String batchId;
    private final Channel channel;
    private final ChannelActionOrigin origin;
    private final Long updateId;
    private final long chatId;
    private final UUID teacherId;
    private final UUID jobId;
    private final ChannelActionType type;
    private String payload;
    private String compensation;
    private ChannelActionState state;
    private int attempts;
    private Instant nextAttemptAt;
    private final Instant deadlineAt;
    private final Instant createdAt;
    private Instant deliveredAt;
    private Instant finalizedAt;
    private String reasonCode;
    private int version;

    @SuppressWarnings("java:S107")
    public ChannelAction(
            String id,
            String batchId,
            Channel channel,
            ChannelActionOrigin origin,
            Long updateId,
            long chatId,
            UUID teacherId,
            UUID jobId,
            ChannelActionType type,
            String payload,
            String compensation,
            ChannelActionState state,
            int attempts,
            Instant nextAttemptAt,
            Instant deadlineAt,
            Instant createdAt,
            Instant deliveredAt,
            Instant finalizedAt,
            String reasonCode,
            int version) {
        this.id = Objects.requireNonNull(id);
        this.batchId = Objects.requireNonNull(batchId);
        this.channel = Objects.requireNonNull(channel);
        this.origin = Objects.requireNonNull(origin);
        this.updateId = updateId;
        this.chatId = chatId;
        this.teacherId = teacherId;
        this.jobId = jobId;
        this.type = Objects.requireNonNull(type);
        this.payload = payload;
        this.compensation = compensation;
        this.state = Objects.requireNonNull(state);
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.deadlineAt = Objects.requireNonNull(deadlineAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.deliveredAt = deliveredAt;
        this.finalizedAt = finalizedAt;
        this.reasonCode = reasonCode;
        this.version = version;
    }

    /**
     * Cria a ação no estado inicial da sua origem: {@code ENTREGUE} para respostas a update (o
     * orquestrador já a tem na resposta HTTP) e {@code PENDENTE} para proativas e compensações.
     */
    @SuppressWarnings("java:S107")
    public static ChannelAction create(
            String id,
            String batchId,
            ChannelActionOrigin origin,
            Long updateId,
            long chatId,
            UUID teacherId,
            UUID jobId,
            ChannelActionType type,
            String payload,
            String compensation,
            Instant now,
            RetryPolicy policy) {
        boolean response = origin == ChannelActionOrigin.UPDATE_REPLY;
        if (response && updateId == null) {
            throw new IllegalArgumentException("resposta a update exige updateId");
        }
        return new ChannelAction(
                id,
                batchId,
                Channel.TELEGRAM,
                origin,
                updateId,
                chatId,
                teacherId,
                jobId,
                type,
                payload,
                compensation,
                response ? ChannelActionState.DELIVERED : ChannelActionState.PENDING,
                0,
                response ? null : now,
                policy.deadlineOf(now),
                now,
                response ? now : null,
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
    public void registerDeliveryFailure(Instant now, RetryPolicy policy) {
        if (state != ChannelActionState.PENDING) {
            return;
        }
        attempts++;
        if (expired(now)) {
            finalize(ChannelActionState.PERMANENTLY_FAILED, DEADLINE_EXPIRED_CODE, now);
            return;
        }
        nextAttemptAt = policy.nextAttempt(attempts, now, deadlineAt);
    }

    /**
     * Reserva (lease) do despacho: empurra a próxima tentativa para o futuro; se o despachante
     * morrer, a ação volta a ser devida quando o lease vence. Só age sobre {@code PENDENTE}.
     */
    public void reserveUntil(Instant leaseUntil) {
        if (state == ChannelActionState.PENDING) {
            nextAttemptAt = leaseUntil;
        }
    }

    /** Aceite HTTP do orquestrador. Só age sobre {@code PENDENTE}. */
    public void markDelivered(Instant now) {
        if (state != ChannelActionState.PENDING) {
            return;
        }
        state = ChannelActionState.DELIVERED;
        deliveredAt = now;
        nextAttemptAt = null;
        attempts++;
    }

    public ConfirmationResult confirmExecuted(Instant now) {
        if (state == ChannelActionState.EXECUTED) {
            return ConfirmationResult.REPEATED;
        }
        if (state.finalized()) {
            return ConfirmationResult.DIVERGENT;
        }
        finalize(ChannelActionState.EXECUTED, null, now);
        return ConfirmationResult.APPLIED;
    }

    public ConfirmationResult confirmFailure(String code, Instant now) {
        Objects.requireNonNull(code);
        if (state == ChannelActionState.FAILED) {
            return code.equals(reasonCode)
                    ? ConfirmationResult.REPEATED
                    : ConfirmationResult.DIVERGENT;
        }
        if (state.finalized()) {
            return ConfirmationResult.DIVERGENT;
        }
        finalize(ChannelActionState.FAILED, code, now);
        return ConfirmationResult.APPLIED;
    }

    /** Descarte por vínculo desfeito: só age sobre {@code PENDENTE}. */
    public void discard(Instant now) {
        if (state == ChannelActionState.PENDING) {
            finalize(ChannelActionState.DISCARDED, LINK_UNDONE_CODE, now);
        }
    }

    /** Encerra uma ação não confirmada cujo prazo venceu. */
    public void expire(Instant now) {
        if (expired(now)) {
            finalize(ChannelActionState.PERMANENTLY_FAILED, DEADLINE_EXPIRED_CODE, now);
        }
    }

    /**
     * Encerra uma ação {@code ENTREGUE} que o orquestrador nunca confirmou até o prazo (FR-029):
     * estado final, sem reentrega, para que o conteúdo entre na retenção. Não é falha de entrega e
     * por isso não conta como falha definitiva nas métricas. Só age sobre {@code ENTREGUE}.
     */
    public boolean expireWithoutConfirmation(Instant now) {
        if (state != ChannelActionState.DELIVERED || !expired(now)) {
            return false;
        }
        finalize(ChannelActionState.PERMANENTLY_FAILED, CODE_WITHOUT_CONFIRMATION, now);
        return true;
    }

    public boolean expired(Instant now) {
        return !state.finalized() && !now.isBefore(deadlineAt);
    }

    // ---- compensação

    /** Só ações que não são elas mesmas compensação e que têm alternativa gravada. */
    public boolean hasCompensation() {
        return origin != ChannelActionOrigin.COMPENSATION && compensation != null;
    }

    /**
     * A falha informada pelo orquestrador pede a compensação desta ação: edição ou etapa que não
     * pôde ser editada ({@code MESSAGE_NOT_EDITABLE}), documento que não pôde ser buscado ({@code
     * DOCUMENT_UNAVAILABLE}) ou mensagem que não pôde ser apagada ({@code MESSAGE_NOT_DELETABLE}),
     * desde que haja alternativa gravada (FR-007b, FR-007c, FR-036).
     */
    public boolean compensatesBy(String code) {
        if (!hasCompensation()) {
            return false;
        }
        return switch (type) {
            case DOCUMENT -> DOCUMENT_UNAVAILABLE_CODE.equals(code);
            case DELETE_MESSAGE -> MESSAGE_NOT_DELETABLE_CODE.equals(code);
            case MESSAGE, EDIT, CALLBACK_REPLY, STEP_MESSAGE ->
                    MESSAGE_NOT_EDITABLE_CODE.equals(code);
        };
    }

    public String compensationId() {
        return id + COMPENSATION_SUFFIX;
    }

    /**
     * Ação alternativa para quando esta não pôde ser executada: no máximo uma por ação (id {@code
     * <id>-c}), no mesmo chat e com o mesmo professor/trabalho.
     */
    public ChannelAction createCompensation(Instant now, RetryPolicy policy) {
        if (!hasCompensation()) {
            throw new IllegalStateException("ação sem compensação");
        }
        ChannelActionType compensationType =
                type == ChannelActionType.STEP_MESSAGE
                        ? ChannelActionType.STEP_MESSAGE
                        : ChannelActionType.MESSAGE;
        return create(
                compensationId(),
                "c:" + id,
                ChannelActionOrigin.COMPENSATION,
                null,
                chatId,
                teacherId,
                jobId,
                compensationType,
                compensation,
                null,
                now,
                policy);
    }

    /** Retenção (R-11): o conteúdo some depois do prazo, o registro fica para auditoria. */
    public void resetPayload() {
        payload = null;
        compensation = null;
    }

    private void finalize(ChannelActionState created, String code, Instant now) {
        state = created;
        reasonCode = code;
        finalizedAt = now;
        nextAttemptAt = null;
    }

    // ---- consulta

    public String id() {
        return id;
    }

    public String batchId() {
        return batchId;
    }

    public Channel channel() {
        return channel;
    }

    public ChannelActionOrigin origin() {
        return origin;
    }

    public Long updateId() {
        return updateId;
    }

    public long chatId() {
        return chatId;
    }

    public UUID teacherId() {
        return teacherId;
    }

    public UUID jobId() {
        return jobId;
    }

    public ChannelActionType type() {
        return type;
    }

    public String payload() {
        return payload;
    }

    public String compensation() {
        return compensation;
    }

    public ChannelActionState state() {
        return state;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant deadlineAt() {
        return deadlineAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public Instant finalizedAt() {
        return finalizedAt;
    }

    public String reasonCode() {
        return reasonCode;
    }

    /** Versão lida do armazenamento (controle otimista). */
    public int version() {
        return version;
    }

    /** Chamado pelo repositório depois de uma gravação condicional bem-sucedida. */
    public void defineVersion(int newVersion) {
        this.version = newVersion;
    }
}
