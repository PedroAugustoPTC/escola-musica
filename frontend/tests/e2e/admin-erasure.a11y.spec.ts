import { expect, test } from '@playwright/test';
import { expectNoSeriousViolations, seed, signInAsAdministrator, type Seeding } from './support';

/**
 * Feature 007 (US4) — telas de eliminação do administrador: axe (sem violação séria ou crítica) em claro e
 * escuro, a 390 e 1440 px (a matriz de temas e larguras vem da configuração do Playwright).
 */
const TEST_TIMEOUT_MS = 180_000;

let data: Seeding;

test.beforeEach(async ({ page }) => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
  await signInAsAdministrator(page);
});

test('A17 pedido, alcance e confirmação', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}/erasure`);
  await expect(page.getByRole('heading', { level: 1, name: 'Eliminar dados do aluno' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A17 pedido' });

  await page.getByLabel('Nome de quem pediu').fill('Responsável de Teste');
  await page.getByLabel('Protocolo ou referência').fill('PROT-A11Y-1');
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.getByRole('list', { name: 'O que será eliminado' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A17 alcance e confirmação' });
});

test('A17c lista de pedidos', async ({ page }) => {
  await page.goto('/admin/erasure-requests');
  await expect(page.getByRole('heading', { level: 1, name: 'Pedidos de eliminação' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A17c lista' });
});
