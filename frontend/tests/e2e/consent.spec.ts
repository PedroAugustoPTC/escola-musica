import { expect, test } from '@playwright/test';
import {
  addTeacherWithoutStudents,
  fillConsent,
  maskedCpf,
  minorBirthDate,
  seed,
  signInAsAdministrator,
  signInAsTeacher,
  syntheticCpf,
} from './support';

/**
 * Feature 007 (US2): o termo vigente aparece no formulário do menor, o consentimento é obrigatório no
 * cadastro e a revogação feita pelo administrador aparece na lista de pendências.
 */
const TEST_TIMEOUT_MS = 180_000;

test.beforeEach(async () => {
  test.setTimeout(TEST_TIMEOUT_MS);
  await seed();
});

test('menor sem consentimento é recusado e com consentimento é cadastrado', async ({ page }) => {
  const teacher = await addTeacherWithoutStudents();
  await signInAsTeacher(page, teacher.code);
  await page.goto('/my-students/new');

  await page.getByLabel('Nome do aluno').fill('Pedro Henrique Lopes');
  await page.getByLabel('Data de nascimento').fill(minorBirthDate());
  await page.getByLabel('CPF').fill(maskedCpf(syntheticCpf('112358132')));
  await page.getByLabel('Nome do responsável').fill('Sandra Lopes');
  await page.getByLabel('Telefone do responsável').fill('(11) 91234-5678');
  await page.getByLabel('E-mail do responsável').fill('sandra@example.test');

  await expect(page.getByRole('group', { name: /Consentimento do responsável/ })).toBeVisible();
  await expect(page.getByText(/Termo de consentimento vigente: versão 1/)).toBeVisible();

  // sem o consentimento: recusa com o erro junto à seção
  await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Confira os campos indicados' })).toBeVisible();
  await expect(page).toHaveURL(/\/my-students\/new$/);

  await fillConsent(page);
  await page.getByRole('button', { name: 'Cadastrar aluno' }).click();
  await expect(page).toHaveURL(/\/my-students\/[0-9a-f-]{36}$/);
});

test('o administrador publica um termo novo e vê as versões', async ({ page }) => {
  await signInAsAdministrator(page);
  await page.goto('/admin/consent-terms');

  await expect(page.getByRole('heading', { level: 1, name: 'Termos de consentimento' })).toBeVisible();
  await page.getByLabel('Texto do termo').fill('Texto institucional da versão dois.');
  await page.getByRole('button', { name: 'Publicar versão' }).click();
  await page.getByRole('button', { name: 'Confirmar publicação' }).click();

  await expect(page.getByText(/Versão 2 ·.*vigente/)).toBeVisible();
});

test('a lista de pendências fica vazia quando todos os menores têm consentimento válido', async ({ page }) => {
  await signInAsAdministrator(page);
  await page.goto('/admin/consents/pending');

  await expect(page.getByRole('heading', { level: 1, name: 'Consentimentos pendentes' })).toBeVisible();
  await expect(page.getByText('Nenhuma pendência')).toBeVisible();
});
