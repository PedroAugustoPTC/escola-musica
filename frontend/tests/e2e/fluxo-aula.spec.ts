import { expect, test } from '@playwright/test';
import { aprovar, enviarAudio, entrar, responderPerguntas, semear, type Semeadura } from './suporte';

/** Quickstart V1, V3 e V15 na Web: acesso, registro com perguntas, alteração, aprovação e saída. */
let dados: Semeadura;

test.beforeEach(async ({ page }) => {
  dados = await semear();
  await entrar(page);
});

test('painel mostra a professora e só os alunos dela', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'Olá, Clara' })).toBeVisible();
  await expect(page.getByText('Matteo Mafra').first()).toBeVisible();
  await expect(page.getByText('Olavo Torrente').first()).toBeVisible();
  await expect(page.getByText('Helena Prado')).toHaveCount(0);
});

test('registra a aula, responde às perguntas, altera e aprova', async ({ page }) => {
  await enviarAudio(page, dados.alunos.matteo);
  await responderPerguntas(page);

  await expect(page.getByText('Versão 1', { exact: true })).toBeVisible();
  await expect(page.getByText(/Estudo nº 5 de Czerny/)).toBeVisible();
  // no desktop o texto e o PDF ficam lado a lado (as abas "Texto"/"Ver PDF" são do celular)
  await expect(page.locator('iframe[title^="PDF do relatório"]')).toBeVisible();

  await page.getByLabel(/Precisa ajustar algo\?/).fill('Diga que a aula foi ótima.');
  await page.getByRole('button', { name: 'Solicitar alteração' }).click();
  await expect(page.getByText('Alteração aplicada. Confira a versão 2.')).toBeVisible();

  await aprovar(page, 2);
  await expect(page.getByText(/Ele já consta no histórico do aluno\./)).toBeVisible();
  await page.getByRole('button', { name: 'Ver histórico' }).click();
  await expect(page).toHaveURL(new RegExp(`/alunos/${dados.alunos.matteo}/historico$`));
  const itens = page.getByRole('list', { name: 'Histórico de Matteo Mafra' }).getByRole('listitem');
  await expect(itens).toHaveCount(1);
  await expect(itens.first()).toContainText(/Aula de \d{2}\/\d{2}\/\d{4}/);
});

test('encerrar sessão invalida o acesso no servidor', async ({ page }) => {
  await page.getByRole('button', { name: 'Clara Souza' }).click();
  await page.getByRole('menuitem', { name: 'Encerrar sessão' }).click();
  await expect(page).toHaveURL(/\/entrar$/);
  const resposta = await page.request.get('/api/v1/inicio');
  expect(resposta.status()).toBe(401);
});
