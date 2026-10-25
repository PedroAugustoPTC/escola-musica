import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StudentForm } from '../../../src/components/student/StudentForm';
import type { StudentFormProps } from '../../../src/components/student/studentFormTypes';
import { msg } from '../../../src/messages/messages';
import { renderWithProviders } from '../../utils';
import { TEST_CPF, TEST_CPF_MASKED } from '../../support/adminFixtures';
import { MY_STUDENT_ACTIVE } from '../../support/myStudentsFixtures';

function baseProps(overrides: Partial<StudentFormProps> = {}): StudentFormProps {
  return {
    variant: 'teacher',
    mode: 'create',
    fieldErrors: [],
    submitting: false,
    blockedSeconds: null,
    onSubmit: vi.fn(),
    onCancel: vi.fn(),
    ...overrides,
  };
}

function renderForm(overrides: Partial<StudentFormProps> = {}) {
  const props = baseProps(overrides);
  return { props, ...renderWithProviders(<StudentForm {...props} />) };
}

const TEACHERS = [
  { id: '0190a000-0000-7000-8000-0000000000b1', name: 'Clara Souza' },
  { id: '0190a000-0000-7000-8000-0000000000b3', name: 'Davi Prado' },
];

afterEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('StudentForm — campos e privacidade', () => {
  it('protege o CPF: teclado numérico, sem preenchimento automático, sem capitalização e sem corretor', () => {
    renderForm();
    const cpf = screen.getByLabelText('CPF');
    expect(cpf).toHaveAttribute('inputmode', 'numeric');
    expect(cpf).toHaveAttribute('autocomplete', 'off');
    expect(cpf).toHaveAttribute('autocapitalize', 'off');
    expect(cpf).toHaveAttribute('spellcheck', 'false');
    expect(cpf).toHaveValue('');
  });

  it('usa os tipos certos: data, telefone e e-mail do responsável', () => {
    renderForm();
    expect(screen.getByLabelText('Data de nascimento')).toHaveAttribute('type', 'date');
    expect(screen.getByLabelText('Telefone do responsável')).toHaveAttribute('type', 'tel');
    expect(screen.getByLabelText('E-mail do responsável')).toHaveAttribute('type', 'email');
  });

  it('mostra a seção Responsável sempre, com o rótulo do catálogo', () => {
    renderForm();
    expect(screen.getByRole('group', { name: msg('MY_STUDENTS_GUARDIAN_SECTION') })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Aluno' })).toBeInTheDocument();
  });

  it('no cadastro o CPF vem vazio e sem a dica de manter', () => {
    renderForm({ mode: 'create' });
    expect(screen.queryByText(msg('MY_STUDENTS_CPF_KEEP'))).not.toBeInTheDocument();
  });

  it('na edição preenche os dados, mas deixa o CPF em branco com a dica de manter o atual', () => {
    renderForm({ mode: 'edit', student: MY_STUDENT_ACTIVE });
    expect(screen.getByLabelText('Nome do aluno')).toHaveValue('Olavo Torrente');
    expect(screen.getByLabelText('Data de nascimento')).toHaveValue('2010-03-22');
    expect(screen.getByLabelText('Instrumento')).toHaveValue('Violão');
    expect(screen.getByLabelText('Nome do responsável')).toHaveValue('Sônia Torrente');
    expect(screen.getByLabelText('Telefone do responsável')).toHaveValue('11912345678');
    expect(screen.getByLabelText('E-mail do responsável')).toHaveValue('sonia@example.test');
    const cpf = screen.getByLabelText('CPF');
    expect(cpf).toHaveValue('');
    expect(cpf).toHaveAccessibleDescription(msg('MY_STUDENTS_CPF_KEEP'));
  });

  it('na edição de aluno sem responsável deixa os campos do responsável vazios', () => {
    renderForm({ mode: 'edit', student: { ...MY_STUDENT_ACTIVE, guardian: undefined, instrument: undefined } });
    expect(screen.getByLabelText('Nome do responsável')).toHaveValue('');
    expect(screen.getByLabelText('Instrumento')).toHaveValue('');
  });

  it('nunca grava o CPF digitado em URL, localStorage ou sessionStorage', async () => {
    const user = userEvent.setup();
    const { router } = renderForm();
    await user.type(screen.getByLabelText('CPF'), TEST_CPF_MASKED);
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    expect(router.state.location.search).toBe('');
    expect(router.state.location.pathname).not.toContain(TEST_CPF);
    for (const store of [window.localStorage, window.sessionStorage]) {
      const content = JSON.stringify({ ...store });
      expect(content).not.toContain(TEST_CPF);
      expect(content).not.toContain('111.444');
    }
    expect(document.title).not.toContain(TEST_CPF);
  });

  it('não aplica máscara: o CPF fica como foi digitado', async () => {
    const user = userEvent.setup();
    renderForm();
    await user.type(screen.getByLabelText('CPF'), TEST_CPF);
    expect(screen.getByLabelText('CPF')).toHaveValue(TEST_CPF);
  });
});

describe('StudentForm — envio', () => {
  it('entrega os valores digitados ao enviar (variante do professor, sem professores)', async () => {
    const user = userEvent.setup();
    const { props } = renderForm();
    await user.type(screen.getByLabelText('Nome do aluno'), 'Olavo Torrente');
    await user.type(screen.getByLabelText('Data de nascimento'), '2010-03-22');
    await user.type(screen.getByLabelText('CPF'), TEST_CPF_MASKED);
    await user.type(screen.getByLabelText('Instrumento'), 'Violão');
    await user.type(screen.getByLabelText('Nome do responsável'), 'Sônia Torrente');
    await user.type(screen.getByLabelText('Telefone do responsável'), '(11) 91234-5678');
    await user.type(screen.getByLabelText('E-mail do responsável'), 'sonia@example.test');
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));

    expect(props.onSubmit).toHaveBeenCalledTimes(1);
    expect(props.onSubmit).toHaveBeenCalledWith({
      name: 'Olavo Torrente',
      birthDate: '2010-03-22',
      cpf: TEST_CPF_MASKED,
      instrument: 'Violão',
      guardianName: 'Sônia Torrente',
      guardianPhone: '(11) 91234-5678',
      guardianEmail: 'sonia@example.test',
      teacherIds: [],
    });
  });

  it('não decide a validade: envia mesmo com campos vazios (as regras são do servidor)', async () => {
    const user = userEvent.setup();
    const { props } = renderForm();
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    expect(props.onSubmit).toHaveBeenCalledTimes(1);
  });

  it('na edição o botão salva e o CPF em branco segue em branco', async () => {
    const user = userEvent.setup();
    const { props } = renderForm({ mode: 'edit', student: MY_STUDENT_ACTIVE });
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    expect(props.onSubmit).toHaveBeenCalledWith(expect.objectContaining({ cpf: '', name: 'Olavo Torrente' }));
  });

  it('Cancelar chama onCancel sem enviar', async () => {
    const user = userEvent.setup();
    const { props } = renderForm();
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(props.onCancel).toHaveBeenCalledTimes(1);
    expect(props.onSubmit).not.toHaveBeenCalled();
  });

  it('durante o envio o botão fica ocupado e desabilitado', () => {
    renderForm({ submitting: true });
    const button = screen.getByRole('button', { name: /Cadastrar aluno/ });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });
});

