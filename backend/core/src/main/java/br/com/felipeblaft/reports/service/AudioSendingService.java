package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.port.AudioInput;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.TemporaryFile;
import java.util.Map;
import java.util.Objects;

/**
 * Regras comuns a todo áudio enviado (registro, resposta e alteração): mesmo limite nos dois canais
 * (FR-015) e referência do áudio anexada ao trabalho sem copiar bytes.
 */
public class AudioSendingService {

    private final BusinessParameters parameters;
    private final TemporaryAudioService audio;

    public AudioSendingService(BusinessParameters parameters, TemporaryAudioService audio) {
        this.parameters = Objects.requireNonNull(parameters);
        this.audio = Objects.requireNonNull(audio);
    }

    public void requireWithinLimit(AudioInput input) {
        if (input.size() > parameters.audioLimitBytes()) {
            throw new DomainException(
                    ErrorCode.AUDIO_TOO_LARGE, Map.of("limitMb", parameters.audioLimitMb()));
        }
    }

    public void attach(ProcessingJob job, AudioInput input) {
        job.defineParameter(ProcessingJob.FILE_SIZE, Long.toString(input.size()));
        switch (input) {
            case TemporaryFile file -> {
                job.defineTemporaryFile(file.path().toString());
                job.defineParameter(ProcessingJob.FILE_MIME, file.mime());
            }
            case ChannelFileReference reference -> {
                job.defineParameter(ProcessingJob.CHANNEL_FILE, reference.id());
                job.defineParameter(ProcessingJob.UNIQUE_CHANNEL_FILE, reference.uniqueId());
                job.defineParameter(ProcessingJob.FILE_MIME, reference.mime());
            }
        }
    }

    /** Descarta o áudio de um envio recusado (FR-018). */
    public void discard(AudioInput input) {
        audio.discard(input);
    }
}
