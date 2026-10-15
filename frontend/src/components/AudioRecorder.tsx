import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react';
import { msg } from '../messages/messages';
import type { SelectedAudio } from '../services/lessonReports';
import { Button } from './Button';
import { Icon } from './Icon';

type RecordingState = 'idle' | 'recording' | 'completed';

/** Tipos aceitos no seletor de arquivo (contrato: MP3, M4A, OGG/Opus, WAV, WebM ou vídeo MP4). */
export const ACCEPTED = 'audio/*,video/mp4,video/webm,.mp3,.m4a,.ogg,.oga,.opus,.wav,.webm,.mp4';

/** Formato de gravação: Opus em WebM; no Safari, MP4/AAC. */
export function recordingFormat(): { mime: string; extension: string } {
  const supports = (type: string) =>
    typeof MediaRecorder !== 'undefined' &&
    typeof MediaRecorder.isTypeSupported === 'function' &&
    MediaRecorder.isTypeSupported(type);
  if (supports('audio/webm;codecs=opus')) return { mime: 'audio/webm;codecs=opus', extension: 'webm' };
  if (supports('audio/mp4')) return { mime: 'audio/mp4', extension: 'm4a' };
  return { mime: '', extension: 'webm' };
}

export function recordingAvailable(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof MediaRecorder !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function'
  );
}

function formatTime(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}

function formatSize(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}

/**
 * Gravador do portal (telas 05, 05b–05e, 07 e 08): grava com MediaRecorder, permite ouvir, regravar
 * e escolher um arquivo. O limite de tamanho é conferido antes do envio; sem suporte a gravação ou
 * com o microfone bloqueado, sobra só o envio de arquivo (`GRAVACAO_INDISPONIVEL`). Quem usa recebe
 * o áudio pronto em `onChange` e decide quando enviar.
 */
