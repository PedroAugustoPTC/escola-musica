package br.com.felipeblaft.relatorios.dominio.telegram;

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
public class ConversaTelegram {

    /**
     * Chave dos dados do fluxo com a confirmação pendente (aprovar, cancelar, descartar, sair…).
     */
    public static final String CONFIRMACAO = "confirmacao";

    private UUID alunoSelecionadoId;
    private FluxoConversa fluxo;
    private EtapaFluxoConversa etapa;
    private final Map<String, String> dados;
    private AudioPendente audioPendente;
    private RelatorioFoco relatorioFoco;
    private Integer versaoApresentada;

    public ConversaTelegram(
            UUID alunoSelecionadoId,
            FluxoConversa fluxo,
            EtapaFluxoConversa etapa,
            Map<String, String> dados,
            AudioPendente audioPendente,
            RelatorioFoco relatorioFoco,
            Integer versaoApresentada) {
        this.alunoSelecionadoId = alunoSelecionadoId;
        this.fluxo = fluxo == null ? FluxoConversa.NENHUM : fluxo;
        this.etapa = etapa == null ? EtapaFluxoConversa.NENHUMA : etapa;
        this.dados = new LinkedHashMap<>(dados == null ? Map.of() : dados);
        this.audioPendente = audioPendente;
        this.relatorioFoco = relatorioFoco;
        this.versaoApresentada = versaoApresentada;
    }

    public static ConversaTelegram vazia() {
        return new ConversaTelegram(null, null, null, null, null, null, null);
    }

    // ---- Seleção de aluno

    public void selecionarAluno(UUID alunoId) {
        this.alunoSelecionadoId = alunoId;
    }

    /** Limpa a seleção de aluno (FR-038: após aprovar ou cancelar). */
    public void limparSelecao() {
        this.alunoSelecionadoId = null;
    }

    // ---- Fluxo e etapa

    /** Começa um fluxo; descarta dados e confirmações da etapa anterior. */
    public void iniciarFluxo(FluxoConversa novoFluxo, EtapaFluxoConversa novaEtapa) {
        this.fluxo = novoFluxo;
        this.etapa = novaEtapa;
        this.dados.clear();
    }

    public void definirEtapa(EtapaFluxoConversa novaEtapa) {
        this.etapa = novaEtapa;
    }

    public void encerrarFluxo() {
        iniciarFluxo(FluxoConversa.NENHUM, EtapaFluxoConversa.NENHUMA);
    }

    public void guardar(String chave, String valor) {
        if (valor == null) {
            dados.remove(chave);
        } else {
            dados.put(chave, valor);
        }
    }

    public Optional<String> dado(String chave) {
        return Optional.ofNullable(dados.get(chave));
    }

    public void pedirConfirmacao(String acao) {
        guardar(CONFIRMACAO, acao);
    }

    public Optional<String> confirmacaoPendente() {
        return dado(CONFIRMACAO);
    }

    public void limparConfirmacao() {
        dados.remove(CONFIRMACAO);
    }

    // ---- Áudio pendente (FR-040)

    /** Um novo áudio substitui o anterior. */
    public void receberAudio(AudioPendente audio) {
        this.audioPendente = audio;
    }

    public Optional<AudioPendente> audioPendente() {
        return Optional.ofNullable(audioPendente);
    }

    /** Descarta o áudio se expirou; devolve {@code true} se descartou agora. */
    public boolean descartarAudioSeExpirado(Instant agora, Duration expiracao) {
        if (audioPendente != null && audioPendente.expiradoEm(agora, expiracao)) {
            audioPendente = null;
            return true;
        }
        return false;
    }

    public void descartarAudio() {
        this.audioPendente = null;
    }

    // ---- Relatório em foco (FR-027b)

    public void focar(RelatorioFoco foco, Integer versao) {
        this.relatorioFoco = foco;
        this.versaoApresentada = versao;
    }

    public void registrarVersaoApresentada(Integer versao) {
        this.versaoApresentada = versao;
    }

    /** "Registrar aula" limpa o foco sem cancelar o relatório. */
    public void limparFoco() {
        this.relatorioFoco = null;
        this.versaoApresentada = null;
    }

    /** Estado inicial (após "sair" ou ao voltar ao início). */
    public void limparTudo() {
        limparSelecao();
        encerrarFluxo();
        descartarAudio();
        limparFoco();
    }

    public UUID alunoSelecionadoId() {
        return alunoSelecionadoId;
    }

    public FluxoConversa fluxo() {
        return fluxo;
    }

    public EtapaFluxoConversa etapa() {
        return etapa;
    }

    public Map<String, String> dados() {
        return Map.copyOf(dados);
    }

    public Optional<RelatorioFoco> relatorioFoco() {
        return Optional.ofNullable(relatorioFoco);
    }

    public Integer versaoApresentada() {
        return versaoApresentada;
    }
}
