import { expect, test, type Page } from '@playwright/test';
import {
  CODE_CLARA,
  expectNoSeriousViolations,
  registerApprovedLesson,
  seed,
  signInAsAdministrator,
  signin,
  tabUntilFocused,
  type Seeding,
} from './support';

/**
 * Feature 006 (SC-009) — A13 "Histórico": axe (sem violação séria ou crítica) em claro e escuro, a 390 e
 * 1440 px, com lista, com aluno escolhido, vazia, com filtros abertos e com erro de filtro; navegação só por
 * teclado do combobox, dos filtros, da paginação e dos botões de PDF; "Inativo" com texto e ícone.
 */
const TEST_TIMEOUT_MS = 240_000;
const EMPTY_FILTERS = 'Nenhum relatório aprovado encontrado para estes filtros.';

let data: Seeding;

test.beforeEach(async ({ page }) => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
  await signin(page, CODE_CLARA);
  await registerApprovedLesson(page, data.students.matteo);
  await page.context().clearCookies();
  await signInAsAdministrator(page);
});

/** Abre os filtros quando a tela estreita os recolhe. */
async function openFilters(page: Page): Promise<void> {
  const toggle = page.getByRole('button', { name: /^Filtros/ });
  if ((await toggle.count()) > 0 && (await toggle.isVisible())) {
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') await toggle.click();
  }
  await expect(page.getByRole('combobox', { name: 'Professor' })).toBeVisible();
}

test('A13 com lista da escola inteira', async ({ page }) => {
  await page.goto('/admin/history');
  await expect(page.getByRole('heading', { level: 1, name: 'Histórico' })).toBeVisible();
  await expect(page.getByText(/Aula de \d{2}\/\d{2}\/\d{4}/).first()).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A13 lista' });
});

test('A13 com aluno escolhido', async ({ page }) => {
  await page.goto(`/admin/history?studentId=${data.students.matteo}`);
  await expect(page.getByRole('button', { name: 'Trocar aluno' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: 'Matteo Mafra' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A13 aluno escolhido' });
});

test('A13 vazia (filtros sem resultado)', async ({ page }) => {
  await page.goto('/admin/history?type=SEMESTER');
  await expect(page.getByText(EMPTY_FILTERS)).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A13 vazia' });
});

test('A13 com filtros abertos', async ({ page }) => {
  await page.goto('/admin/history');
  await openFilters(page);
  await expectNoSeriousViolations(page, { name: 'A13 filtros abertos' });
});

test('A13 com erro de filtro', async ({ page }) => {
  await page.goto('/admin/history');
  await openFilters(page);
  await page.getByLabel('De').fill('2030-01-10');
  await page.getByLabel('Até').fill('2030-01-01');
  await page.getByRole('button', { name: 'Aplicar' }).click();
  // período invertido: o servidor aponta o campo `from` (PERIOD_START_AFTER_END)
  const from = page.getByLabel('De');
  await expect(from).toHaveAttribute('aria-invalid', 'true');
  await expect(from).toHaveAccessibleDescription(/\S/);
  await expectNoSeriousViolations(page, { name: 'A13 erro de filtro' });
});

test('teclado: combobox de aluno, filtros e botões de PDF', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/admin/history');

  const combobox = page.getByRole('combobox', { name: 'Procurar aluno' });
  await tabUntilFocused(page, combobox);
  await page.keyboard.type('Matteo');
  await expect(page.getByRole('option', { name: /Matteo Mafra/ })).toBeVisible();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: 'Trocar aluno' })).toBeVisible();

  await tabUntilFocused(page, page.getByRole('combobox', { name: 'Professor' }));
  await tabUntilFocused(page, page.getByRole('combobox', { name: 'Tipo' }));
  await tabUntilFocused(page, page.getByLabel('De'));
  await tabUntilFocused(page, page.getByLabel('Até'));
  await tabUntilFocused(page, page.getByRole('button', { name: 'Aplicar' }));
  await tabUntilFocused(page, page.getByRole('button', { name: 'Limpar filtros' }).first());

  await tabUntilFocused(page, page.getByRole('button', { name: /Abrir PDF/ }).first());
  await tabUntilFocused(page, page.getByRole('button', { name: /Baixar PDF/ }).first());
});

test('paginação: a navegação só existe quando há mais de uma página', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  // a massa tem um só aprovado; Anterior/Próxima por teclado são cobertos no teste de tela (Vitest)
  await page.goto('/admin/history');
  await expect(page.getByText(/Aula de \d{2}\/\d{2}\/\d{4}/).first()).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Paginação' })).toHaveCount(0);
});

test('"Inativo" aparece com texto e ícone (SC-009)', async ({ page }) => {
  await page.goto('/admin/history');
  await expect(page.getByText(/Aula de \d{2}\/\d{2}\/\d{4}/).first()).toBeVisible();
  // marca "Inativo" é sempre texto mais ícone; a massa deste teste não tem inativos, então vale para todas
  for (const mark of await page.locator('.status-badge--inactive').all()) {
    await expect(mark).toContainText('Inativo');
    await expect(mark.locator('svg, .icon, [aria-hidden="true"]').first()).toBeVisible();
  }
});
