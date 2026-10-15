import { expect, test } from '@playwright/test';
import { openOpenReport, sendAudio, signin, answerQuestions, seed, type Seeding } from './support';

/** Quickstart V6 na Web: relatório em aberto, continuar ou descartar (US3). */
let data: Seeding;

test.beforeEach(async ({ page }) => {
  data = await seed();
  await signin(page);
  await sendAudio(page, data.students.matteo);
  await expect(page.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible();
});

test('continuar de onde parei volta às perguntas e segue até a conferência', async ({ page }) => {
  await openOpenReport(page, 'Matteo Mafra');
  await expect(page.getByText(/pelo portal/)).toBeVisible();
  await page.getByRole('button', { name: 'Continuar de onde parei' }).click();
  await answerQuestions(page);
});

test('descartar cancela o relatório e pede um novo áudio', async ({ page }) => {
  await openOpenReport(page, 'Matteo Mafra');
  await page.getByRole('button', { name: 'Descartar e começar um novo' }).click();
  await page
    .getByRole('dialog', { name: 'Descartar o relatório em aberto?' })
    .getByRole('button', { name: 'Sim, descartar' })
    .click();
  await expect(page.getByRole('button', { name: 'Enviar áudio e gerar relatório' })).toBeVisible();
  const open = await page.request.get(`/api/v1/students/${data.students.matteo}/open-report`);
  expect(open.status()).toBe(204);
});
