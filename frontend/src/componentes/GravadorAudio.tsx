import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react';
import { msg } from '../mensagens/mensagens';
import type { AudioSelecionado } from '../servicos/relatoriosAula';
import { Botao } from './Botao';
import { Icone } from './Icone';

type EstadoGravacao = 'ocioso' | 'gravando' | 'concluida';

/** Tipos aceitos no seletor de arquivo (contrato: MP3, M4A, OGG/Opus, WAV, WebM ou vídeo MP4). */
export const ACEITOS = 'audio/*,video/mp4,video/webm,.mp3,.m4a,.ogg,.oga,.opus,.wav,.webm,.mp4';

/** Formato de gravação: Opus em WebM; no Safari, MP4/AAC. */
export function formatoGravacao(): { mime: string; extensao: string } {
  const suporta = (tipo: string) =>
    typeof MediaRecorder !== 'undefined' &&
    typeof MediaRecorder.isTypeSupported === 'function' &&
    MediaRecorder.isTypeSupported(tipo);
  if (suporta('audio/webm;codecs=opus')) return { mime: 'audio/webm;codecs=opus', extensao: 'webm' };
  if (suporta('audio/mp4')) return { mime: 'audio/mp4', extensao: 'm4a' };
  return { mime: '', extensao: 'webm' };
}

export function gravacaoDisponivel(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof MediaRecorder !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    typeof navigator.mediaDevices?.getUserMedia === 'function'
  );
}

function formatarTempo(segundos: number): string {
  const minutos = Math.floor(segundos / 60);
  const resto = segundos % 60;
  return `${String(minutos).padStart(2, '0')}:${String(resto).padStart(2, '0')}`;
}

function formatarTamanho(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}

/**
 * Gravador do portal (telas 05, 05b–05e, 07 e 08): grava com MediaRecorder, permite ouvir, regravar
 * e escolher um arquivo. O limite de tamanho é conferido antes do envio; sem suporte a gravação ou
 * com o microfone bloqueado, sobra só o envio de arquivo (`GRAVACAO_INDISPONIVEL`). Quem usa recebe
 * o áudio pronto em `aoMudar` e decide quando enviar.
 */
