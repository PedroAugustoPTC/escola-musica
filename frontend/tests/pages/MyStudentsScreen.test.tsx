import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '../../src/app/routes';
import { msg } from '../../src/messages/messages';
import { renderAsTeacher, type SimulatedResponse } from '../utils';
import { TEST_CPF, TEST_CPF_MASKED } from '../support/adminFixtures';
import {
  MY_STUDENT_ACTIVE,
  MY_STUDENT_INACTIVE,
  MY_STUDENT_SHARED,
  myStudentsPage,
} from '../support/myStudentsFixtures';

type Query = Record<string, string>;

function query(url: string): Query {
  return Object.fromEntries(new URL(url, 'http://test.local').searchParams.entries());
}

/** Resposta de `GET /my-students` que depende do filtro pedido. */
function listBy(pages: Record<string, SimulatedResponse>) {
  return (url: string): SimulatedResponse => {
    const { status = 'ACTIVE', search } = query(url);
    return pages[search ? `${status}:${search}` : status] ?? { body: myStudentsPage([]) };
  };
}

describe('P01 Meus alunos', () => {
  it('lista os alunos do professor com situação e link para o detalhe', async () => {
    renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': {
        body: myStudentsPage([MY_STUDENT_ACTIVE, MY_STUDENT_SHARED]),
      },
    });

    expect(
      await screen.findByRole('heading', { level: 1, name: msg('MY_STUDENTS_TITLE') }),
    ).toBeInTheDocument();
    const link = await screen.findByRole('link', { name: 'Olavo Torrente' });
    expect(link).toHaveAttribute('href', `/my-students/${MY_STUDENT_ACTIVE.id}`);
    expect(screen.getByRole('link', { name: 'Miguel de Castro' })).toBeInTheDocument();
    expect(screen.getAllByText('Ativo')).toHaveLength(2);
    expect(screen.getByText(/2 aluno\(s\)/)).toBeInTheDocument();
  });

  it('pede por padrão só os ativos, sem termo de busca', async () => {
    const api = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });
    const listCall = api.calls.find((c) => c.url.startsWith('/api/v1/my-students'))!;
    expect(query(listCall.url)).toMatchObject({ status: 'ACTIVE' });
    expect(query(listCall.url).search).toBeUndefined();
  });

  it('o botão Cadastrar aluno leva ao formulário', async () => {
    const user = userEvent.setup();
    const { router } = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
    });
    await user.click(await screen.findByRole('button', { name: 'Cadastrar aluno' }));
    expect(router.state.location.pathname).toBe('/my-students/new');
  });

  it('lista vazia mostra a orientação do catálogo e o botão Cadastrar o primeiro aluno', async () => {
    const user = userEvent.setup();
    const { router } = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': { body: myStudentsPage([]) },
    });
    expect(await screen.findByText(msg('MY_STUDENTS_EMPTY'))).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cadastrar o primeiro aluno' }));
    expect(router.state.location.pathname).toBe('/my-students/new');
  });

  it('busca por nome ou instrumento só ao enviar o formulário de busca', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': listBy({
        ACTIVE: { body: myStudentsPage([MY_STUDENT_ACTIVE, MY_STUDENT_SHARED]) },
        'ACTIVE:violino': { body: myStudentsPage([MY_STUDENT_SHARED]) },
      }),
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });

    const field = screen.getByRole('searchbox', { name: 'Buscar entre os meus alunos' });
    await user.type(field, 'violino');
    expect(api.calls.some((c) => query(c.url).search !== undefined)).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Olavo Torrente' })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('link', { name: 'Miguel de Castro' })).toBeInTheDocument();
    expect(api.calls.some((c) => query(c.url).search === 'violino')).toBe(true);
  });

  it('busca sem resultado mostra a mensagem do catálogo e permite limpar', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': listBy({
        ACTIVE: { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
        'ACTIVE:Beethoven': { body: myStudentsPage([]) },
      }),
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });
    await user.type(screen.getByRole('searchbox', { name: 'Buscar entre os meus alunos' }), 'Beethoven');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(
      await screen.findByText(msg('MY_STUDENTS_SEARCH_EMPTY', { search: 'Beethoven' })),
    ).toBeInTheDocument();
    expect(screen.queryByText(msg('MY_STUDENTS_EMPTY'))).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Limpar busca' }));
    expect(await screen.findByRole('link', { name: 'Olavo Torrente' })).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'Buscar entre os meus alunos' })).toHaveValue('');
  });

  it.each([
    ['sem máscara', TEST_CPF],
    ['com máscara', TEST_CPF_MASKED],
    ['só com traço', `${TEST_CPF.slice(0, 9)}-${TEST_CPF.slice(9)}`],
  ])(
    'termo com forma de CPF (%s) é recusado no cliente: não vai à URL, à API nem ao cache',
    async (_label, term) => {
      const user = userEvent.setup();
      const { router, client, calls } = renderAsTeacher(routes, '/my-students', {
        'GET /api/v1/my-students': { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
      });
      await screen.findByRole('link', { name: 'Olavo Torrente' });
      const before = calls.length;

      const field = screen.getByRole('searchbox', { name: 'Buscar entre os meus alunos' });
      await user.type(field, term);
      await user.click(screen.getByRole('button', { name: 'Buscar' }));

      expect(await screen.findByText(/busca por CPF não existe/i)).toBeInTheDocument();
      expect(field).toHaveAttribute('aria-invalid', 'true');
      expect(field).toHaveAccessibleDescription(/busca por CPF não existe/i);

      // nenhuma chamada nova, URL limpa e nada do termo no cache de consulta
      expect(calls.length).toBe(before);
      expect(router.state.location.search).toBe('');
      expect(router.state.location.pathname).toBe('/my-students');
      const keys = JSON.stringify(
        client
          .getQueryCache()
          .getAll()
          .map((q) => q.queryKey),
      );
      expect(keys).not.toContain(TEST_CPF);
      expect(keys).not.toContain('111.444');
      expect(calls.map((c) => c.url).join(' ')).not.toContain(TEST_CPF);
      expect(JSON.stringify({ ...window.sessionStorage, ...window.localStorage })).not.toContain(TEST_CPF);
    },
  );

  it('um número curto que não é CPF continua sendo uma busca válida', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });
    await user.type(screen.getByRole('searchbox', { name: 'Buscar entre os meus alunos' }), '2026');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(api.calls.some((c) => query(c.url).search === '2026')).toBe(true));
    expect(screen.queryByText(/busca por CPF não existe/i)).not.toBeInTheDocument();
  });

  it('o filtro Ativos/Inativos/Todos pede a situação certa e marca a opção escolhida', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': listBy({
        ACTIVE: { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
        INACTIVE: { body: myStudentsPage([MY_STUDENT_INACTIVE]) },
        ALL: { body: myStudentsPage([MY_STUDENT_ACTIVE, MY_STUDENT_INACTIVE]) },
      }),
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });
    const group = screen.getByRole('group', { name: 'Situação' });
    expect(within(group).getByRole('button', { name: 'Ativos' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(within(group).getByRole('button', { name: 'Inativos' }));
    expect(await screen.findByRole('link', { name: 'Helena Prado' })).toBeInTheDocument();
    expect(screen.getByText('Inativo')).toBeInTheDocument();
    expect(within(group).getByRole('button', { name: 'Inativos' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(group).getByRole('button', { name: 'Ativos' })).toHaveAttribute('aria-pressed', 'false');

    await user.click(within(group).getByRole('button', { name: 'Todos' }));
    expect(await screen.findByRole('link', { name: 'Olavo Torrente' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Helena Prado' })).toBeInTheDocument();
    expect(api.calls.map((c) => query(c.url).status)).toEqual(
      expect.arrayContaining(['ACTIVE', 'INACTIVE', 'ALL']),
    );
  });

  it('lista vazia no filtro de inativos não manda cadastrar o primeiro aluno', async () => {
    const user = userEvent.setup();
    renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': listBy({ ACTIVE: { body: myStudentsPage([MY_STUDENT_ACTIVE]) } }),
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });
    await user.click(screen.getByRole('button', { name: 'Inativos' }));
    expect(await screen.findByText('Nenhum aluno nesta situação.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cadastrar o primeiro aluno' })).not.toBeInTheDocument();
  });

  it('pagina com Anterior e Próxima e mostra o total', async () => {
    const user = userEvent.setup();
    const api = renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': (url) => {
        const page = Number(query(url).page ?? '0');
        return {
          body: myStudentsPage(page === 0 ? [MY_STUDENT_ACTIVE] : [MY_STUDENT_SHARED], {
            page,
            size: 1,
            total: 2,
          }),
        };
      },
    });
    await screen.findByRole('link', { name: 'Olavo Torrente' });
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Próxima' }));
    expect(await screen.findByRole('link', { name: 'Miguel de Castro' })).toBeInTheDocument();
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled();
    expect(api.calls.some((c) => query(c.url).page === '1')).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Anterior' }));
    expect(await screen.findByRole('link', { name: 'Olavo Torrente' })).toBeInTheDocument();
  });

  it('mostra o carregamento enquanto a lista não chega', async () => {
    renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': () => new Promise(() => undefined),
    });
    expect(await screen.findByLabelText('Carregando alunos')).toHaveAttribute('aria-busy', 'true');
  });

  it('falha da lista mostra o erro e permite tentar de novo', async () => {
    const user = userEvent.setup();
    let fail = true;
    renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': () =>
        fail
          ? { status: 500, body: { code: 'INTERNAL_ERROR', message: 'Falhou.', reference: 'K7M2QX' } }
          : { body: myStudentsPage([MY_STUDENT_ACTIVE]) },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Informe o código de referência K7M2QX ao suporte.',
    );
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByRole('link', { name: 'Olavo Torrente' })).toBeInTheDocument();
  });

  it('erro de carga da lista mostra o texto do catálogo pelo código, nunca a mensagem crua', async () => {
    renderAsTeacher(routes, '/my-students', {
      'GET /api/v1/my-students': {
        status: 500,
        body: { code: 'INTERNAL_ERROR', message: 'java.lang.NullPointerException: detalhe interno' },
      },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('INTERNAL_ERROR'));
    expect(document.body.textContent).not.toContain('NullPointerException');
  });
});
