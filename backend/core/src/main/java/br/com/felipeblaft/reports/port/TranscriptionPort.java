package br.com.felipeblaft.reports.port;

/**
 * Transcrição de áudio (contracts/portas-provedores.md §1). Texto vazio → {@link
 * AudioNotProcessableException} (SEM_FALA); arquivo recusado → FORMATO; indisponibilidade → {@link
 * ProviderUnavailableException}. A porta não guarda o áudio.
 */
public interface TranscriptionPort {

    TranscriptionResult transcribe(NormalizedAudio audio);
}
