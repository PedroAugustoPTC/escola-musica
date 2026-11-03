import { expect, test, type Page } from '@playwright/test';
import {
  addTeacherWithoutStudents,
  CPF_HELENA,
  CPF_MATTEO,
  expectFocusOrder,
  expectModalDialogBehavior,
  expectNoSeriousViolations,
  expectValueAbsentFromUrlAndStorage,
  maskedCpf,
  minorBirthDate,
  seed,
  signInAsTeacher,
  syntheticCpf,
  tabUntilFocused,
  type Seeding,
} from './support';

/**
 * T146 — P01 a P03 ("Meus alunos", portal do professor): axe nos dois temas e larguras e uso só por
 * teclado. O escopo do professor é decidido no servidor: aluno de outro professor nunca aparece.
 */
let data: Seeding;

/** O aluno semeado é menor de idade e vem sem responsável: salvar a edição exige os dados do responsável. */
async function fillGuardian(page: Page): Promise<void> {
  await page.getByLabel('Nome do responsável').fill('Marta Torrente');
  await page.getByLabel('Telefone do responsável').fill('(11) 91234-5678');
  await page.getByLabel('E-mail do responsável').fill('marta@example.test');
}

test.beforeEach(async () => {
  data = await seed();
});

test.describe('P01 — meus alunos', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTeacher(page);
  });

  test('axe, contagem e lista só com os alunos da professora; o painel tem o atalho', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Meus alunos' })).toBeVisible();
    await page.goto('/my-students');
    await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
    await expect(page.getByRole('heading', { level: 1, name: 'Meus alunos' })).toBeVisible();
    await expect(page.getByText('3 aluno(s)')).toBeVisible();
    await expect(page.getByText('Matteo Mafra')).toBeVisible();
    await expect(page.getByText('Helena Prado')).toHaveCount(0);
    await expectNoSeriousViolations(page, { name: 'P01 meus alunos' });
  });

  test('busca e filtro de situação pelo teclado; busca por CPF é recusada e anunciada', async ({ page }) => {
    await page.goto('/my-students');
    await tabUntilFocused(page, page.getByRole('searchbox', { name: 'Buscar entre os meus alunos' }));
    await page.keyboard.type('Violino');
    await page.keyboard.press('Enter');
    await expect(page.getByText('Miguel de Castro Torrente')).toBeVisible();
    await expect(page.getByText('Matteo Mafra')).toHaveCount(0);

    await page.getByRole('searchbox', { name: 'Buscar entre os meus alunos' }).fill('Zzz inexistente');
    await page.keyboard.press('Enter');
    await expect(page.getByText('Nenhum aluno encontrado para “Zzz inexistente”.')).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'P01 busca sem resultado' });

    const cpf = CPF_MATTEO;
    await page.getByRole('searchbox', { name: 'Buscar entre os meus alunos' }).fill(maskedCpf(cpf));
    await page.keyboard.press('Enter');
    await expect(page.getByRole('alert')).toContainText('A busca por CPF não existe.');
    await expect(page.getByRole('searchbox', { name: 'Buscar entre os meus alunos' })).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    await expectValueAbsentFromUrlAndStorage(page, cpf);
  });

  test('o filtro Inativos mostra o estado vazio da situação', async ({ page }) => {
    await page.goto('/my-students');
    await page.getByRole('button', { name: 'Inativos' }).click();
    await expect(page.getByText('Nenhum aluno nesta situação.')).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'P01 sem inativos' });
  });

  test('a ordem de foco chega ao botão Cadastrar aluno, à busca e à lista', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/my-students');
    await expectFocusOrder(page, [
      page.getByRole('button', { name: 'Cadastrar aluno' }),
      page.getByRole('searchbox', { name: 'Buscar entre os meus alunos' }),
      page.getByRole('button', { name: 'Ativos' }),
      page.getByRole('link', { name: /Matteo Mafra/ }),
    ]);
  });
});

test.describe('P01 — primeiro acesso, sem alunos', () => {
  test('o estado vazio oferece Cadastrar o primeiro aluno', async ({ page }) => {
    const teacher = await addTeacherWithoutStudents();
    await signInAsTeacher(page, teacher.code);
    await page.goto('/my-students');
    await expect(page.getByText('Você ainda não tem alunos. Cadastre o primeiro.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cadastrar o primeiro aluno' })).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'P01 vazio' });
  });
});

