package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.port.AudioInput;
import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.ChannelFilePort;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.port.TranscriptionResult;
import java.util.Objects;

/**
 * Obtém, normaliza e transcreve o áudio e apaga o original e o normalizado num {@code finally}, com
 * sucesso ou falha (FR-018). Diante de indisponibilidade do provedor, tenta de novo uma vez (R-08)
 * antes de apagar.
 */
public class TemporaryAudioService {

    private final ChannelFilePort channelFile;
    private final AudioNormalizationPort normalization;
    private final TranscriptionPort transcription;

    public TemporaryAudioService(
            ChannelFilePort channelFile,
            AudioNormalizationPort normalization,
            TranscriptionPort transcription) {
        this.channelFile = Objects.requireNonNull(channelFile);
        this.normalization = Objects.requireNonNull(normalization);
        this.transcription = Objects.requireNonNull(transcription);
    }

    public String providerName() {
        return transcription.providerName();
    }

    public TranscriptionResult transcribe(AudioInput input) {
        TemporaryFile original = null;
        NormalizedAudio normalized = null;
        try {
            original =
                    switch (input) {
                        case TemporaryFile file -> file;
                        case ChannelFileReference reference -> channelFile.getFile(reference);
                    };
            normalized = normalization.normalize(original);
            TranscriptionResult result;
            try {
                result = transcription.transcribe(normalized);
            } catch (ProviderUnavailableException firstFailure) {
                result = transcription.transcribe(normalized);
            }
            if (result.text() == null || result.text().isBlank()) {
                throw new AudioNotProcessableException(
                        AudioNotProcessableException.Reason.NO_SPEECH);
            }
            return result;
        } finally {
            if (normalized != null) {
                normalization.delete(normalized.path());
            }
            if (original != null) {
                normalization.delete(original.path());
            }
        }
    }

    /** Descarta um áudio que não será processado (ex.: o caso de uso recusou o envio). */
    public void discard(AudioInput input) {
        if (input instanceof TemporaryFile file) {
            normalization.delete(file.path());
        }
    }
}
