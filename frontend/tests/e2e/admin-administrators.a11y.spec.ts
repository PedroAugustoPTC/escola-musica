import { expect, test, type Page } from '@playwright/test';
import {
  ADMIN_EMAIL,
  expectFocusOrder,
  expectModalDialogBehavior,
  expectNoSeriousViolations,
  expectValueAbsentFromUrlAndStorage,
  randomPassword,
  seed,
  signInAsAdministrator,
  tabUntilFocused,
} from './support';

/**
 * T193 — A09 (administradores) e A02 (definir a senha com o código de ativação) nos dois temas e
 * larguras; o código de ativação aparece uma vez, a ativação funciona só pelo teclado e o último
 * administrador ativo não pode ser inativado.
 */
test.beforeEach(async ({ page, context, baseURL }) => {
  await seed();
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: baseURL });
  await signInAsAdministrator(page);
});

/** Cria um administrador (A09) e devolve o código de ativação exibido uma única vez. */
async function createAdministrator(page: Page, name: string, email: string): Promise<string> {
  await page.goto('/admin/administrators');
  await page.getByRole('button', { name: 'Criar administrador' }).click();
  const form = page.getByRole('region', { name: 'Criar administrador' });
  await form.getByLabel('Nome').fill(name);
  await form.getByLabel('E-mail').fill(email);
  await form.getByRole('button', { name: 'Criar' }).click();
  const panel = page.getByRole('region', { name: `Código de ativação de ${name}` });
  await expect(panel).toBeVisible();
  const code = (await panel.locator('.secret-panel__code').textContent())?.trim() ?? '';
  expect(code).toMatch(/^[A-Z0-9]{6,}$/);
  return code;
}

