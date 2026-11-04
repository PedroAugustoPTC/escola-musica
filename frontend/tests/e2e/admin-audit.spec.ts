import { expect, test } from '@playwright/test';
import {
  CODE_CLARA,
  registerApprovedLesson,
  seed,
  signInAsAdministrator,
  signin,
  type Seeding,
} from './support';

/**
 * Feature 007 (US1) — trilha de auditoria na Web: o professor aprova um relatório; o administrador chega à
 * trilha, vê o evento com o rótulo em português (sem nome de aluno), verifica a integridade e, a partir do
 * detalhe do aluno, chega aos acessos aos relatórios dele em até 3 interações (SC-007).
 */
const INTERACTION_BUDGET = 3;
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

test('o administrador vê o evento de aprovação e verifica a integridade da cadeia', async ({ page }) => {
  await page.goto('/admin');
  await page.getByRole('navigation', { name: 'Área administrativa' }).getByRole('link', { name: 'Auditoria' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Trilha de auditoria' })).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: 'Relatório de aula aprovado' }).first();
  await expect(row).toBeVisible();
  await expect(row).toContainText('Clara Souza');
  await expect(page.getByText('Ainda não verificada')).toBeVisible();

  await page.getByRole('button', { name: 'Verificar agora' }).click();
  await expect(page.getByText('Cadeia íntegra')).toBeVisible();
});

test('do detalhe do aluno às leituras dos relatórios dele em até 3 interações', async ({ page }) => {
  let interactions = 0;
  await page.goto(`/admin/students/${data.students.matteo}`);
  await page.getByRole('link', { name: 'Quem acessou os relatórios deste aluno' }).click();
  interactions += 1;

  await expect(page.getByRole('heading', { level: 1, name: 'Trilha de auditoria' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Relatório de aula aprovado' }).first()).toBeVisible();
  expect(interactions).toBeLessThanOrEqual(INTERACTION_BUDGET);
  // a rota guarda só o identificador do aluno, nunca o nome
  expect(page.url()).toContain(data.students.matteo);
  expect(page.url()).not.toContain('Matteo');
});

test('período acima de 90 dias é recusado com mensagem junto ao campo', async ({ page }) => {
  await page.goto('/admin/audit');
  await page.getByLabel('De', { exact: true }).fill('2020-01-01');
  await page.getByRole('button', { name: 'Aplicar' }).click();

  const from = page.getByLabel('De', { exact: true });
  await expect(from).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('O período pode ter no máximo 90 dias.').first()).toBeVisible();
});
