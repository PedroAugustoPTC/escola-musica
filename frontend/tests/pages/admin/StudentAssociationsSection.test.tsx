import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  REMOVAL_PREVIEW,
  TEACHER_ACTIVE,
  TEACHER_INACTIVE,
  administratorStudent,
  pageOf,
} from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';
import { resetStub } from './studentFormStub';

vi.mock('../../../src/components/student/StudentForm', async () => await import('./studentFormStub'));

const DORA = {
  id: '0190a000-0000-7000-8000-0000000000b3',
  name: 'Dora Pires',
  email: 'dora@example.test',
  active: true,
  telegramLinked: false,
};
const STUDENT = administratorStudent();
const STUDENT_PATH = `/api/v1/admin/students/${STUDENT.id}`;
const ASSOCIATION = `${STUDENT_PATH}/teachers/${TEACHER_ACTIVE.id}`;

function open(apiRoutes: Parameters<typeof renderAdminArea>[1] = {}) {
  return renderAdminArea(`/admin/students/${STUDENT.id}`, {
    [`GET ${STUDENT_PATH}`]: { body: STUDENT },
    'GET /api/v1/admin/teachers': { body: pageOf([TEACHER_ACTIVE, DORA, TEACHER_INACTIVE]) },
    [`GET ${ASSOCIATION}/removal-preview`]: { body: REMOVAL_PREVIEW },
    ...apiRoutes,
  });
}

async function openRemoval(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Retirar Clara Souza' }));
  return screen.findByRole('dialog', { name: 'Retirar professor do aluno' });
}

describe('StudentAssociationsSection', () => {
  beforeEach(() => resetStub());

  it('lista os professores associados com a situação em ícone e texto', async () => {
    open({
      [`GET ${STUDENT_PATH}`]: {
        body: administratorStudent({
          teachers: [
            { id: TEACHER_ACTIVE.id, name: 'Clara Souza', active: true },
            { id: TEACHER_INACTIVE.id, name: 'Bruno Lima', active: false },
          ],
        }),
      },
    });
    const section = (await screen.findByRole('heading', { name: 'Professores do aluno' })).closest(
      'section',
    )!;
    const items = within(section).getAllByRole('listitem');
    expect(within(items[0]!).getByText('Clara Souza')).toBeInTheDocument();
    expect(within(items[0]!).getByText('Ativo')).toBeInTheDocument();
    expect(within(items[1]!).getByText('Bruno Lima')).toBeInTheDocument();
    expect(within(items[1]!).getByText('Inativo')).toBeInTheDocument();
  });

  it('retirar mostra quantos relatórios em aberto ficam congelados, vindo da prévia', async () => {
    const api = open();
    const user = userEvent.setup();
    const dialog = await openRemoval(user);

    expect(dialog).toHaveTextContent(
      'Retirar Clara Souza de Matteo Mafra? O acesso dele(a) ao aluno termina agora. 2 relatório(s) em aberto ficarão congelados.',
    );
    expect(api.calls.some((call) => call.method === 'GET' && call.url.endsWith('/removal-preview'))).toBe(
      true,
    );
    expect(api.calls.some((call) => call.method === 'DELETE')).toBe(false);
  });

  it('a confirmação não bloqueia, grava a retirada e atualiza a lista', async () => {
    let associated = true;
    const api = open({
      [`GET ${STUDENT_PATH}`]: () => ({
        body: administratorStudent({ teachers: associated ? STUDENT.teachers : [] }),
      }),
      [`DELETE ${ASSOCIATION}`]: () => {
        associated = false;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    const dialog = await openRemoval(user);
    await user.click(within(dialog).getByRole('button', { name: 'Retirar' }));

    expect(await screen.findByText('Professor retirado do aluno.')).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === 'DELETE' && call.url.endsWith(ASSOCIATION))).toBe(true);
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Retirar Clara Souza' })).not.toBeInTheDocument(),
    );
    expect(
      screen.getByText('Este aluno não tem professores associados. Só o administrador o vê.'),
    ).toBeInTheDocument();
  });

  it('cancelar (Voltar ou Esc) não grava e devolve o foco', async () => {
    const api = open();
    const user = userEvent.setup();
    const trigger = await screen.findByRole('button', { name: 'Retirar Clara Souza' });
    await openRemoval(user);
    await user.click(screen.getByRole('button', { name: 'Voltar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();

    await openRemoval(user);
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(trigger).toHaveFocus());
    expect(api.calls.some((call) => call.method === 'DELETE')).toBe(false);
  });

  it('falha na prévia não abre a confirmação e mostra o erro', async () => {
    open({
      [`GET ${ASSOCIATION}/removal-preview`]: {
        status: 500,
        body: { code: 'INTERNAL_ERROR', message: 'Falhou.', reference: 'K7M2QX' },
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Retirar Clara Souza' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Algo deu errado. Informe o código de referência K7M2QX ao suporte.',
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('falha ao retirar mantém o diálogo com o erro', async () => {
    open({ [`DELETE ${ASSOCIATION}`]: { status: 500, body: { code: 'OPERATION_NOT_SAVED', message: 'x' } } });
    const user = userEvent.setup();
    const dialog = await openRemoval(user);
    await user.click(within(dialog).getByRole('button', { name: 'Retirar' }));
    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent(
      'Nada foi gravado',
    );
  });

  it('só professores ativos e ainda não associados podem ser incluídos', async () => {
    const api = open();
    await screen.findByRole('option', { name: 'Dora Pires' });
    const select = screen.getByLabelText('Incluir professor');
    const options = within(select)
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(options).toEqual(['Escolha um professor', 'Dora Pires']);
    const request = api.calls.find((call) => call.url.startsWith('/api/v1/admin/teachers'))!;
    expect(request.url).toContain('active=true');
  });

  it('inclui o professor escolhido e atualiza a lista', async () => {
    let associated = false;
    const api = open({
      [`GET ${STUDENT_PATH}`]: () => ({
        body: administratorStudent({
          teachers: associated
            ? [...(STUDENT.teachers ?? []), { id: DORA.id, name: 'Dora Pires', active: true }]
            : STUDENT.teachers,
        }),
      }),
      [`PUT ${STUDENT_PATH}/teachers/${DORA.id}`]: () => {
        associated = true;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    await screen.findByRole('option', { name: 'Dora Pires' });
    await user.selectOptions(screen.getByLabelText('Incluir professor'), DORA.id);
    await user.click(screen.getByRole('button', { name: 'Incluir' }));

    expect(await screen.findByText('Professor incluído no aluno.')).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === 'PUT' && call.url.endsWith(`/teachers/${DORA.id}`))).toBe(
      true,
    );
    expect(await screen.findByRole('button', { name: 'Retirar Dora Pires' })).toBeInTheDocument();
  });

  it('professor inativo recusado pelo servidor mostra o texto do catálogo', async () => {
    open({
      [`PUT ${STUDENT_PATH}/teachers/${DORA.id}`]: {
        status: 409,
        body: { code: 'TEACHER_INACTIVE', message: 'x' },
      },
    });
    const user = userEvent.setup();
    await screen.findByRole('option', { name: 'Dora Pires' });
    await user.selectOptions(screen.getByLabelText('Incluir professor'), DORA.id);
    await user.click(screen.getByRole('button', { name: 'Incluir' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este professor está inativo. Reative-o antes.',
    );
  });
});
