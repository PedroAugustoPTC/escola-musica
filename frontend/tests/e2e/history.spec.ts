import { randomUUID } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import { signin, registerApprovedLesson, seed, type Seeding } from './support';

/** Quickstart V10 e V11 na Web: histórico, PDF oficial e acesso cruzado (US5). */
let data: Seeding;

test.beforeEach(async ({ page }) => {
  data = await seed();
  await signin(page);
});

test('vazio oferece registrar aula', async ({ page }) => {
  await page.goto(`/students/${data.students.olavo}/history`);
  await expect(page.getByText('Este aluno ainda não tem relatórios aprovados.')).toBeVisible();
});

test('lista o aprovado e abre o PDF oficial numa nova aba', async ({ page }) => {
  await registerApprovedLesson(page, data.students.matteo);
  await page.goto(`/students/${data.students.matteo}/history`);
  const item = page.getByRole('list', { name: 'Histórico de Matteo Mafra' }).getByRole('listitem').first();
  await expect(item).toContainText(/Aula de \d{2}\/\d{2}\/\d{4}/);
  await expect(item).toContainText(/Aprovado em \d{2}\/\d{2}\/\d{4} às \d{2}:\d{2}/);

  // o Chromium sem interface baixa o PDF em vez de exibi-lo e a aba segue em about:blank; por isso
  // os destinos são observados desde a criação da aba (sem corrida) e em qualquer página do contexto
  const destinations: string[] = [];
  const observe = (target: Page) => {
    target.on('download', (download) => destinations.push(download.url()));
    target.on('framenavigated', (board) => destinations.push(board.url()));
  };
  observe(page);
  page.context().on('page', observe);

  const [tab] = await Promise.all([
    page.waitForEvent('popup'),
    item.getByRole('button', { name: /Abrir PDF/ }).click(),
  ]);
  expect(tab).toBeTruthy();
  await expect.poll(() => destinations.some((destination) => destination.startsWith('blob:'))).toBe(true);
});

test('aluno e relatório de outro professor não são revelados', async ({ page }) => {
  await page.goto(`/students/${data.students.helena}/history`);
  await expect(page.getByText('Não encontramos o que você procurou.')).toBeVisible();
  await expect(page.getByText('Helena Prado')).toHaveCount(0);

  const history = await page.request.get(`/api/v1/students/${data.students.helena}/history`);
  expect(history.status()).toBe(404);
  expect((await history.json()).code).toBe('RESOURCE_NOT_FOUND');
  const pdf = await page.request.get(`/api/v1/lesson-reports/${randomUUID()}/official-pdf`);
  expect(pdf.status()).toBe(404);
});
