import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AudioRecorder } from '../../src/components/AudioRecorder';

/** MediaRecorder simulado: `stop` entrega um pedaço de áudio e dispara `onstop`. */
class FakeRecorder {
  static isTypeSupported = (type: string) => type === 'audio/webm;codecs=opus';
  state: 'inactive' | 'recording' = 'inactive';
  mimeType = 'audio/webm;codecs=opus';
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
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

function mockMicrophone(allow = true) {
  const stop = vi.fn();
  vi.stubGlobal('MediaRecorder', FakeRecorder);
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: {
      getUserMedia: allow
        ? vi.fn(async () => ({ getTracks: () => [{ stop }] }))
        : vi.fn(async () => {
            throw new DOMException('denied', 'NotAllowedError');
          }),
    },
  });
  return { stop };
}

afterEach(() => {
  vi.unstubAllGlobals();
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
});

describe('AudioRecorder', () => {
  it('grava, mostra ouvir e regravar e entrega o áudio só quando concluído', async () => {
    const { stop } = mockMicrophone();
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AudioRecorder limitMb={50} onChange={onChange} />);

    expect(screen.getByText('Pronto para gravar')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Começar a gravar' }));
    expect(await screen.findByText('Gravando…')).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Parar gravação' }));

    expect(await screen.findByText('Gravação concluída')).toBeInTheDocument();
    expect(stop).toHaveBeenCalled();
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: 'recording.webm', file: expect.any(Blob) }),
    );
    expect(screen.getByRole('button', { name: 'Ouvir' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Regravar' }));
    expect(onChange).toHaveBeenLastCalledWith(null);
    expect(screen.getByText('Pronto para gravar')).toBeInTheDocument();
  });

  it('sem suporte a gravação sobra só o envio de arquivo (GRAVACAO_INDISPONIVEL)', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AudioRecorder limitMb={50} onChange={onChange} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Gravação indisponível neste navegador');
    expect(screen.queryByRole('button', { name: 'Começar a gravar' })).not.toBeInTheDocument();

    const file = new File(['abc'], 'lesson.m4a', { type: 'audio/mp4' });
    await user.upload(screen.getByLabelText('Arquivo de áudio'), file);

    expect(onChange).toHaveBeenLastCalledWith({ file, name: 'lesson.m4a' });
    expect(screen.getByText('lesson.m4a')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remover arquivo' }));
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it('microfone negado mostra o aviso e mantém o envio de arquivo', async () => {
    mockMicrophone(false);
    const user = userEvent.setup();
    render(<AudioRecorder limitMb={50} onChange={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Começar a gravar' }));

    expect(await screen.findByText(/Gravação indisponível neste navegador/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Enviar arquivo de áudio/ })).toBeInTheDocument();
  });

  it('arquivo acima do limite é recusado antes do envio', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AudioRecorder limitMb={1} onChange={onChange} />);

    const large = new File([new Uint8Array(2 * 1024 * 1024)], 'long.mp3', { type: 'audio/mpeg' });
    await user.upload(screen.getByLabelText('Arquivo de áudio'), large);

    expect(
      screen.getByText('O áudio enviado é maior que o limite permitido. Grave um trecho mais curto.'),
    ).toBeInTheDocument();
    expect(onChange).toHaveBeenLastCalledWith(null);
    expect(screen.queryByText('long.mp3')).not.toBeInTheDocument();
  });
});
