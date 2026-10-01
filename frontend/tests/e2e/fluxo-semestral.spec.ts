import { expect, test } from '@playwright/test';
import { aprovar, entrar, registrarAulaAprovada, semear, type Semeadura } from './suporte';

/** Quickstart V8 e V9 na Web: geração, conferência e aprovação do semestral (US4). */
let dados: Semeadura;

test.beforeEach(async ({ page }) => {
  dados = await semear();
  await entrar(page);
});

test('gera, confere e aprova o semestral; gerar de novo é recusado', async ({ page }) => {
  await registrarAulaAprovada(page, dados.alunos.matteo);

  await page.goto(`/alunos/${dados.alunos.matteo}/semestral`);
  await page.getByRole('button', { name: 'Gerar relatório semestral' }).click();
  await expect(page.getByText('1 relatórios de aula foram considerados nesta consolidação.')).toBeVisible();
  await expect(page.getByRole('article', { name: 'Relatório em texto' })).toContainText('Informações Gerais');

  await aprovar(page, 1);
  await expect(page.getByText(/Ele já consta no histórico do aluno\./)).toBeVisible();

  await page.goto(`/alunos/${dados.alunos.matteo}/semestral`);
  await expect(page.getByText('O relatório semestral deste período já foi aprovado.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();
});

test('período sem aulas aprovadas não gera nada', async ({ page }) => {
  await page.goto(`/alunos/${dados.alunos.olavo}/semestral`);
  await expect(page.getByText(/Nenhum relatório de aula aprovado neste período\./)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gerar relatório semestral' })).toBeDisabled();
});
