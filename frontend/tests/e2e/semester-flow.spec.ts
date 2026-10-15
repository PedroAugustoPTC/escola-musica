import { expect, test } from '@playwright/test';
import { approve, signin, registerApprovedLesson, seed, type Seeding } from './support';

/** Quickstart V8 e V9 na Web: geração, conferência e aprovação do semestral (US4). */
let data: Seeding;

test.beforeEach(async ({ page }) => {
  data = await seed();
  await signin(page);
});

test('gera, confere e aprova o semestral; gerar de novo é recusado', async ({ page }) => {
  await registerApprovedLesson(page, data.students.matteo);

  await page.goto(`/students/${data.students.matteo}/semester`);
  await page.getByRole('button', { name: 'Gerar relatório semestral' }).click();
  await expect(page.locator('.consolidation')).toHaveText(
    '1 relatório(s) de aula considerado(s) nesta consolidação.',
  );
  await expect(page.getByRole('article', { name: 'Relatório em texto' })).toContainText('Informações Gerais');

  await approve(page, 1);
  await expect(page.getByText(/Ele já consta no histórico do aluno\./)).toBeVisible();

  await page.goto(`/students/${data.students.matteo}/semester`);
  await expect(page.getByText('O relatório semestral deste período já foi aprovado.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();
});

test('período sem aulas aprovadas não gera nada', async ({ page }) => {
  await page.goto(`/students/${data.students.olavo}/semester`);
  await expect(page.getByText(/Nenhum relatório de aula aprovado neste período\./)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();
});
