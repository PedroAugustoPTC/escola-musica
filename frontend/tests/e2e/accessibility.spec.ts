import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import {
  openOpenReport,
  CODE_CLARA,
  sendAudio,
  signin,
  registerApprovedLesson,
  seed,
  type Seeding,
} from './support';

/**
 * SC-011: axe sem violações AA nas telas 01 a 12 (e variações), a 390 px e 1440 px, nos temas claro
 * e escuro, sem rolagem horizontal; e o acesso completo por teclado.
 */
const WIDTHS = [390, 1440] as const;
const THEMES = ['light', 'dark'] as const;

async function verify(page: Page, name: string) {
  await page.waitForLoadState('domcontentloaded');
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .exclude('iframe')
    .analyze();
  const summary = result.violations.map((v) => `${v.id} (${v.nodes.map((n) => n.target).join(' | ')})`);
  expect(summary, `${name}`).toEqual([]);
  const scroll = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(scroll, `${name}: rolagem horizontal`).toBeLessThanOrEqual(0);
}

for (const width of WIDTHS) {
  for (const theme of THEMES) {
    test.describe(`${width}px, tema ${theme}`, () => {
      let data: Seeding;

      test.beforeEach(async ({ page }) => {
        data = await seed();
        await page.setViewportSize({ width, height: 900 });
        await page.emulateMedia({ colorScheme: theme });
      });

      test('telas 01, 02, 03, 05 e variações vazias de 10 e 12', async ({ page }) => {
        await page.goto('/sign-in');
        await verify(page, '01 entrar');
        await signin(page);
        await verify(page, '02 painel');
        await page.goto('/students');
        await verify(page, '03 alunos');
        await page.goto(`/students/${data.students.olavo}/semester`);
        await expect(page.getByText(/Nenhum relatório de aula aprovado/)).toBeVisible();
        await verify(page, '10b semestral sem aulas');
        await page.goto(`/students/${data.students.olavo}/history`);
        await expect(page.getByText('Este aluno ainda não tem relatórios aprovados.')).toBeVisible();
        await verify(page, '12b histórico vazio');
        await page.goto(`/students/${data.students.matteo}/new-lesson`);
        await verify(page, '05 gravação');
      });

      test('telas 04, 07, 08, 09, 10, 11 e 12', async ({ page }) => {
        await signin(page);
        await sendAudio(page, data.students.matteo);
        await expect(page.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible();
        await verify(page, '07 perguntas');
        await openOpenReport(page, 'Matteo Mafra');
        await verify(page, '04 relatório em aberto');
        await page.getByRole('button', { name: 'Continuar de onde parei' }).click();
        await page.getByLabel('Sua resposta').fill('Estudou sim.');
        await page.getByRole('button', { name: 'Enviar resposta' }).click();
        await expect(
          page.getByRole('heading', { name: 'Confira o relatório antes de aprovar' }),
        ).toBeVisible();
        await verify(page, '08 conferência');

        await registerApprovedLesson(page, data.students.miguel);
        await verify(page, '09 aprovado');
        await page.goto(`/students/${data.students.miguel}/semester`);
        await expect(page.getByRole('button', { name: 'Gerar relatório semestral' })).toBeEnabled();
        await verify(page, '10 semestral');
        await page.getByRole('button', { name: 'Gerar relatório semestral' }).click();
        await expect(page.getByRole('article', { name: 'Relatório em texto' })).toBeVisible();
        await verify(page, '11 conferência semestral');
        await page.goto(`/students/${data.students.miguel}/history`);
        await expect(page.getByRole('list', { name: /Histórico de/ })).toBeVisible();
        await verify(page, '12 histórico');
      });
    });
  }
}

test('acesso completo por teclado', async ({ page }) => {
  await seed();
  await page.goto('/sign-in');
  const field = page.getByLabel('Código de acesso');
  for (let i = 0; i < 10 && !(await field.evaluate((e) => e === document.activeElement)); i++) {
    await page.keyboard.press('Tab');
  }
  await expect(field).toBeFocused();
  await page.keyboard.type(CODE_CLARA);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Olá, Clara' })).toBeVisible();
});
