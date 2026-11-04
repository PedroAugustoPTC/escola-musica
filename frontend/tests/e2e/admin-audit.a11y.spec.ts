import { expect, test } from '@playwright/test';
import {
  CODE_CLARA,
  expectNoSeriousViolations,
  registerApprovedLesson,
  seed,
  signInAsAdministrator,
  signin,
  type Seeding,
} from './support';

/**
 * Feature 007 (US1) — A14 "Trilha de auditoria": axe (sem violação séria ou crítica) em claro e escuro, a
 * 390 e 1440 px, com eventos, com o selo de integridade e com erro de período. O professor aprova um
 * relatório antes, de modo que a trilha já tem eventos de aprovação e de leitura.
 */
const TEST_TIMEOUT_MS = 240_000;

let data: Seeding;

test.beforeEach(async ({ page }) => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
  await signin(page, CODE_CLARA);
  await registerApprovedLesson(page, data.students.matteo);
  await page.context().clearCookies();
  await signInAsAdministrator(page);
});

test('A14 com eventos e o selo de integridade', async ({ page }) => {
  await page.goto('/admin/audit');
  await expect(page.getByRole('heading', { level: 1, name: 'Trilha de auditoria' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Relatório de aula aprovado' }).first()).toBeVisible();
  await expect(page.getByText('Ainda não verificada')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A14 eventos' });

  await page.getByRole('button', { name: 'Verificar agora' }).click();
  await expect(page.getByText('Cadeia íntegra')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A14 selo íntegro' });
});

test('A14 filtrada pelo aluno, a partir do atalho do detalhe', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}`);
  await page.getByRole('link', { name: 'Quem acessou os relatórios deste aluno' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Trilha de auditoria' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Relatório de aula aprovado' }).first()).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A14 por aluno' });
});

test('A14 com erro de período', async ({ page }) => {
  await page.goto('/admin/audit');
  await page.getByLabel('De', { exact: true }).fill('2020-01-01');
  await page.getByRole('button', { name: 'Aplicar' }).click();
  const from = page.getByLabel('De', { exact: true });
  await expect(from).toHaveAttribute('aria-invalid', 'true');
  await expect(from).toHaveAccessibleDescription(/\S/);
  await expectNoSeriousViolations(page, { name: 'A14 erro de período' });
});
