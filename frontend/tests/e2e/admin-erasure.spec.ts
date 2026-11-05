import { expect, test } from '@playwright/test';
import { seed, signInAsAdministrator, type Seeding } from './support';

/**
 * Feature 007 (US4) — o administrador elimina os dados de um aluno a pedido: registra o pedido, vê o
 * alcance, confirma com a palavra e chega ao comprovante, que não traz dado do aluno. Depois o aluno some
 * da lista e do detalhe.
 */
const TEST_TIMEOUT_MS = 240_000;

let data: Seeding;

test.beforeEach(async ({ page }) => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
  await signInAsAdministrator(page);
});

test('elimina o aluno em três passos e mostra o comprovante sem dado pessoal', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}`);
  await page.getByRole('button', { name: 'Eliminar dados' }).click();

  await expect(page.getByText(/Passo 1 de 3/)).toBeVisible();
  await page.getByLabel('Nome de quem pediu').fill('Responsável de Teste');
  await page.getByLabel('Protocolo ou referência').fill('PROT-E2E-1');
  await page.getByRole('button', { name: 'Continuar' }).click();

  await expect(page.getByText(/Passo 3 de 3/)).toBeVisible();
  await expect(page.getByRole('list', { name: 'O que será eliminado' })).toBeVisible();
  await expect(page.getByRole('timer')).toContainText('A confirmação vale por mais');

  const confirm = page.getByRole('button', { name: 'Eliminar dados' });
  await expect(confirm).toBeDisabled();
  await page.getByLabel('Digite ELIMINAR para confirmar').fill('ELIMINAR');
  await confirm.click();

  await expect(page.getByRole('heading', { level: 1, name: 'Comprovante de eliminação de dados' })).toBeVisible();
  await expect(page.getByText('PROT-E2E-1')).toBeVisible();
  const text = (await page.locator('main, body').first().textContent()) ?? '';
  expect(text).not.toContain('Matteo');
  expect(text).not.toContain('Responsável de Teste');

  await page.goto('/admin/students');
  await expect(page.getByRole('heading', { level: 1, name: 'Alunos' })).toBeVisible();
  await expect(page.getByText('Matteo Mafra')).toHaveCount(0);

  await page.goto('/admin/erasure-requests');
  await expect(page.getByText('Concluído').first()).toBeVisible();
});
