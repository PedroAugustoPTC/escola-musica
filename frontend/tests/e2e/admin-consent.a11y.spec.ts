import { expect, test } from '@playwright/test';
import { expectNoSeriousViolations, seed, signInAsAdministrator, type Seeding } from './support';

/**
 * Feature 007 (US2) — telas de consentimento do administrador: axe (sem violação séria ou crítica) em
 * claro e escuro, a 390 e 1440 px (a matriz de temas e larguras vem da configuração do Playwright).
 */
const TEST_TIMEOUT_MS = 180_000;

let data: Seeding;

test.beforeEach(async ({ page }) => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
  await signInAsAdministrator(page);
});

test('A16c pendências vazia', async ({ page }) => {
  await page.goto('/admin/consents/pending');
  await expect(page.getByRole('heading', { level: 1, name: 'Consentimentos pendentes' })).toBeVisible();
  await expect(page.getByText('Nenhuma pendência')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A16c vazia' });
});

test('A16d termos, com a confirmação de publicação', async ({ page }) => {
  await page.goto('/admin/consent-terms');
  await expect(page.getByRole('heading', { level: 1, name: 'Termos de consentimento' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A16d termos' });

  await page.getByLabel('Texto do termo').fill('Texto institucional de teste.');
  await page.getByRole('checkbox', { name: /Exige novo consentimento/ }).check();
  await page.getByRole('button', { name: 'Publicar versão' }).click();
  await expect(page.getByRole('alert')).toContainText('deixam de valer');
  await expectNoSeriousViolations(page, { name: 'A16d confirmação' });
});

test('A16 consentimento no detalhe do aluno, com o formulário de registro aberto', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}`);
  await expect(page.getByRole('heading', { name: 'Consentimento do responsável' })).toBeVisible();
  await expect(page.getByText('Situação: Válido')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A16 detalhe' });

  await page.getByRole('button', { name: 'Registrar consentimento' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar consentimento' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A16 registrar' });
});