test.describe('A09 — administradores', () => {
  test('axe, lista com situação em texto e ícone e filtro com aria-pressed', async ({ page }) => {
    await page.goto('/admin/administrators');
    await expect(page.getByRole('heading', { level: 1, name: 'Administradores' })).toBeVisible();
    const row = page.getByRole('listitem').filter({ hasText: ADMIN_EMAIL });
    await expect(row).toContainText('Ativo');
    await expect(row).toContainText(/Ativado em \d{2}\/\d{2}\/\d{4}/);
    await expect(page.getByRole('group', { name: 'Situação' }).getByRole('button', { name: 'Todos' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expectNoSeriousViolations(page, { name: 'A09 administradores' });
  });

  test('criar: formulário acessível, erros anunciados e foco na ordem', async ({ page }) => {
    await page.goto('/admin/administrators');
    await page.getByRole('button', { name: 'Criar administrador' }).click();
    const form = page.getByRole('region', { name: 'Criar administrador' });
    await expectNoSeriousViolations(page, { name: 'A09 criar administrador' });
    await page.setViewportSize({ width: 1440, height: 900 });
    await expectFocusOrder(page, [
      form.getByLabel('Nome'),
      form.getByLabel('E-mail'),
      form.getByRole('button', { name: 'Criar' }),
      form.getByRole('button', { name: 'Cancelar' }),
    ]);
    await form.getByRole('button', { name: 'Criar' }).click();
    await expect(form.getByRole('alert')).toBeFocused();
    await expect(form.getByRole('alert')).toContainText('Informe o nome.');

    await form.getByLabel('Nome').fill('Duplicada');
    await form.getByLabel('E-mail').fill(ADMIN_EMAIL);
    await form.getByRole('button', { name: 'Criar' }).click();
    await expect(form.getByRole('alert')).toContainText('Já existe um cadastro com este e-mail');
  });

  test('o código de ativação aparece uma vez, é acessível por teclado e não vai à URL nem ao armazenamento', async ({
    page,
  }) => {
    const code = await createAdministrator(page, 'Marina Duarte', 'marina@example.test');
    const panel = page.getByRole('region', { name: 'Código de ativação de Marina Duarte' });
    await expect(panel).toContainText('Este código aparece uma única vez');
    await expectNoSeriousViolations(page, { name: 'A09 código de ativação' });

    await page.setViewportSize({ width: 1440, height: 900 });
    await tabUntilFocused(page, panel.getByRole('button', { name: 'Copiar' }));
    await page.keyboard.press('Enter');
    await expect(panel.getByRole('status')).toHaveText('Código copiado.');
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(code);
    await expectValueAbsentFromUrlAndStorage(page, code);

    await tabUntilFocused(page, panel.getByRole('button', { name: 'Já anotei o código' }));
    await page.keyboard.press('Enter');
    await expect(panel).toHaveCount(0);
    const row = page.getByRole('listitem').filter({ hasText: 'marina@example.test' });
    await expect(row).toContainText('Ativação pendente');
    await expect(page.locator('body')).not.toContainText(code);
    await expectNoSeriousViolations(page, { name: 'A09 ativação pendente' });
  });

  test('inativar: diálogo com foco preso e Esc; o último administrador ativo não pode ser inativado', async ({
    page,
  }) => {
    await page.goto('/admin/administrators');
    // único administrador ativo: a recusa do servidor aparece dentro do diálogo, anunciada
    const lastTrigger = page.getByRole('button', { name: 'Inativar Administradora de Teste' });
    await lastTrigger.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Inativar administrador' });
    await expect(dialog).toHaveAccessibleDescription(/Inativar Administradora de Teste\? As sessões dele\(a\) terminam agora\./);
    await expectNoSeriousViolations(page, { name: 'A09 diálogo de inativação' });
    await dialog.getByRole('button', { name: 'Inativar' }).click();
    await expect(dialog.getByRole('alert')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('listitem').filter({ hasText: ADMIN_EMAIL })).toContainText('Ativo');

    // com outro administrador ativo no sistema a inativação é permitida
    const code = await createAdministrator(page, 'Nelson Faria', 'nelson@example.test');
    await page.getByRole('button', { name: 'Já anotei o código' }).click();
    expect(code).toBeTruthy();
    const trigger = page.getByRole('button', { name: 'Inativar Nelson Faria' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    await expectModalDialogBehavior(page, page.getByRole('dialog', { name: 'Inativar administrador' }), trigger);
  });
});

test.describe('A02 — definir a senha com o código de ativação', () => {
  test('axe, lang, ordem de foco e a regra de 12 a 128 caracteres visível antes do envio', async ({ browser, baseURL }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'pt-BR', baseURL });
    try {
      const page = await context.newPage();
      await page.goto('/admin/activate');
      await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
      await expect(page.getByRole('heading', { level: 1, name: 'Definir a senha' })).toBeVisible();
      await expect(page.getByLabel('Código de ativação')).toHaveAttribute('autocomplete', 'off');
      await expect(page.getByLabel('Nova senha', { exact: true })).toHaveAccessibleDescription(
        'A senha deve ter de 12 a 128 caracteres.',
      );
      await expectNoSeriousViolations(page, { name: 'A02 definir senha' });
      await page.setViewportSize({ width: 1440, height: 900 });
      await expectFocusOrder(page, [
        page.getByLabel('E-mail'),
        page.getByLabel('Código de ativação'),
        page.getByLabel('Nova senha', { exact: true }),
        page.getByLabel('Confirme a nova senha'),
        page.getByRole('button', { name: 'Definir senha' }),
        page.getByRole('link', { name: 'Já tenho senha' }),
      ]);

      // campos vazios e código inválido: erros anunciados, sem revelar se o e-mail existe
      await page.getByRole('button', { name: 'Definir senha' }).click();
      await expect(page.getByRole('alert')).toBeFocused();
      await expect(page.getByRole('alert')).toContainText('Informe o código de ativação.');
      await expectNoSeriousViolations(page, { name: 'A02 campos vazios' });
      const password = randomPassword();
      await page.getByLabel('E-mail').fill('ninguem@example.test');
      await page.getByLabel('Código de ativação').fill('ABCD2345');
      await page.getByLabel('Nova senha', { exact: true }).fill(password);
      await page.getByLabel('Confirme a nova senha').fill(password);
      await page.getByRole('button', { name: 'Definir senha' }).click();
      await expect(page.getByRole('alert')).toContainText('Código de ativação inválido ou vencido.');
      await expectNoSeriousViolations(page, { name: 'A02 código inválido' });
    } finally {
      await context.close();
    }
  });

  test('ativa só pelo teclado e o novo administrador entra com a senha definida', async ({
    page,
    browser,
    baseURL,
  }) => {
    const code = await createAdministrator(page, 'Olívia Matos', 'olivia@example.test');
    const password = randomPassword();
    const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'pt-BR', baseURL });
    try {
      const guest = await context.newPage();
      await guest.goto('/admin/activate');
      await tabUntilFocused(guest, guest.getByLabel('E-mail'));
      await guest.keyboard.type('olivia@example.test');
      await guest.keyboard.press('Tab');
      await guest.keyboard.type(code.toLowerCase());
      await guest.keyboard.press('Tab');
      await guest.keyboard.type(password);
      await guest.keyboard.press('Tab');
      await guest.keyboard.type(password);
      await guest.keyboard.press('Enter');
      const done = guest.getByRole('status').filter({ hasText: 'Senha definida. Agora você já pode entrar.' });
      await expect(done).toBeVisible();
      await expectNoSeriousViolations(guest, { name: 'A02 senha definida' });
      await expectValueAbsentFromUrlAndStorage(guest, code);
      await guest.getByRole('link', { name: 'Entrar' }).click();
      await signInAsAdministrator(guest, { email: 'olivia@example.test', password });
      await expect(guest.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible();
    } finally {
      await context.close();
    }
  });
});
