import { expect, test } from '@playwright/test';
import {
  expectModalDialogBehavior,
  expectNoSeriousViolations,
  seed,
  sendAudio,
  signInAsAdministrator,
  signInAsTeacher,
  tabUntilFocused,
  type Seeding,
} from './support';

/**
 * T166 — A12, seção "Professores do aluno": o diálogo de retirada de associação é acessível (foco preso,
 * `Esc`, aviso anunciado) nos dois temas e larguras. Retirar nunca é bloqueado: o aviso diz quantos
 * relatórios em aberto ficam congelados.
 */
let data: Seeding;

test.beforeEach(async ({ page }) => {
  data = await seed();
  await signInAsAdministrator(page);
});

test('a seção lista os professores e oferece incluir só quem ainda não está associado', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}`);
  const section = page.getByRole('region', { name: 'Professores do aluno' });
  await expect(section.getByRole('listitem').filter({ hasText: 'Clara Souza' })).toBeVisible();
  const select = section.getByLabel('Incluir professor');
  await expect(select.locator('option')).toHaveText(['Escolha um professor', 'Bruno Lima']);
  await expectNoSeriousViolations(page, { name: 'A12 professores do aluno' });
});

test('o diálogo de retirada prende o foco, Esc cancela sem gravar e o aviso é anunciado', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}`);
  const trigger = page.getByRole('button', { name: 'Retirar Clara Souza' });
  await trigger.focus();
  await page.keyboard.press('Enter');

  const dialog = page.getByRole('dialog', { name: 'Retirar professor do aluno' });
  await expect(dialog).toBeVisible();
  // o aviso é a descrição do diálogo: o leitor de tela o anuncia ao abrir
  await expect(dialog).toHaveAccessibleDescription(
    /Retirar Clara Souza de Matteo Mafra\? O acesso dele\(a\) ao aluno termina agora\. 0 relatório\(s\) em aberto ficarão congelados\./,
  );
  await expectNoSeriousViolations(page, { name: 'A12 diálogo de retirada' });
  await expectModalDialogBehavior(page, dialog, trigger);

  // cancelar não grava
  await expect(page.getByRole('region', { name: 'Professores do aluno' }).getByText('Clara Souza')).toBeVisible();
  const list = await page.request.get(`/api/v1/admin/students/${data.students.matteo}`);
  expect(list.status()).toBe(200);
});

test('retirar com relatório em aberto avisa quantos congelam e não bloqueia', async ({ page, browser, baseURL }) => {
  // a professora Clara abre um relatório (áudio enviado e perguntas pendentes) para o Matteo
  const teacherContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'pt-BR', baseURL });
  try {
    const teacherPage = await teacherContext.newPage();
    await signInAsTeacher(teacherPage);
    await sendAudio(teacherPage, data.students.matteo);
    await expect(teacherPage.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible({
      timeout: 60_000,
    });
  } finally {
    await teacherContext.close();
  }

  await page.goto(`/admin/students/${data.students.matteo}`);
  const trigger = page.getByRole('button', { name: 'Retirar Clara Souza' });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: 'Retirar professor do aluno' });
  await expect(dialog).toHaveAccessibleDescription(/1 relatório\(s\) em aberto ficarão congelados\./);
  await expectNoSeriousViolations(page, { name: 'A12 retirada com congelados' });

  await dialog.getByRole('button', { name: 'Retirar' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status').filter({ hasText: 'Professor retirado do aluno.' })).toBeVisible();
  // o botão da linha some: o foco vai ao título da seção
  await expect(page.getByRole('heading', { level: 2, name: 'Professores do aluno' })).toBeFocused();
  await expect(page.getByRole('button', { name: 'Retirar Clara Souza' })).toHaveCount(0);
});

test('incluir professor funciona só pelo teclado e o aviso é anunciado', async ({ page }) => {
  await page.goto(`/admin/students/${data.students.matteo}`);
  const section = page.getByRole('region', { name: 'Professores do aluno' });
  await tabUntilFocused(page, section.getByLabel('Incluir professor'));
  await page.keyboard.press('ArrowDown');
  await expect(section.getByLabel('Incluir professor')).toHaveValue(/[0-9a-f-]{36}/);
  await tabUntilFocused(page, section.getByRole('button', { name: 'Incluir' }));
  await page.keyboard.press('Enter');
  await expect(section.getByRole('status')).toHaveText('Professor incluído no aluno.');
  await expect(section.getByRole('listitem').filter({ hasText: 'Bruno Lima' })).toBeVisible();
});
