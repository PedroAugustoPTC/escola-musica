import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { SessionClock } from '../../../src/components/admin/SessionClock';

const MINUTE = 60_000;

describe('SessionClock', () => {
  it('mostra o horário de expiração no fuso da escola', () => {
    // 14:00 UTC = 11:00 em America/Sao_Paulo
    render(<SessionClock expiresAt="2099-09-21T14:00:00Z" onExpired={() => undefined} />);
    expect(screen.getByText('Sua sessão expira às 11:00.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('avisa uma única vez, com role="alert", quando faltam 5 minutos', async () => {
    const expiresAt = new Date(Date.now() + 5 * MINUTE + 1200).toISOString();
    render(<SessionClock expiresAt={expiresAt} onExpired={() => undefined} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    const alert = await screen.findByRole('alert', {}, { timeout: 4000 });
    expect(alert).toHaveTextContent(
      'Sua sessão termina em 5 minuto(s). Conclua o que está fazendo e entre de novo.',
    );
    // continua sendo um único aviso, com o mesmo texto, mesmo depois de a contagem andar
    await new Promise((resolve) => setTimeout(resolve, 2200));
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('alert')).toHaveTextContent('termina em 5 minuto(s)');
    expect(document.body.textContent).not.toContain('{minutes}');
    // relógio real (≈ 3,5 s a 7 s): o limite padrão de 5 s estoura com a máquina carregada
  }, 15_000);

  it('já abre com o aviso quando restam menos de 5 minutos', () => {
    const expiresAt = new Date(Date.now() + 2 * MINUTE + 30_000).toISOString();
    render(<SessionClock expiresAt={expiresAt} onExpired={() => undefined} />);
    expect(screen.getByRole('alert')).toHaveTextContent('termina em 3 minuto(s)');
  });

  it('chama onExpired uma vez ao chegar a hora', async () => {
    const onExpired = vi.fn();
    render(<SessionClock expiresAt={new Date(Date.now() + 1500).toISOString()} onExpired={onExpired} />);
    await waitFor(() => expect(onExpired).toHaveBeenCalledTimes(1), { timeout: 4000 });
    await new Promise((resolve) => setTimeout(resolve, 1300));
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('com a sessão já vencida chama onExpired logo', async () => {
    const onExpired = vi.fn();
    render(<SessionClock expiresAt={new Date(Date.now() - 1000).toISOString()} onExpired={onExpired} />);
    await waitFor(() => expect(onExpired).toHaveBeenCalledTimes(1));
  });
});
