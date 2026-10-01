package br.com.felipeblaft.relatorios.porta;

/**
 * Transcrição de áudio (contracts/portas-provedores.md §1). Texto vazio →
 * {@link AudioNaoProcessavelExcecao} (SEM_FALA); arquivo recusado → FORMATO; indisponibilidade →
 * {@link ProvedorIndisponivelExcecao}. A porta não guarda o áudio.
 */
public interface TranscricaoPorta {

    ResultadoTranscricao transcrever(AudioNormalizado audio);
}
