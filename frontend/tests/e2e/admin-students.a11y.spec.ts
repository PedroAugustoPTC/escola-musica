import { expect, test } from '@playwright/test';
import {
  expectFocusOrder,
  expectModalDialogBehavior,
  expectNoSeriousViolations,
  expectValueAbsentFromUrlAndStorage,
  maskedCpf,
  seed,
  signInAsAdministrator,
  syntheticCpf,
  tabUntilFocused,
  type Seeding,
} from './support';

/**
 * T117 — A10 a A12 (alunos da escola): axe nos dois temas e larguras; erros por campo anunciados; a URL
 * final e o armazenamento do navegador não contêm o CPF digitado.
 */
let data: Seeding;

test.beforeEach(async ({ page }) => {
  data = await seed();
  await signInAsAdministrator(page);
});

test.describe('A10 — alunos da escola', () => {
  test('axe, busca, filtro de situação e professores associados na linha', async ({ page }) => {
    await page.goto('/admin/students');
    await expect(page.getByRole('heading', { level: 1, name: 'Alunos' })).toBeVisible();
    const row = page.getByRole('listitem').filter({ hasText: 'Matteo Mafra' });
    await expect(row).toContainText('Piano');
    await expect(row).toContainText('Ativo');
    await expect(row).toContainText('Clara Souza');
    await expect(page.getByRole('search')).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A10 alunos' });
  });

  test('a busca por nome funciona pelo teclado e o termo não vai ao armazenamento', async ({ page }) => {
    await page.goto('/admin/students');
    await tabUntilFocused(page, page.getByRole('searchbox'));
    await page.keyboard.type('Helena');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('link', { name: 'Helena Prado' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Matteo Mafra' })).toHaveCount(0);

    await page.getByRole('searchbox').fill('Zzz inexistente');
    await page.keyboard.press('Enter');
    await expect(page.getByText(/Nenhum aluno encontrado/)).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A10 busca sem resultado' });
  });

  test('não existe busca por CPF: o termo é recusado e não vai à URL nem ao armazenamento', async ({ page }) => {
    await page.goto('/admin/students');
    const cpf = '52998224725';
    await page.getByRole('searchbox').fill(maskedCpf(cpf));
    await page.keyboard.press('Enter');
    await expect(page.getByText('Não há busca por CPF. Procure pelo nome ou pelo instrumento.')).toBeVisible();
    await expect(page.getByRole('searchbox')).toHaveAttribute('aria-invalid', 'true');
    await expectValueAbsentFromUrlAndStorage(page, cpf);
    await expectNoSeriousViolations(page, { name: 'A10 busca por CPF recusada' });
  });

  test('o estado vazio oferece a ação que resolve', async ({ page }) => {
    await page.getByRole('navigation', { name: 'Área administrativa' }).getByRole('link', { name: 'Alunos' }).click();
    await page.getByRole('group', { name: 'Situação' }).getByRole('button', { name: 'Inativos' }).click();
    await expect(page.getByText(/Nenhum aluno encontrado/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Limpar busca' })).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A10 sem inativos' });
  });
});

test.describe('A11 — cadastrar aluno', () => {
  test('axe, ordem de foco e seções com legenda', async ({ page }) => {
    await page.goto('/admin/students/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Cadastrar aluno' })).toBeVisible();
    await expect(page.getByRole('group', { name: 'Aluno' })).toBeVisible();
    await expect(page.getByRole('group', { name: /^Responsável/ })).toBeVisible();
    await expect(page.getByRole('group', { name: 'Professores' })).toBeVisible();
    await expect(page.getByLabel('CPF')).toHaveAttribute('inputmode', 'numeric');
    await expect(page.getByLabel('CPF')).toHaveAttribute('autocomplete', 'off');
    await expect(page.getByLabel('CPF')).toHaveAttribute('spellcheck', 'false');
    await expectNoSeriousViolations(page, { name: 'A11 cadastrar aluno' });

    await page.setViewportSize({ width: 1440, height: 900 });
    await expectFocusOrder(page, [
      page.getByLabel('Nome do aluno'),
      page.getByLabel('Data de nascimento'),
      page.getByLabel('CPF'),
      page.getByLabel('Instrumento'),
      page.getByLabel('Nome do responsável'),
      page.getByLabel('Telefone do responsável'),
      page.getByLabel('E-mail do responsável'),
      page.getByRole('checkbox', { name: 'Clara Souza' }),
      page.getByRole('checkbox', { name: 'Bruno Lima' }),
      page.getByRole('button', { name: 'Cadastrar aluno' }),
      page.getByRole('button', { name: 'Cancelar' }),
    ]);
  });

  test('CPF inválido e CPF repetido: erros por campo, resumo anunciado e ligações aria', async ({ page }) => {
    await page.goto('/admin/students/new');
    await page.getByLabel('Nome do aluno').fill('Aluno de Teste');
    await page.getByLabel('Data de nascimento').fill('1995-04-20');
    await page.getByLabel('CPF').fill('111.111.111-11');
    await page.getByLabel('Instrumento').fill('Violão');
    await page.getByRole('button', { name: 'Cadastrar aluno' }).click();

    const summary = page.getByRole('alert').filter({ hasText: 'Confira os campos indicados' });
    await expect(summary).toBeFocused();
    const cpf = page.getByLabel('CPF');
    await expect(cpf).toHaveAttribute('aria-invalid', 'true');
    await expect(cpf).toHaveAccessibleDescription(/\S/);
    // o item do resumo leva ao campo
    await summary.getByRole('link', { name: /CPF/ }).click();
    await expect(cpf).toBeFocused();
    await expectNoSeriousViolations(page, { name: 'A11 CPF inválido' });

    // CPF de outro aluno: a duplicidade indica o NOME do aluno, nunca o CPF
    await cpf.fill(maskedCpf('52998224725'));
    await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
    const refusal = page.getByRole('alert').filter({ hasText: 'Matteo Mafra' });
    await expect(refusal).toContainText('Este CPF já pertence ao aluno Matteo Mafra.');
    await expect(page.locator('body')).not.toContainText('52998224725');
    await expectValueAbsentFromUrlAndStorage(page, '52998224725');
  });

  test('menor sem responsável é recusado com o erro no grupo do responsável', async ({ page }) => {
    await page.goto('/admin/students/new');
    await page.getByLabel('Nome do aluno').fill('Criança de Teste');
    await page.getByLabel('Data de nascimento').fill('2017-02-10');
    await page.getByLabel('CPF').fill(syntheticCpf('135792468'));
    await page.getByLabel('Instrumento').fill('Piano');
    await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Confira os campos indicados' })).toBeFocused();
    await expect(page.getByRole('group', { name: /^Responsável/ })).toHaveAccessibleDescription(/\S/);
    await expectNoSeriousViolations(page, { name: 'A11 menor sem responsável' });
  });

  test('o cadastro completo não deixa o CPF digitado na URL final nem no armazenamento', async ({ page }) => {
    const cpf = syntheticCpf('246813579');
    await page.goto('/admin/students/new');
    await page.getByLabel('Nome do aluno').fill('Ana Beatriz Campos');
    await page.getByLabel('Data de nascimento').fill('1990-08-05');
    await page.getByLabel('CPF').fill(maskedCpf(cpf));
    await page.getByLabel('Instrumento').fill('Canto');
    await page.getByRole('checkbox', { name: 'Bruno Lima' }).check();
    await page.getByRole('button', { name: 'Cadastrar aluno' }).click();

    await expect(page).toHaveURL(/\/admin\/students\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Ana Beatriz Campos' })).toBeVisible();
    await expectValueAbsentFromUrlAndStorage(page, cpf);
    await expectValueAbsentFromUrlAndStorage(page, maskedCpf(cpf));
    await expect(page).not.toHaveTitle(new RegExp(cpf));
    // nenhum texto da tela repete o CPF
    await expect(page.locator('body')).not.toContainText(cpf);
    await expect(page.locator('body')).not.toContainText(maskedCpf(cpf));
  });
});

test.describe('A12 — aluno', () => {
  test('axe da edição: o CPF vem em branco com a dica de manter o atual', async ({ page }) => {
    await page.goto(`/admin/students/${data.students.matteo}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Matteo Mafra' })).toBeVisible();
    const cpf = page.getByLabel('CPF');
    await expect(cpf).toHaveValue('');
    await expect(cpf).toHaveAccessibleDescription('Deixe em branco para manter o CPF atual.');
    await expect(page.getByRole('heading', { level: 2, name: 'Professores do aluno' })).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A12 aluno' });
  });

  test('salvar sem tocar no CPF mantém o aluno e anuncia o aviso de sucesso', async ({ page }) => {
    await page.goto(`/admin/students/${data.students.olavo}`);
    await page.getByLabel('Instrumento').fill('Violão clássico');
    // o aluno semeado é menor de idade e vem sem responsável: salvar exige os dados do responsável
    await page.getByLabel('Nome do responsável').fill('Marta Torrente');
    await page.getByLabel('Telefone do responsável').fill('(11) 91234-5678');
    await page.getByLabel('E-mail do responsável').fill('marta@example.test');
    await page.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Dados salvos.' })).toBeVisible();
    await expect(page.getByLabel('CPF')).toHaveValue('');
    await expectValueAbsentFromUrlAndStorage(page, '12345678909');
  });

  test('o diálogo de inativar prende o foco, Esc cancela e o foco volta ao botão', async ({ page }) => {
    await page.goto(`/admin/students/${data.students.olavo}`);
    const trigger = page.getByRole('button', { name: 'Inativar' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Inativar aluno' });
    await expectNoSeriousViolations(page, { name: 'A12 diálogo inativar' });
    await expectModalDialogBehavior(page, dialog, trigger);
  });
});
