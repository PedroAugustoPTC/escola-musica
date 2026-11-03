import { expect, test } from '@playwright/test';
import {
  addTeacherWithoutStudents,
  expectValueAbsentFromUrlAndStorage,
  maskedCpf,
  minorBirthDate,
  seed,
  signInAsTeacher,
  syntheticCpf,
} from './support';

/** SC-002: a professora cadastra um aluno completo em até 3 minutos de uso, sem deixar o CPF no navegador. */
const BUDGET_MS = 180_000;
const KEY_DELAY_MS = 40;
/** Folga sobre o orçamento: o teste só pode morrer por timeout depois de a asserção de 180 s ter a chance de falar. */
const TEST_TIMEOUT_MS = BUDGET_MS + 60_000;

test.beforeEach(async () => {
  await seed();
});

test('cadastro completo do primeiro aluno em até 3 minutos, sem o CPF na URL nem no armazenamento (SC-002)', async ({
  page,
}) => {
  // o timeout padrão do Playwright (90 s) mataria o roteiro antes do orçamento de 180 s
  test.setTimeout(TEST_TIMEOUT_MS);
  const teacher = await addTeacherWithoutStudents();
  const cpf = syntheticCpf('975318642');
  const started = Date.now();

  await signInAsTeacher(page, teacher.code);
  await page.getByRole('button', { name: 'Meus alunos' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Meus alunos' })).toBeVisible();
  await page.getByRole('button', { name: 'Cadastrar o primeiro aluno' }).click();

  await page.getByLabel('Nome do aluno').pressSequentially('Lucas Almeida Pires', { delay: KEY_DELAY_MS });
  await page.getByLabel('Data de nascimento').fill('1998-11-23');
  await page.getByLabel('CPF').pressSequentially(maskedCpf(cpf), { delay: KEY_DELAY_MS });
  await page.getByLabel('Instrumento').pressSequentially('Bateria', { delay: KEY_DELAY_MS });
  await page.getByRole('button', { name: 'Cadastrar aluno' }).click();

  await expect(page).toHaveURL(/\/my-students\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Lucas Almeida Pires' })).toBeVisible();
  const elapsed = Date.now() - started;
  console.log(`SC-002: cadastro completo em ${(elapsed / 1000).toFixed(1)} s`);
  expect(elapsed, 'SC-002: o cadastro passou de 3 minutos').toBeLessThanOrEqual(BUDGET_MS);

  await expect(page.getByRole('status').filter({ hasText: /Lucas Almeida Pires/ })).toBeVisible();
  await expectValueAbsentFromUrlAndStorage(page, cpf);
  await expect(page.locator('body')).not.toContainText(cpf);
  await expect(page.locator('body')).not.toContainText(maskedCpf(cpf));

  // o aluno aparece na lista da professora e o CPF não foi para a URL da lista
  await page.goto('/my-students');
  await expect(page.getByText('Lucas Almeida Pires')).toBeVisible();
  await expectValueAbsentFromUrlAndStorage(page, cpf);
});

test('menor de idade com responsável: o cadastro fecha com os dados do responsável', async ({ page }) => {
  const teacher = await addTeacherWithoutStudents();
  await signInAsTeacher(page, teacher.code);
  await page.goto('/my-students/new');
  await page.getByLabel('Nome do aluno').fill('Pedro Henrique Lopes');
  await page.getByLabel('Data de nascimento').fill(minorBirthDate());
  await page.getByLabel('CPF').fill(syntheticCpf('112358132'));
  await page.getByLabel('Instrumento').fill('Violino');
  await page.getByLabel('Nome do responsável').fill('Sandra Lopes');
  await page.getByLabel('Telefone do responsável').fill('(11) 91234-5678');
  await page.getByLabel('E-mail do responsável').fill('sandra@example.test');
  await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
  await expect(page).toHaveURL(/\/my-students\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Pedro Henrique Lopes' })).toBeVisible();
  await expect(page.getByLabel('Nome do responsável')).toHaveValue('Sandra Lopes');
});
