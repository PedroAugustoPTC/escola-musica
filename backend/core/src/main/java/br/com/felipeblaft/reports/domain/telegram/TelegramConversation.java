package br.com.felipeblaft.reports.domain.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado da conversa do Telegram, persistido junto do vínculo (FR-036): aluno selecionado, fluxo e
 * etapa, dados curtos da etapa, áudio pendente e relatório em foco. O adaptador não guarda nada em
 * memória; tudo o que decide a próxima mensagem está aqui.
 */
public class TelegramConversation {

    /**
     * Chave dos dados do fluxo com a confirmação pendente (aprovar, cancelar, descartar, sair…).
     */
    public static final String CONFIRMATION = "confirmation";

    private UUID selectedStudentId;
    private ConversationFlow flow;
    private ConversationFlowStep step;
    private final Map<String, String> data;
    private PendingAudio pendingAudio;
    private FocusReport focusReport;
    private Integer presentedVersion;

    public TelegramConversation(
            UUID selectedStudentId,
            ConversationFlow flow,
            ConversationFlowStep step,
            Map<String, String> data,
            PendingAudio pendingAudio,
            FocusReport focusReport,
            Integer presentedVersion) {
        this.selectedStudentId = selectedStudentId;
        this.flow = flow == null ? ConversationFlow.NENHUM : flow;
        this.step = step == null ? ConversationFlowStep.NENHUMA : step;
        this.data = new LinkedHashMap<>(data == null ? Map.of() : data);
        this.pendingAudio = pendingAudio;
        this.focusReport = focusReport;
        this.presentedVersion = presentedVersion;
    }

    public static TelegramConversation empty() {
        return new TelegramConversation(null, null, null, null, null, null, null);
    }

    // ---- Seleção de aluno

    public void selectStudent(UUID studentId) {
        this.selectedStudentId = studentId;
    }

    /** Limpa a seleção de aluno (FR-038: após aprovar ou cancelar). */
    public void cleanSelection() {
        this.selectedStudentId = null;
    }

    // ---- Fluxo e etapa

    /** Começa um fluxo; descarta dados e confirmações da etapa anterior. */
    public void startFlow(ConversationFlow newFlow, ConversationFlowStep newStep) {
        this.flow = newFlow;
        this.step = newStep;
        this.data.clear();
    }

    public void defineStep(ConversationFlowStep newStep) {
        this.step = newStep;
    }

    public void endFlow() {
        startFlow(ConversationFlow.NENHUM, ConversationFlowStep.NENHUMA);
    }

    public void store(String key, String value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
    }

    public Optional<String> data(String key) {
        return Optional.ofNullable(data.get(key));
    }

    public void askConfirmation(String action) {
        store(CONFIRMATION, action);
    }

    public Optional<String> pendingConfirmation() {
        return data(CONFIRMATION);
    }

    public void cleanConfirmation() {
        data.remove(CONFIRMATION);
    }

    // ---- Áudio pendente (FR-040)

    /** Um novo áudio substitui o anterior. */
    public void receiveAudio(PendingAudio audio) {
        this.pendingAudio = audio;
    }

    public Optional<PendingAudio> pendingAudio() {
        return Optional.ofNullable(pendingAudio);
    }

    /** Descarta o áudio se expirou; devolve {@code true} se descartou agora. */
    public boolean discardAudioIfExpired(Instant now, Duration expiry) {
        if (pendingAudio != null && pendingAudio.expiredAt(now, expiry)) {
            pendingAudio = null;
            return true;
        }
        return false;
    }

    public void discardAudio() {
        this.pendingAudio = null;
    }

    // ---- Relatório em foco (FR-027b)

    public void focus(FocusReport focus, Integer version) {
        this.focusReport = focus;
        this.presentedVersion = version;
    }

    public void registerPresentedVersion(Integer version) {
        this.presentedVersion = version;
    }

    /** "Registrar aula" limpa o foco sem cancelar o relatório. */
    public void cleanFocus() {
        this.focusReport = null;
        this.presentedVersion = null;
    }

    /** Estado inicial (após "sair" ou ao voltar ao início). */
    public void cleanEverything() {
        cleanSelection();
        endFlow();
        discardAudio();
        cleanFocus();
    }

    public UUID selectedStudentId() {
        return selectedStudentId;
    }

    public ConversationFlow flow() {
        return flow;
    }

    public ConversationFlowStep step() {
        return step;
    }

    public Map<String, String> data() {
        return Map.copyOf(data);
    }

    public Optional<FocusReport> focusReport() {
        return Optional.ofNullable(focusReport);
    }

    public Integer presentedVersion() {
        return presentedVersion;
    }
}