test.describe('P02 — cadastrar aluno', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTeacher(page);
    await page.goto('/my-students/new');
  });

  test('axe, ordem de foco e a seção do responsável sempre visível, sem professores', async ({ page }) => {
    await expect(page.getByRole('heading', { level: 1, name: 'Cadastrar aluno' })).toBeVisible();
    await expect(
      page.getByRole('group', { name: 'Responsável (obrigatório para menores de 18 anos)' }),
    ).toBeVisible();
    await expect(page.getByRole('group', { name: 'Professores' })).toHaveCount(0);
    await expectNoSeriousViolations(page, { name: 'P02 cadastrar aluno' });
    await page.setViewportSize({ width: 1440, height: 900 });
    await expectFocusOrder(page, [
      page.getByLabel('Nome do aluno'),
      page.getByLabel('Data de nascimento'),
      page.getByLabel('CPF'),
      page.getByLabel('Instrumento'),
      page.getByLabel('Nome do responsável'),
      page.getByLabel('Telefone do responsável'),
      page.getByLabel('E-mail do responsável'),
      page.getByRole('button', { name: 'Cadastrar aluno' }),
      page.getByRole('button', { name: 'Cancelar' }),
    ]);
  });

  test('erros por campo anunciados: CPF inválido e menor sem responsável', async ({ page }) => {
    await page.getByLabel('Nome do aluno').fill('Criança de Teste');
    await page.getByLabel('Data de nascimento').fill(minorBirthDate());
    await page.getByLabel('CPF').fill('123.456.789-00');
    await page.getByLabel('Instrumento').fill('Piano');
    await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
    const summary = page.getByRole('alert').filter({ hasText: 'Confira os campos indicados' });
    await expect(summary).toBeFocused();
    await expect(page.getByLabel('CPF')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByLabel('CPF')).toHaveAccessibleDescription(/\S/);
    await expectNoSeriousViolations(page, { name: 'P02 com erros' });
    await expectValueAbsentFromUrlAndStorage(page, '12345678900');
  });

  test('CPF de aluno de outro professor: mensagem neutra, sem nome nem professor', async ({ page }) => {
    await page.getByLabel('Nome do aluno').fill('Aluno Alheio');
    await page.getByLabel('Data de nascimento').fill('1992-01-01');
    await page.getByLabel('CPF').fill(CPF_HELENA);
    await page.getByLabel('Instrumento').fill('Flauta');
    await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
    await expect(page.getByLabel('CPF')).toHaveAccessibleDescription(
      /Não foi possível usar este CPF\. Procure o administrador\./,
    );
    await expect(page.locator('body')).not.toContainText('Helena Prado');
    await expect(page.locator('body')).not.toContainText('Bruno Lima');
    await expectValueAbsentFromUrlAndStorage(page, CPF_HELENA);
    await expectNoSeriousViolations(page, { name: 'P02 CPF indisponível' });
  });
});

test.describe('P03 — aluno', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsTeacher(page);
  });

  test('axe da edição, com o CPF em branco e a dica de manter o atual', async ({ page }) => {
    await page.goto(`/my-students/${data.students.matteo}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Matteo Mafra' })).toBeVisible();
    await expect(page.getByLabel('CPF')).toHaveValue('');
    await expect(page.getByLabel('CPF')).toHaveAccessibleDescription(
      'Deixe em branco para manter o CPF atual.',
    );
    await expectNoSeriousViolations(page, { name: 'P03 aluno' });
  });

  test('o diálogo de inativar prende o foco, Esc cancela e o foco volta ao botão', async ({ page }) => {
    await page.goto(`/my-students/${data.students.olavo}`);
    const trigger = page.getByRole('button', { name: 'Inativar aluno' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Inativar aluno' });
    await expect(dialog).toHaveAccessibleDescription(/Inativar Olavo Torrente\?/);
    await expectNoSeriousViolations(page, { name: 'P03 diálogo de inativação' });
    await expectModalDialogBehavior(page, dialog, trigger);
  });

  test('aluno de outro professor não é revelado', async ({ page }) => {
    await page.goto(`/my-students/${data.students.helena}`);
    await expect(page.getByText('Helena Prado')).toHaveCount(0);
    await expect(page.getByText('Não encontramos o que você procurou.')).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'P03 aluno inexistente' });
  });

  test('o CPF novo usado na edição não fica na URL nem no armazenamento', async ({ page }) => {
    const cpf = syntheticCpf('864209753');
    await page.goto(`/my-students/${data.students.olavo}`);
    await fillGuardian(page);
    await page.getByLabel('CPF').fill(maskedCpf(cpf));
    await page.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page.getByRole('status').filter({ hasText: /Olavo Torrente/ })).toBeVisible();
    await expectValueAbsentFromUrlAndStorage(page, cpf);
    await expect(page.locator('body')).not.toContainText(cpf);
  });
});
