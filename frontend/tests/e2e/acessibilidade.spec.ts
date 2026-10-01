import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import {
  abrirRelatorioEmAberto,
  CODIGO_CLARA,
  enviarAudio,
  entrar,
  registrarAulaAprovada,
  semear,
  type Semeadura,
} from './suporte';

/**
 * SC-011: axe sem violações AA nas telas 01 a 12 (e variações), a 390 px e 1440 px, nos temas claro
 * e escuro, sem rolagem horizontal; e o acesso completo por teclado.
 */
const LARGURAS = [390, 1440] as const;
const TEMAS = ['light', 'dark'] as const;

async function verificar(page: Page, nome: string) {
  await page.waitForLoadState('domcontentloaded');
  const resultado = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .exclude('iframe')
    .analyze();
  const resumo = resultado.violations.map((v) => `${v.id} (${v.nodes.map((n) => n.target).join(' | ')})`);
  expect(resumo, `${nome}`).toEqual([]);
  const rolagem = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(rolagem, `${nome}: rolagem horizontal`).toBeLessThanOrEqual(0);
}

for (const largura of LARGURAS) {
  for (const tema of TEMAS) {
    test.describe(`${largura}px, tema ${tema}`, () => {
      let dados: Semeadura;

      test.beforeEach(async ({ page }) => {
        dados = await semear();
        await page.setViewportSize({ width: largura, height: 900 });
        await page.emulateMedia({ colorScheme: tema });
      });

      test('telas 01, 02, 03, 05 e variações vazias de 10 e 12', async ({ page }) => {
        await page.goto('/entrar');
        await verificar(page, '01 entrar');
        await entrar(page);
        await verificar(page, '02 painel');
        await page.goto('/alunos');
        await verificar(page, '03 alunos');
        await page.goto(`/alunos/${dados.alunos.olavo}/semestral`);
        await expect(page.getByText(/Nenhum relatório de aula aprovado/)).toBeVisible();
        await verificar(page, '10b semestral sem aulas');
        await page.goto(`/alunos/${dados.alunos.olavo}/historico`);
        await expect(page.getByText('Este aluno ainda não tem relatórios aprovados.')).toBeVisible();
        await verificar(page, '12b histórico vazio');
        await page.goto(`/alunos/${dados.alunos.matteo}/nova-aula`);
        await verificar(page, '05 gravação');
      });

      test('telas 04, 07, 08, 09, 10, 11 e 12', async ({ page }) => {
        await entrar(page);
        await enviarAudio(page, dados.alunos.matteo);
        await expect(page.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible();
        await verificar(page, '07 perguntas');
        await abrirRelatorioEmAberto(page, 'Matteo Mafra');
        await verificar(page, '04 relatório em aberto');
        await page.getByRole('button', { name: 'Continuar de onde parei' }).click();
        await page.getByLabel('Sua resposta').fill('Estudou sim.');
        await page.getByRole('button', { name: 'Enviar resposta' }).click();
        await expect(
          page.getByRole('heading', { name: 'Confira o relatório antes de aprovar' }),
        ).toBeVisible();
        await verificar(page, '08 conferência');

        await registrarAulaAprovada(page, dados.alunos.miguel);
        await verificar(page, '09 aprovado');
        await page.goto(`/alunos/${dados.alunos.miguel}/semestral`);
        await expect(page.getByRole('button', { name: 'Gerar relatório semestral' })).toBeEnabled();
        await verificar(page, '10 semestral');
        await page.getByRole('button', { name: 'Gerar relatório semestral' }).click();
        await expect(page.getByRole('article', { name: 'Relatório em texto' })).toBeVisible();
        await verificar(page, '11 conferência semestral');
        await page.goto(`/alunos/${dados.alunos.miguel}/historico`);
        await expect(page.getByRole('list', { name: /Histórico de/ })).toBeVisible();
        await verificar(page, '12 histórico');
      });
    });
  }
}

test('acesso completo por teclado', async ({ page }) => {
  await semear();
  await page.goto('/entrar');
  const campo = page.getByLabel('Código de acesso');
  for (let i = 0; i < 10 && !(await campo.evaluate((e) => e === document.activeElement)); i++) {
    await page.keyboard.press('Tab');
  }
  await expect(campo).toBeFocused();
  await page.keyboard.type(CODIGO_CLARA);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Olá, Clara' })).toBeVisible();
});
