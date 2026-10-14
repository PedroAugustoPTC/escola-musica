import { randomUUID } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
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

  // o Chromium sem interface baixa o PDF em vez de exibi-lo e a aba segue em about:blank; por isso
  // os destinos são observados desde a criação da aba (sem corrida) e em qualquer página do contexto
  const destinos: string[] = [];
  const observar = (alvo: Page) => {
    alvo.on('download', (download) => destinos.push(download.url()));
    alvo.on('framenavigated', (quadro) => destinos.push(quadro.url()));
  };
  observar(page);
  page.context().on('page', observar);

  const [aba] = await Promise.all([
    page.waitForEvent('popup'),
    item.getByRole('button', { name: /Abrir PDF/ }).click(),
  ]);
  expect(aba).toBeTruthy();
  await expect.poll(() => destinos.some((destino) => destino.startsWith('blob:'))).toBe(true);
});

test('aluno e relatório de outro professor não são revelados', async ({ page }) => {
  await page.goto(`/alunos/${dados.alunos.helena}/historico`);
  await expect(page.getByText('Este aluno não está associado a você.')).toBeVisible();
  await expect(page.getByText('Helena Prado')).toHaveCount(0);

  const historico = await page.request.get(`/api/v1/students/${dados.alunos.helena}/history`);
  expect(historico.status()).toBe(403);
  expect((await historico.json()).code).toBe('STUDENT_NOT_ASSIGNED');
  const pdf = await page.request.get(`/api/v1/lesson-reports/${randomUUID()}/official-pdf`);
  expect(pdf.status()).toBe(404);
});
