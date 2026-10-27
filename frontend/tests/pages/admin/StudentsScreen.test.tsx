import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TEACHER_ACTIVE, TEST_CPF, administratorStudent, pageOf } from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';

vi.mock('../../../src/components/student/StudentForm', async () => await import('./studentFormStub'));

const LIST = 'GET /api/v1/admin/students';

const MATTEO = administratorStudent();
const HELENA = administratorStudent({
  id: '0190a000-0000-7000-8000-0000000000d2',
  name: 'Helena Prado',
  age: 25,
  instrument: undefined,
  active: false,
  teachers: [
    { id: TEACHER_ACTIVE.id, name: 'Clara Souza', active: true },
    { id: '0190a000-0000-7000-8000-0000000000b2', name: 'Bruno Lima', active: false },
  ],
});

describe('StudentsScreen (A10)', () => {
  it('lista nome, idade, instrumento, situação e professores associados', async () => {
    renderAdminArea('/admin/students', { [LIST]: { body: pageOf([MATTEO, HELENA]) } });

    expect(await screen.findByRole('heading', { name: 'Alunos' })).toBeInTheDocument();
    const items = await screen.findAllByRole('listitem');
    const matteo = items.find((item) => within(item).queryByText('Matteo Mafra'))!;
    const helena = items.find((item) => within(item).queryByText('Helena Prado'))!;
    expect(within(matteo).getByRole('link', { name: 'Matteo Mafra' })).toHaveAttribute(
      'href',
      `/admin/students/${MATTEO.id}`,
    );
    expect(within(matteo).getByText('14 anos')).toBeInTheDocument();
    expect(within(matteo).getByText('Piano')).toBeInTheDocument();
    expect(within(matteo).getByText('Ativo')).toBeInTheDocument();
    expect(within(matteo).getByText(/Clara Souza/)).toBeInTheDocument();
    expect(within(helena).getByText('Inativo')).toBeInTheDocument();
    expect(within(helena).getByText(/Bruno Lima \(inativo\)/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Cadastrar aluno' })).toHaveAttribute(
      'href',
      '/admin/students/new',
    );
  });

  it('busca em <form role="search"> pelo nome ou instrumento e guarda o termo na URL', async () => {
    const api = renderAdminArea('/admin/students', { [LIST]: { body: pageOf([MATTEO]) } });
    const user = userEvent.setup();
    await screen.findByText('Matteo Mafra');

    const form = screen.getByRole('search');
    await user.type(within(form).getByRole('searchbox', { name: 'Buscar aluno' }), 'piano');
    await user.click(within(form).getByRole('button', { name: 'Buscar' }));

    await screen.findByText('Matteo Mafra');
    expect(api.router.state.location.search).toBe('?search=piano');
    expect(api.calls.some((call) => call.url.includes('search=piano'))).toBe(true);
  });

  it.each([TEST_CPF, '111.444.777-35', '111 444 777 35'])(
    'termo com forma de CPF (%s) é recusado no cliente, sem ir à URL, à API nem ao cache',
    async (term) => {
      const api = renderAdminArea('/admin/students', { [LIST]: { body: pageOf([MATTEO]) } });
      const user = userEvent.setup();
      await screen.findByText('Matteo Mafra');
      const before = api.calls.length;

      await user.type(screen.getByRole('searchbox', { name: 'Buscar aluno' }), term);
      await user.click(screen.getByRole('button', { name: 'Buscar' }));

      expect(
        await screen.findByText('Não há busca por CPF. Procure pelo nome ou pelo instrumento.'),
      ).toBeInTheDocument();
      expect(screen.getByRole('searchbox', { name: 'Buscar aluno' })).toHaveAttribute('aria-invalid', 'true');
      expect(api.router.state.location.search).toBe('');
      expect(api.calls.slice(before)).toEqual([]);
      const keys = JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((query) => query.queryKey),
      );
      expect(keys).not.toContain('11144477735');
      expect(keys).not.toContain('111.444');
      expect(keys).not.toContain('111 444');
    },
  );

  it('filtra por situação: ativos (padrão), inativos e todos', async () => {
    const api = renderAdminArea('/admin/students', { [LIST]: { body: pageOf([MATTEO]) } });
    const user = userEvent.setup();
    await screen.findByText('Matteo Mafra');
    expect(screen.getByRole('button', { name: 'Ativos' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: 'Inativos' }));
    await screen.findByText('Matteo Mafra');
    expect(api.calls.some((call) => call.url.includes('status=INACTIVE'))).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Todos' }));
    await screen.findByText('Matteo Mafra');
    expect(api.calls.some((call) => call.url.includes('status=ALL'))).toBe(true);
  });

  it('sem alunos mostra o estado vazio com a ação que resolve', async () => {
    renderAdminArea('/admin/students', { [LIST]: { body: pageOf([]) } });
    expect(await screen.findByText('Nenhum aluno cadastrado ainda.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Cadastrar aluno' }).length).toBeGreaterThan(0);
  });

  it('busca sem resultado oferece limpar a busca', async () => {
    renderAdminArea('/admin/students?search=zzz', { [LIST]: { body: pageOf([]) } });
    expect(await screen.findByText('Nenhum aluno encontrado para “zzz”.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Limpar busca' })).toBeInTheDocument();
  });

  it('erro mostra alerta com "Tentar novamente"; carregando é anunciado', async () => {
    renderAdminArea('/admin/students', {
      [LIST]: { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Ocorreu um erro inesperado.' } },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado.');
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });

  it('carregando mostra os esqueletos anunciados', async () => {
    renderAdminArea('/admin/students', { [LIST]: () => new Promise(() => undefined) });
    expect(await screen.findByRole('status', { name: 'Carregando alunos' })).toHaveAttribute(
      'aria-busy',
      'true',
    );
  });

  it('pagina com Anterior/Próxima e total', async () => {
    const api = renderAdminArea('/admin/students', {
      [LIST]: { body: pageOf([MATTEO], { page: 0, size: 20, total: 45 }) },
    });
    const user = userEvent.setup();
    expect(await screen.findByText('Página 1 de 3 · 45 aluno(s)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    await screen.findByText('Matteo Mafra');
    expect(api.calls.some((call) => call.url.includes('page=1'))).toBe(true);
  });

  it.each([TEST_CPF, '111.444.777-35', '111 444 777 35'])(
    '?search= com forma de CPF (%s) na URL é descartado: limpa a URL, avisa e nunca vai à API nem à chave',
    async (term) => {
      const api = renderAdminArea(`/admin/students?search=${encodeURIComponent(term)}`, {
        [LIST]: { body: pageOf([MATTEO]) },
      });
      await screen.findByText('Matteo Mafra');

      expect(
        await screen.findByText('Não há busca por CPF. Procure pelo nome ou pelo instrumento.'),
      ).toBeInTheDocument();
      await waitFor(() => expect(api.router.state.location.search).toBe(''));
      expect(screen.getByRole('searchbox', { name: 'Buscar aluno' })).toHaveValue('');
      expect(api.calls.map((call) => call.url).join(' ')).not.toMatch(
        /11144477735|111\.444|111%20444|111\+444/,
      );
      const keys = JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((query) => query.queryKey),
      );
      expect(keys).not.toMatch(/11144477735|111\.444|111 444/);
    },
  );
});
