import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GravadorAudio } from '../../src/componentes/GravadorAudio';

/** MediaRecorder simulado: `stop` entrega um pedaço de áudio e dispara `onstop`. */
class GravadorFalso {
  static isTypeSupported = (tipo: string) => tipo === 'audio/webm;codecs=opus';
  state: 'inactive' | 'recording' = 'inactive';
  mimeType = 'audio/webm;codecs=opus';
  ondataavailable: ((evento: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;
  start() {
    this.state = 'recording';
  }
  stop() {
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob(['audio'], { type: this.mimeType }) });
    this.onstop?.();
  }
}

function simularMicrofone(permitir = true) {
  const parar = vi.fn();
  vi.stubGlobal('MediaRecorder', GravadorFalso);
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: {
      getUserMedia: permitir
        ? vi.fn(async () => ({ getTracks: () => [{ stop: parar }] }))
        : vi.fn(async () => {
            throw new DOMException('negado', 'NotAllowedError');
          }),
    },
  });
  return { parar };
}

afterEach(() => {
  vi.unstubAllGlobals();
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
});

describe('GravadorAudio', () => {
  it('grava, mostra ouvir e regravar e entrega o áudio só quando concluído', async () => {
    const { parar } = simularMicrofone();
    const aoMudar = vi.fn();
    const usuario = userEvent.setup();
    render(<GravadorAudio limiteMb={50} aoMudar={aoMudar} />);

    expect(screen.getByText('Pronto para gravar')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Começar a gravar' }));
    expect(await screen.findByText('Gravando…')).toBeInTheDocument();
    expect(aoMudar).not.toHaveBeenCalled();

    await usuario.click(screen.getByRole('button', { name: 'Parar gravação' }));

    expect(await screen.findByText('Gravação concluída')).toBeInTheDocument();
    expect(parar).toHaveBeenCalled();
    expect(aoMudar).toHaveBeenLastCalledWith(
      expect.objectContaining({ nome: 'gravacao.webm', arquivo: expect.any(Blob) }),
    );
    expect(screen.getByRole('button', { name: 'Ouvir' })).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Regravar' }));
    expect(aoMudar).toHaveBeenLastCalledWith(null);
    expect(screen.getByText('Pronto para gravar')).toBeInTheDocument();
  });

  it('sem suporte a gravação sobra só o envio de arquivo (GRAVACAO_INDISPONIVEL)', async () => {
    const aoMudar = vi.fn();
    const usuario = userEvent.setup();
    render(<GravadorAudio limiteMb={50} aoMudar={aoMudar} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Gravação indisponível neste navegador');
    expect(screen.queryByRole('button', { name: 'Começar a gravar' })).not.toBeInTheDocument();

    const arquivo = new File(['abc'], 'aula.m4a', { type: 'audio/mp4' });
    await usuario.upload(screen.getByLabelText('Arquivo de áudio'), arquivo);

    expect(aoMudar).toHaveBeenLastCalledWith({ arquivo, nome: 'aula.m4a' });
    expect(screen.getByText('aula.m4a')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Remover arquivo' }));
    expect(aoMudar).toHaveBeenLastCalledWith(null);
  });

  it('microfone negado mostra o aviso e mantém o envio de arquivo', async () => {
    simularMicrofone(false);
    const usuario = userEvent.setup();
    render(<GravadorAudio limiteMb={50} aoMudar={vi.fn()} />);

    await usuario.click(screen.getByRole('button', { name: 'Começar a gravar' }));

    expect(await screen.findByText(/Gravação indisponível neste navegador/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Enviar arquivo de áudio/ })).toBeInTheDocument();
  });

  it('arquivo acima do limite é recusado antes do envio', async () => {
    const aoMudar = vi.fn();
    const usuario = userEvent.setup();
    render(<GravadorAudio limiteMb={1} aoMudar={aoMudar} />);

    const grande = new File([new Uint8Array(2 * 1024 * 1024)], 'longa.mp3', { type: 'audio/mpeg' });
    await usuario.upload(screen.getByLabelText('Arquivo de áudio'), grande);

    expect(
      screen.getByText('O áudio enviado é maior que o limite permitido. Grave um trecho mais curto.'),
    ).toBeInTheDocument();
    expect(aoMudar).toHaveBeenLastCalledWith(null);
    expect(screen.queryByText('longa.mp3')).not.toBeInTheDocument();
  });
});