describe('StudentForm — variante do administrador', () => {
  it('oferece a multisseleção de professores e entrega os escolhidos', async () => {
    const user = userEvent.setup();
    const { props } = renderForm({ variant: 'admin', teacherOptions: TEACHERS });
    const group = screen.getByRole('group', { name: 'Professores' });
    expect(within(group).getAllByRole('checkbox')).toHaveLength(2);
    await user.click(within(group).getByRole('checkbox', { name: 'Davi Prado' }));
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    expect(props.onSubmit).toHaveBeenCalledWith(expect.objectContaining({ teacherIds: [TEACHERS[1]!.id] }));
  });

  it('na edição marca os professores já associados', () => {
    renderForm({
      variant: 'admin',
      mode: 'edit',
      teacherOptions: TEACHERS,
      student: { ...MY_STUDENT_ACTIVE, teachers: [{ id: TEACHERS[0]!.id, name: 'Clara Souza', active: true }] },
    });
    expect(screen.getByRole('checkbox', { name: 'Clara Souza' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Davi Prado' })).not.toBeChecked();
  });

  it('na variante do professor não existe a seção de professores', () => {
    renderForm({ variant: 'teacher', teacherOptions: TEACHERS });
    expect(screen.queryByRole('group', { name: 'Professores' })).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });
});

describe('StudentForm — erros por campo', () => {
  const ERRORS = [
    { field: 'cpf', code: 'INVALID_CPF' },
    { field: 'birthDate', code: 'INVALID_BIRTH_DATE' },
    { field: 'guardian.phone', code: 'INVALID_PHONE' },
  ];

  it('liga cada erro ao seu campo por aria-describedby e marca o campo como inválido', () => {
    renderForm({ fieldErrors: ERRORS });
    const cpf = screen.getByLabelText('CPF');
    expect(cpf).toHaveAttribute('aria-invalid', 'true');
    expect(cpf).toHaveAccessibleDescription(msg('INVALID_CPF'));

    const birth = screen.getByLabelText('Data de nascimento');
    expect(birth).toHaveAttribute('aria-invalid', 'true');
    expect(birth).toHaveAccessibleDescription(msg('INVALID_BIRTH_DATE'));

    const phone = screen.getByLabelText('Telefone do responsável');
    expect(phone).toHaveAttribute('aria-invalid', 'true');
    expect(phone).toHaveAccessibleDescription(msg('INVALID_PHONE'));

    expect(screen.getByLabelText('Nome do aluno')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('na edição o campo com erro descreve o erro e também a dica do CPF', () => {
    renderForm({ mode: 'edit', student: MY_STUDENT_ACTIVE, fieldErrors: [{ field: 'cpf', code: 'INVALID_CPF' }] });
    expect(screen.getByLabelText('CPF').getAttribute('aria-describedby')!.split(' ')).toHaveLength(2);
    expect(screen.getByLabelText('CPF')).toHaveAccessibleDescription(
      `${msg('INVALID_CPF')} ${msg('MY_STUDENTS_CPF_KEEP')}`,
    );
  });

  it('mostra o resumo de erros no topo e o foco vai para ele ao reportar erros', () => {
    renderForm({ fieldErrors: ERRORS });
    const summary = screen.getByRole('alert');
    expect(summary).toHaveFocus();
    expect(within(summary).getAllByRole('link')).toHaveLength(3);
    expect(within(summary).getByRole('link', { name: new RegExp(msg('INVALID_CPF')) })).toBeInTheDocument();
  });

  it('o foco vai para o resumo quando os erros chegam depois do envio', async () => {
    const user = userEvent.setup();
    function Harness() {
      const [errors, setErrors] = useState<StudentFormProps['fieldErrors']>([]);
      return <StudentForm {...baseProps()} fieldErrors={errors} onSubmit={() => setErrors(ERRORS)} />;
    }
    renderWithProviders(<Harness />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cadastrar aluno' }));
    expect(await screen.findByRole('alert')).toHaveFocus();
  });

  it('o link do resumo leva o foco ao campo', async () => {
    const user = userEvent.setup();
    renderForm({ fieldErrors: ERRORS });
    await user.click(screen.getByRole('link', { name: new RegExp(msg('INVALID_CPF')) }));
    expect(screen.getByLabelText('CPF')).toHaveFocus();
  });

  it('erro de grupo do responsável aparece na seção e entra no resumo', () => {
    renderForm({ fieldErrors: [{ field: 'guardian', code: 'GUARDIAN_REQUIRED' }] });
    const group = screen.getByRole('group', { name: msg('MY_STUDENTS_GUARDIAN_SECTION') });
    expect(group).toHaveAccessibleDescription(msg('GUARDIAN_REQUIRED'));
    expect(within(screen.getByRole('alert')).getByText(msg('GUARDIAN_REQUIRED'))).toBeInTheDocument();
  });

  it('erro de campo desconhecido entra só no resumo, sem quebrar', () => {
    renderForm({ fieldErrors: [{ field: 'unknownField', code: 'INVALID_TEXT' }] });
    expect(within(screen.getByRole('alert')).getByText(msg('INVALID_TEXT'))).toBeInTheDocument();
  });

  it('sem erros não mostra resumo nem marca campos', () => {
    renderForm();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByLabelText('CPF')).not.toHaveAttribute('aria-invalid', 'true');
  });
});

describe('StudentForm — bloqueio contra sondagem', () => {
  it('desabilita só o envio e mostra o tempo restante', () => {
    renderForm({ blockedSeconds: 90 });
    expect(screen.getByRole('button', { name: 'Cadastrar aluno' })).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent(msg('OPERATION_TEMPORARILY_BLOCKED', { minutes: 2 }));
    expect(screen.getByLabelText('CPF')).toBeEnabled();
    expect(screen.getByLabelText('Nome do aluno')).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeEnabled();
  });

  it('o aviso de bloqueio é uma região viva educada', () => {
    renderForm({ blockedSeconds: 30 });
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByRole('status')).toHaveTextContent('1 minuto(s)');
  });

  it('sem bloqueio não mostra aviso e o envio fica disponível', () => {
    renderForm({ blockedSeconds: null });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cadastrar aluno' })).toBeEnabled();
  });
});