export function GravadorAudio({
  limiteMb,
  aoMudar,
  desabilitado = false,
  compacto = false,
  dica,
}: {
  limiteMb: number;
  aoMudar: (audio: AudioSelecionado | null) => void;
  desabilitado?: boolean;
  compacto?: boolean;
  dica?: string;
}) {
  const [estado, setEstado] = useState<EstadoGravacao>('ocioso');
  const [indisponivel, setIndisponivel] = useState(() => !gravacaoDisponivel());
  const [segundos, setSegundos] = useState(0);
  const [gravacao, setGravacao] = useState<AudioSelecionado | null>(null);
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [tocando, setTocando] = useState(false);
  const gravador = useRef<MediaRecorder | null>(null);
  const fluxo = useRef<MediaStream | null>(null);
  const partes = useRef<Blob[]>([]);
  const relogio = useRef<number | null>(null);
  const reprodutor = useRef<HTMLAudioElement | null>(null);
  const urlReproducao = useRef<string | null>(null);
  const entradaArquivo = useRef<HTMLInputElement>(null);
  const idAjuda = useId();
  const limiteBytes = limiteMb * 1024 * 1024;

  const pararRelogio = () => {
    if (relogio.current !== null) {
      window.clearInterval(relogio.current);
      relogio.current = null;
    }
  };

  const liberarMicrofone = () => {
    fluxo.current?.getTracks().forEach((faixa) => faixa.stop());
    fluxo.current = null;
  };

  const pararReproducao = () => {
    reprodutor.current?.pause();
    reprodutor.current = null;
    if (urlReproducao.current) {
      URL.revokeObjectURL(urlReproducao.current);
      urlReproducao.current = null;
    }
    setTocando(false);
  };

  useEffect(
    () => () => {
      pararRelogio();
      liberarMicrofone();
      pararReproducao();
      if (gravador.current?.state === 'recording') gravador.current.stop();
    },
    [],
  );

  const selecionar = (audio: AudioSelecionado | null) => {
    if (audio && audio.arquivo.size > limiteBytes) {
      setErro(msg('AUDIO_TOO_LARGE'));
      aoMudar(null);
      return false;
    }
    setErro(null);
    aoMudar(audio);
    return true;
  };

  const iniciar = async () => {
    setErro(null);
    let midia: MediaStream;
    try {
      midia = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      setIndisponivel(true);
      return;
    }
    const formato = formatoGravacao();
    let novo: MediaRecorder;
    try {
      novo = formato.mime ? new MediaRecorder(midia, { mimeType: formato.mime }) : new MediaRecorder(midia);
    } catch {
      midia.getTracks().forEach((faixa) => faixa.stop());
      setIndisponivel(true);
      return;
    }
    fluxo.current = midia;
    partes.current = [];
    novo.ondataavailable = (evento: BlobEvent) => {
      if (evento.data && evento.data.size > 0) partes.current.push(evento.data);
    };
    novo.onstop = () => {
      liberarMicrofone();
      const tipo = novo.mimeType || formato.mime || 'audio/webm';
      const blob = new Blob(partes.current, { type: tipo });
      const audio = { arquivo: blob, nome: `gravacao.${formato.extensao}` };
      setGravacao(audio);
      setEstado('concluida');
      selecionar(audio);
    };
    gravador.current = novo;
    novo.start();
    setSegundos(0);
    setEstado('gravando');
    setArquivo(null);
    relogio.current = window.setInterval(() => setSegundos((s) => s + 1), 1000);
  };

  const parar = () => {
    pararRelogio();
    if (gravador.current?.state === 'recording') gravador.current.stop();
  };

  const regravar = () => {
    pararReproducao();
    setGravacao(null);
    setSegundos(0);
    setEstado('ocioso');
    selecionar(null);
  };

  const ouvir = () => {
    if (!gravacao) return;
    if (tocando) {
      pararReproducao();
      return;
    }
    const url = URL.createObjectURL(gravacao.arquivo);
    urlReproducao.current = url;
    const audio = new Audio(url);
    reprodutor.current = audio;
    audio.onended = () => pararReproducao();
    setTocando(true);
    void audio.play().catch(() => pararReproducao());
  };

  const escolherArquivo = (evento: ChangeEvent<HTMLInputElement>) => {
    const escolhido = evento.target.files?.[0] ?? null;
    evento.target.value = '';
    if (!escolhido) return;
    if (escolhido.size > limiteBytes) {
      setArquivo(null);
      setErro(msg('AUDIO_TOO_LARGE'));
      aoMudar(null);
      return;
    }
    pararReproducao();
    setGravacao(null);
    setEstado('ocioso');
    setArquivo(escolhido);
    selecionar({ arquivo: escolhido, nome: escolhido.name });
  };

  const removerArquivo = () => {
    setArquivo(null);
    selecionar(null);
  };

  const gravando = estado === 'gravando';
  const concluida = estado === 'concluida';

  return (
    <div className={`gravador${compacto ? ' gravador--compacto' : ''}`}>
      {indisponivel ? (
        <div role="alert" className="alerta alerta--info">
          <Icone nome="mic_off" />
          <div className="alerta__texto">{msg('RECORDING_UNAVAILABLE')}</div>
        </div>
      ) : (
        <>
          <div className="gravador__painel">
            <div aria-live="polite" className="gravador__status">
              <div className="gravador__tempo">{formatarTempo(segundos)}</div>
              <div className="gravador__situacao">
                {gravando ? <span className="gravador__ponto" aria-hidden="true" /> : null}
                {gravando ? 'Gravando…' : concluida ? 'Gravação concluída' : 'Pronto para gravar'}
              </div>
            </div>
            <button
              type="button"
              className={`gravador__botao${gravando ? ' gravador__botao--gravando' : ''}`}
              aria-label={gravando ? 'Parar gravação' : concluida ? 'Gravar de novo' : 'Começar a gravar'}
              aria-pressed={gravando}
              disabled={desabilitado}
              onClick={gravando ? parar : concluida ? regravar : () => void iniciar()}
            >
              <Icone nome={gravando ? 'stop' : 'mic'} tamanho="grande" />
            </button>
            {concluida ? (
              <div className="gravador__acoes">
                <Botao variante="secundario" icone={tocando ? 'pause' : 'play_arrow'} onClick={ouvir}>
                  {tocando ? 'Pausar' : 'Ouvir'}
                </Botao>
                <Botao variante="secundario" icone="replay" onClick={regravar} disabled={desabilitado}>
                  Regravar
                </Botao>
              </div>
            ) : null}
            {dica && !compacto ? <p className="gravador__dica">{dica}</p> : null}
          </div>
          <div className="separador-ou" aria-hidden="true">
            <span />
            OU
            <span />
          </div>
        </>
      )}
      {arquivo ? (
        <div className="gravador__arquivo">
          <Icone nome="audio_file" />
          <div className="gravador__arquivo-texto">
            <div className="gravador__arquivo-nome">{arquivo.name}</div>
            <div className="texto-apoio">{formatarTamanho(arquivo.size)}</div>
          </div>
          <Botao
            variante="icone"
            aria-label="Remover arquivo"
            onClick={removerArquivo}
            disabled={desabilitado}
          >
            <Icone nome="close" />
          </Botao>
        </div>
      ) : (
        <>
          <button
            type="button"
            className="gravador__enviar-arquivo"
            aria-describedby={idAjuda}
            disabled={desabilitado || gravando}
            onClick={() => entradaArquivo.current?.click()}
          >
            <span className="gravador__enviar-rotulo">
              <Icone nome="upload" />
              Enviar arquivo de áudio
            </span>
            <span id={idAjuda} className="texto-apoio">
              {msg('FORMATS_AND_LIMIT', { limitMb: limiteMb })}
            </span>
          </button>
          <input
            ref={entradaArquivo}
            type="file"
            accept={ACEITOS}
            className="oculto-visual"
            tabIndex={-1}
            aria-label="Arquivo de áudio"
            onChange={escolherArquivo}
          />
        </>
      )}
      {erro ? (
        <div role="alert" className="alerta alerta--erro">
          <Icone nome="error" />
          <div className="alerta__texto">{erro}</div>
        </div>
      ) : null}
    </div>
  );
}
