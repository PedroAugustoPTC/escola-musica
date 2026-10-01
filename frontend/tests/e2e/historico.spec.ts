import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { entrar, registrarAulaAprovada, semear, type Semeadura } from './suporte';

/** Quickstart V10 e V11 na Web: histórico, PDF oficial e acesso cruzado (US5). */
let dados: Semeadura;

test.beforeEach(async ({ page }) => {
  dados = await semear();
  await entrar(page);
});

test('vazio oferece registrar aula', async ({ page }) => {
  await page.goto(`/alunos/${dados.alunos.olavo}/historico`);
  await expect(page.getByText('Este aluno ainda não tem relatórios aprovados.')).toBeVisible();
});

test('lista o aprovado e abre o PDF oficial numa nova aba', async ({ page }) => {
  await registrarAulaAprovada(page, dados.alunos.matteo);
  await page.goto(`/alunos/${dados.alunos.matteo}/historico`);
  const item = page.getByRole('list', { name: 'Histórico de Matteo Mafra' }).getByRole('listitem').first();
  await expect(item).toContainText(/Aula de \d{2}\/\d{2}\/\d{4}/);
  await expect(item).toContainText(/Aprovado em \d{2}\/\d{2}\/\d{4} às \d{2}:\d{2}/);

  const [aba] = await Promise.all([
    page.waitForEvent('popup'),
    item.getByRole('button', { name: /Abrir PDF/ }).click(),
  ]);
  // o Chromium sem interface baixa o PDF em vez de exibi-lo: vale o blob aberto ou baixado na aba
  const destino = await aba
    .waitForEvent('download', { timeout: 20_000 })
    .then((download) => download.url())
    .catch(() => aba.url());
  expect(destino).toMatch(/^blob:/);
});

test('aluno e relatório de outro professor não são revelados', async ({ page }) => {
  await page.goto(`/alunos/${dados.alunos.helena}/historico`);
  await expect(page.getByText('Este aluno não está associado a você.')).toBeVisible();
  await expect(page.getByText('Helena Prado')).toHaveCount(0);

  const historico = await page.request.get(`/api/v1/alunos/${dados.alunos.helena}/historico`);
  expect(historico.status()).toBe(403);
  expect((await historico.json()).codigo).toBe('ALUNO_NAO_ASSOCIADO');
  const pdf = await page.request.get(`/api/v1/relatorios-aula/${randomUUID()}/pdf-oficial`);
  expect(pdf.status()).toBe(404);
});
