import { expect, test } from '@playwright/test';
import {
  abrirRelatorioEmAberto,
  enviarAudio,
  entrar,
  responderPerguntas,
  semear,
  type Semeadura,
} from './suporte';

/** Quickstart V6 na Web: relatório em aberto, continuar ou descartar (US3). */
let dados: Semeadura;

test.beforeEach(async ({ page }) => {
  dados = await semear();
  await entrar(page);
  await enviarAudio(page, dados.alunos.matteo);
  await expect(page.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible();
});

test('continuar de onde parei volta às perguntas e segue até a conferência', async ({ page }) => {
  await abrirRelatorioEmAberto(page, 'Matteo Mafra');
  await expect(page.getByText(/pelo portal/)).toBeVisible();
  await page.getByRole('button', { name: 'Continuar de onde parei' }).click();
  await responderPerguntas(page);
});

test('descartar cancela o relatório e pede um novo áudio', async ({ page }) => {
  await abrirRelatorioEmAberto(page, 'Matteo Mafra');
  await page.getByRole('button', { name: 'Descartar e começar um novo' }).click();
  await page
    .getByRole('dialog', { name: 'Descartar o relatório em aberto?' })
    .getByRole('button', { name: 'Sim, descartar' })
    .click();
  await expect(page.getByRole('button', { name: 'Enviar áudio e gerar relatório' })).toBeVisible();
  const aberto = await page.request.get(`/api/v1/students/${dados.alunos.matteo}/open-report`);
  expect(aberto.status()).toBe(204);
});
