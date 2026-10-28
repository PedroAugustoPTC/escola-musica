import { expect, test } from '@playwright/test';
import {
  CODE_CLARA,
  expectModalDialogBehavior,
  expectNoSeriousViolations,
  expectValueAbsentFromUrlAndStorage,
  linkTelegram,
  seed,
  signInAsAdministrator,
  signInAsTeacher,
  tabUntilFocused,
  type Seeding,
} from './support';

/**
 * T178 — A08, acesso do professor: os diálogos de reemissão do código e de desvinculação do Telegram são
 * acessíveis (foco preso, `Esc`, descrição anunciada) nos dois temas e larguras.
 */
let data: Seeding;

test.beforeEach(async ({ page }) => {
  data = await seed();
  await linkTelegram(data.clara);
  await signInAsAdministrator(page);
  await page.goto(`/admin/teachers/${data.clara}`);
  await expect(page.getByText('Telegram vinculado')).toBeVisible();
});

test('a tela do professor com Telegram vinculado oferece reemitir, desvincular e inativar', async ({
  page,
}) => {
  await expect(page.getByRole('button', { name: 'Reemitir código' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Desvincular Telegram' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Inativar' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A08 com Telegram vinculado' });
});

test('reemissão: foco preso, Esc cancela, a caixa do Telegram é rotulada e a descrição é anunciada', async ({
  page,
  browser,
  baseURL,
}) => {
  const trigger = page.getByRole('button', { name: 'Reemitir código' });
  await trigger.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Reemitir código de acesso' });
  await expect(dialog).toHaveAccessibleDescription(
    /Reemitir o código de Clara Souza\?.*sessões da Web são encerradas/,
  );
  const endLink = dialog.getByRole('checkbox', { name: 'Também encerrar o vínculo do Telegram' });
  await expect(endLink).not.toBeChecked();
  await expectNoSeriousViolations(page, { name: 'A08 diálogo de reemissão' });
  await expectModalDialogBehavior(page, dialog, trigger);
  // cancelar não reemite: nenhum código novo na tela e o código antigo segue valendo (a professora entra com ele)
  await expect(page.getByRole('region', { name: /Código de acesso de/ })).toHaveCount(0);
  const teacherContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'pt-BR', baseURL });
  try {
    await signInAsTeacher(await teacherContext.newPage(), CODE_CLARA);
  } finally {
    await teacherContext.close();
  }
});

test('reemissão só pelo teclado: marca a caixa, confirma e o código novo aparece uma única vez', async ({
  page,
}) => {
  await page.getByRole('button', { name: 'Reemitir código' }).focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Reemitir código de acesso' });
  await tabUntilFocused(
    page,
    dialog.getByRole('checkbox', { name: 'Também encerrar o vínculo do Telegram' }),
  );
  await page.keyboard.press('Space');
  await expect(dialog.getByRole('checkbox')).toBeChecked();
  await tabUntilFocused(page, dialog.getByRole('button', { name: 'Reemitir' }));
  await page.keyboard.press('Enter');

  const panel = page.getByRole('region', { name: 'Código de acesso de Clara Souza' });
  await expect(panel).toBeVisible();
  await expect(dialog).toBeHidden();
  const code = (await panel.locator('.secret-panel__code').textContent())?.trim() ?? '';
  expect(code).toMatch(/^[A-Z0-9]{6,}$/);
  await expectValueAbsentFromUrlAndStorage(page, code);
  // o vínculo do Telegram caiu junto, como marcado
  await expect(page.getByText('Telegram não vinculado')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A08 código reemitido' });
  // com o código na tela, nenhuma outra emissão
  await expect(page.getByRole('button', { name: 'Reemitir código' })).toBeDisabled();
});

test('desvincular: foco preso, Esc cancela e a confirmação anuncia o resultado', async ({ page }) => {
  const trigger = page.getByRole('button', { name: 'Desvincular Telegram' });
  await trigger.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'Desvincular Telegram' });
  await expect(dialog).toHaveAccessibleDescription(/Desvincular o Telegram de Clara Souza\?/);
  await expectNoSeriousViolations(page, { name: 'A08 diálogo de desvinculação' });
  await expectModalDialogBehavior(page, dialog, trigger);
  await expect(page.getByText('Telegram vinculado')).toBeVisible();

  await trigger.focus();
  await page.keyboard.press('Enter');
  await dialog.getByRole('button', { name: 'Desvincular' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('status').filter({ hasText: 'Vínculo do Telegram encerrado.' })).toBeVisible();
  await expect(page.getByText('Telegram não vinculado')).toBeVisible();
  // o botão some: o foco vai ao título
  await expect(page.getByRole('heading', { level: 1, name: 'Clara Souza' })).toBeFocused();
});
