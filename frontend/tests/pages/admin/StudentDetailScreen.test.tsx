import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TEACHER_ACTIVE, administratorStudent, pageOf, validationError } from '../../support/adminFixtures';
import { renderAdminArea } from './adminTestSupport';
import { resetStub } from './studentFormStub';

vi.mock('../../../src/components/student/StudentForm', async () => await import('./studentFormStub'));

const STUDENT = administratorStudent();
const PATH = `/api/v1/admin/students/${STUDENT.id}`;

function open(apiRoutes: Parameters<typeof renderAdminArea>[1] = {}) {
  return renderAdminArea(`/admin/students/${STUDENT.id}`, {
    [`GET ${PATH}`]: { body: STUDENT },
    'GET /api/v1/admin/teachers': { body: pageOf([TEACHER_ACTIVE]) },
    ...apiRoutes,
  });
}

describe('StudentDetailScreen (A12)', () => {
  beforeEach(() => resetStub());

  it('mostra o aluno com situação em ícone e texto e o formulário de edição', async () => {
    open();
    expect(await screen.findByRole('heading', { name: 'Matteo Mafra' })).toBeInTheDocument();
    expect(screen.getAllByText('Ativo').length).toBeGreaterThan(0);
    expect(screen.getByText('modo admin/edit')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Inativar' })).toBeInTheDocument();
  });

  it('oferece o atalho da auditoria, que abre a trilha só com o identificador do aluno', async () => {
    open();
    expect(await screen.findByRole('link', { name: 'Quem acessou os relatórios deste aluno' })).toHaveAttribute(
      'href',
      `/admin/audit?targetId=${STUDENT.id}`,
    );
  });

  it('oferece "Ver histórico", que abre o histórico da escola só com o identificador do aluno', async () => {
    open();
    expect(await screen.findByRole('link', { name: 'Ver histórico' })).toHaveAttribute(
      'href',
      `/admin/history?studentId=${STUDENT.id}`,
    );
  });

  it('edita sem reenviar o CPF (em branco mantém o atual) e confirma em role="status"', async () => {
    const api = open({ [`PATCH ${PATH}`]: { body: { ...STUDENT, instrument: 'Violão' } } });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));

    expect(await screen.findByText('Dados salvos.')).toBeInTheDocument();
    const patch = api.calls.find((call) => call.method === 'PATCH')!;
    const body = JSON.parse(patch.init.body as string) as Record<string, unknown>;
    expect(body).not.toHaveProperty('cpf');
    expect(body).toMatchObject({ name: 'Matteo Mafra', birthDate: '2012-05-10' });
    expect(
      JSON.stringify(
        api.client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.variables ?? null),
      ),
    ).not.toContain('cpf');
  });

  it('CPF informado na edição vai só no corpo da requisição', async () => {
    const { stub } = await import('./studentFormStub');
    stub.values = { cpf: '111.444.777-35' };
    const api = open({ [`PATCH ${PATH}`]: { body: STUDENT } });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));
    await screen.findByText('Dados salvos.');
    const patch = api.calls.find((call) => call.method === 'PATCH')!;
    expect(JSON.parse(patch.init.body as string)).toMatchObject({ cpf: '111.444.777-35' });
    expect(api.router.state.location.search).toBe('');
    expect(
      JSON.stringify(
        api.client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data ?? null),
      ),
    ).not.toContain('111.444');
  });

  it('erros por campo e CPF repetido na edição', async () => {
    open({
      [`PATCH ${PATH}`]: {
        status: 400,
        body: validationError([{ field: 'birthDate', code: 'INVALID_BIRTH_DATE' }]),
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salvar aluno' }));
    await waitFor(() =>
      expect(screen.getByRole('list', { name: 'Erros por campo' })).toHaveTextContent(
        'birthDate:INVALID_BIRTH_DATE',
      ),
    );
  });

  it('inativar pede confirmação; Esc cancela, o foco volta e nada é gravado', async () => {
    const api = open();
    const user = userEvent.setup();
    const trigger = await screen.findByRole('button', { name: 'Inativar' });
    await user.click(trigger);

    const dialog = await screen.findByRole('dialog', { name: 'Inativar aluno' });
    expect(dialog).toHaveTextContent(
      'Inativar Matteo Mafra? O aluno deixa de aparecer para novas aulas; nada é apagado.',
    );
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
    expect(api.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('confirmar inativa, avisa em role="status" e passa a oferecer "Reativar", que reativa na hora', async () => {
    let active = true;
    const api = open({
      [`GET ${PATH}`]: () => ({ body: { ...STUDENT, active } }),
      [`POST ${PATH}/inactivation`]: () => {
        active = false;
        return { body: { ...STUDENT, active: false } };
      },
      [`POST ${PATH}/reactivation`]: () => {
        active = true;
        return { body: { ...STUDENT, active: true } };
      },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Inativar' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));

    expect(await screen.findByText('Matteo Mafra foi inativado(a).')).toBeInTheDocument();
    expect(screen.getByText('Inativo')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reativar' }));
    expect(await screen.findByText('Matteo Mafra foi reativado(a).')).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === 'POST')).toHaveLength(2);
  });

  it('falha ao inativar mantém o diálogo com o erro', async () => {
    open({
      [`POST ${PATH}/inactivation`]: { status: 500, body: { code: 'OPERATION_NOT_SAVED', message: 'x' } },
    });
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Inativar' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Inativar' }));
    expect(await within(await screen.findByRole('dialog')).findByRole('alert')).toHaveTextContent(
      'Nada foi gravado',
    );
    expect(screen.queryByText('Inativo')).not.toBeInTheDocument();
  });

  it('aluno inexistente mostra o erro do servidor', async () => {
    open({
      [`GET ${PATH}`]: {
        status: 404,
        body: { code: 'RESOURCE_NOT_FOUND', message: 'Não encontramos o que você procurou.' },
      },
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('Não encontramos o que você procurou.');
  });
});