export function AudioRecorder({
  limitMb,
  onChange,
  disabled = false,
  compact = false,
  tip,
}: {
  limitMb: number;
  onChange: (audio: SelectedAudio | null) => void;
  disabled?: boolean;
  compact?: boolean;
  tip?: string;
}) {
  const [state, setState] = useState<RecordingState>('idle');
  const [unavailable, setUnavailable] = useState(() => !recordingAvailable());
  const [seconds, setSeconds] = useState(0);
  const [recording, setRecording] = useState<SelectedAudio | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [playing, setPlaying] = useState(false);
  const recorder = useRef<MediaRecorder | null>(null);
  const flow = useRef<MediaStream | null>(null);
  const parts = useRef<Blob[]>([]);
  const clock = useRef<number | null>(null);
  const player = useRef<HTMLAudioElement | null>(null);
  const playbackUrl = useRef<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const helpId = useId();
  const limitBytes = limitMb * 1024 * 1024;

  const stopClock = () => {
    if (clock.current !== null) {
      window.clearInterval(clock.current);
      clock.current = null;
    }
  };

  const releaseMicrophone = () => {
    flow.current?.getTracks().forEach((track) => track.stop());
    flow.current = null;
  };

  const stopPlayback = () => {
    player.current?.pause();
    player.current = null;
    if (playbackUrl.current) {
      URL.revokeObjectURL(playbackUrl.current);
      playbackUrl.current = null;
    }
    setPlaying(false);
  };

  useEffect(
    () => () => {
      stopClock();
      releaseMicrophone();
      stopPlayback();
      if (recorder.current?.state === 'recording') recorder.current.stop();
    },
    [],
  );

  const select = (audio: SelectedAudio | null) => {
    if (audio && audio.file.size > limitBytes) {
      setError(msg('AUDIO_TOO_LARGE'));
      onChange(null);
      return false;
    }
    setError(null);
    onChange(audio);
    return true;
  };

  const start = async () => {
    setError(null);
    let media: MediaStream;
    try {
      media = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      setUnavailable(true);
      return;
    }
    const format = recordingFormat();
    let nextValue: MediaRecorder;
    try {
      nextValue = format.mime
        ? new MediaRecorder(media, { mimeType: format.mime })
        : new MediaRecorder(media);
    } catch {
      media.getTracks().forEach((track) => track.stop());
      setUnavailable(true);
      return;
    }
    flow.current = media;
    parts.current = [];
    nextValue.ondataavailable = (event: BlobEvent) => {
      if (event.data && event.data.size > 0) parts.current.push(event.data);
    };
    nextValue.onstop = () => {
      releaseMicrophone();
      const type = nextValue.mimeType || format.mime || 'audio/webm';
      const blob = new Blob(parts.current, { type });
      const audio = { file: blob, name: `recording.${format.extension}` };
      setRecording(audio);
      setState('completed');
      select(audio);
    };
    recorder.current = nextValue;
    nextValue.start();
    setSeconds(0);
    setState('recording');
    setFile(null);
    clock.current = window.setInterval(() => setSeconds((s) => s + 1), 1000);
  };

  const stop = () => {
    stopClock();
    if (recorder.current?.state === 'recording') recorder.current.stop();
  };

  const rerecord = () => {
    stopPlayback();
    setRecording(null);
    setSeconds(0);
    setState('idle');
    select(null);
  };

  const listen = () => {
    if (!recording) return;
    if (playing) {
      stopPlayback();
      return;
    }
    const url = URL.createObjectURL(recording.file);
    playbackUrl.current = url;
    const audio = new Audio(url);
    player.current = audio;
    audio.onended = () => stopPlayback();
    setPlaying(true);
    void audio.play().catch(() => stopPlayback());
  };

  const chooseFile = (event: ChangeEvent<HTMLInputElement>) => {
    const chosen = event.target.files?.[0] ?? null;
    event.target.value = '';
    if (!chosen) return;
    if (chosen.size > limitBytes) {
      setFile(null);
      setError(msg('AUDIO_TOO_LARGE'));
      onChange(null);
      return;
    }
    stopPlayback();
    setRecording(null);
    setState('idle');
    setFile(chosen);
    select({ file: chosen, name: chosen.name });
  };

  const removeFile = () => {
    setFile(null);
    select(null);
  };

  const isRecording = state === 'recording';
  const completed = state === 'completed';

  return (
    <div className={`recorder${compact ? ' recorder--compact' : ''}`}>
      {unavailable ? (
        <div role="alert" className="alert alert--info">
          <Icon name="mic_off" />
          <div className="alert__text">{msg('RECORDING_UNAVAILABLE')}</div>
        </div>
      ) : (
        <>
          <div className="recorder__panel">
            <div aria-live="polite" className="recorder__status">
              <div className="recorder__time">{formatTime(seconds)}</div>
              <div className="recorder__state">
                {isRecording ? <span className="recorder__dot" aria-hidden="true" /> : null}
                {isRecording ? 'Gravando…' : completed ? 'Gravação concluída' : 'Pronto para gravar'}
              </div>
            </div>
            <button
              type="button"
              className={`recorder__button${isRecording ? ' recorder__button--recording' : ''}`}
              aria-label={isRecording ? 'Parar gravação' : completed ? 'Gravar de novo' : 'Começar a gravar'}
              aria-pressed={isRecording}
              disabled={disabled}
              onClick={isRecording ? stop : completed ? rerecord : () => void start()}
            >
              <Icon name={isRecording ? 'stop' : 'mic'} size="large" />
            </button>
            {completed ? (
              <div className="recorder__actions">
                <Button variant="secondary" icon={playing ? 'pause' : 'play_arrow'} onClick={listen}>
                  {playing ? 'Pausar' : 'Ouvir'}
                </Button>
                <Button variant="secondary" icon="replay" onClick={rerecord} disabled={disabled}>
                  Regravar
                </Button>
              </div>
            ) : null}
            {tip && !compact ? <p className="recorder__tip">{tip}</p> : null}
          </div>
          <div className="or-separator" aria-hidden="true">
            <span />
            OU
            <span />
          </div>
        </>
      )}
      {file ? (
        <div className="recorder__file">
          <Icon name="audio_file" />
          <div className="recorder__file-text">
            <div className="recorder__file-name">{file.name}</div>
            <div className="support-text">{formatSize(file.size)}</div>
          </div>
          <Button variant="icon" aria-label="Remover arquivo" onClick={removeFile} disabled={disabled}>
            <Icon name="close" />
          </Button>
        </div>
      ) : (
        <>
          <button
            type="button"
            className="recorder__send-file"
            aria-describedby={helpId}
            disabled={disabled || isRecording}
            onClick={() => fileInput.current?.click()}
          >
            <span className="recorder__send-label">
              <Icon name="upload" />
              Enviar arquivo de áudio
            </span>
            <span id={helpId} className="support-text">
              {msg('FORMATS_AND_LIMIT', { limitMb })}
            </span>
          </button>
          <input
            ref={fileInput}
            type="file"
            accept={ACCEPTED}
            className="visually-hidden"
            tabIndex={-1}
            aria-label="Arquivo de áudio"
            onChange={chooseFile}
          />
        </>
      )}
      {error ? (
        <div role="alert" className="alert alert--error">
          <Icon name="error" />
          <div className="alert__text">{error}</div>
        </div>
      ) : null}
    </div>
  );
}
