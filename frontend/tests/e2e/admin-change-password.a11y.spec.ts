import { expect, test } from '@playwright/test';
import {
  administratorCredentialsFromEnvironment,
  expectFocusOrder,
  expectNoSeriousViolations,
  randomPassword,
  seed,
  signInAsAdministrator,
  tabUntilFocused,
} from './support';

/**
 * T205 — A03 (trocar a senha provisória): axe nos dois temas e larguras, uso só por teclado e erro de
 * senha anunciado. O administrador é semeado com senha provisória (o primeiro acesso do bootstrap).
 */
test.beforeEach(async ({ page }) => {
  await seed({ provisionalAdministrator: true });
  await signInAsAdministrator(page);
});

test('a entrada com a senha provisória leva só à troca de senha, sem navegação', async ({ page }) => {
  await expect(page).toHaveURL(/\/admin\/change-password$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
  await expect(page.getByRole('heading', { level: 1, name: 'Defina uma nova senha' })).toBeVisible();
  await expect(page.getByText('Defina uma nova senha para continuar.')).toBeVisible();
  // enquanto a senha for provisória a navegação administrativa não é oferecida
  await expect(page.getByRole('navigation', { name: 'Área administrativa' })).toHaveCount(0);
  await page.goto('/admin/teachers');
  await expect(page).toHaveURL(/\/admin\/change-password$/);
});

test('axe sem violação séria nos dois temas e larguras', async ({ page }) => {
  await expectNoSeriousViolations(page, { name: 'A03 trocar senha' });
});

test('os campos têm rótulo, autocompletar de senha nova e a regra visível antes do envio', async ({
  page,
}) => {
  const newPassword = page.getByLabel('Nova senha', { exact: true });
  const confirmation = page.getByLabel('Confirme a nova senha');
  await expect(newPassword).toHaveAttribute('type', 'password');
  await expect(newPassword).toHaveAttribute('autocomplete', 'new-password');
  await expect(confirmation).toHaveAttribute('autocomplete', 'new-password');
  await expect(newPassword).toHaveAccessibleDescription('A senha deve ter de 12 a 128 caracteres.');
});

test('a ordem de foco é nova senha, confirmação e trocar senha', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await expectFocusOrder(page, [
    page.getByLabel('Nova senha', { exact: true }),
    page.getByLabel('Confirme a nova senha'),
    page.getByRole('button', { name: 'Trocar senha' }),
  ]);
});

test('erros de forma são anunciados, ligados aos campos e levam o foco ao resumo', async ({ page }) => {
  await page.getByRole('button', { name: 'Trocar senha' }).click();
  const summary = page.getByRole('alert');
  await expect(summary).toBeFocused();
  await expect(summary).toContainText('Informe a nova senha.');
  await expect(summary).toContainText('Confirme a nova senha.');
  const newPassword = page.getByLabel('Nova senha', { exact: true });
  await expect(newPassword).toHaveAttribute('aria-invalid', 'true');
  await expect(newPassword).toHaveAccessibleDescription(/Informe a nova senha\./);
  await expectNoSeriousViolations(page, { name: 'A03 campos vazios' });

  await newPassword.fill(randomPassword());
  await page.getByLabel('Confirme a nova senha').fill(randomPassword());
  await page.getByRole('button', { name: 'Trocar senha' }).click();
  await expect(page.getByRole('alert')).toContainText('As senhas não são iguais.');
  await expect(page.getByLabel('Confirme a nova senha')).toHaveAccessibleDescription(
    'As senhas não são iguais.',
  );
});

test('senha curta é recusada pelo servidor e o erro é anunciado', async ({ page }) => {
  await page.getByLabel('Nova senha', { exact: true }).fill('curta123');
  await page.getByLabel('Confirme a nova senha').fill('curta123');
  await page.getByRole('button', { name: 'Trocar senha' }).click();
  const summary = page.getByRole('alert');
  await expect(summary).toBeVisible();
  await expect(summary).toContainText('12 a 128');
  await expect(page).toHaveURL(/\/admin\/change-password$/);
  // a senha digitada não fica nos campos depois da falha
  await expect(page.getByLabel('Nova senha', { exact: true })).toHaveValue('');
  await expectNoSeriousViolations(page, { name: 'A03 senha recusada' });
});

test('troca a senha só pelo teclado; a provisória deixa de valer e a nova entra', async ({
  page,
  browser,
}) => {
  const credentials = administratorCredentialsFromEnvironment();
  const fresh = randomPassword();
  await tabUntilFocused(page, page.getByLabel('Nova senha', { exact: true }));
  await page.keyboard.type(fresh);
  await page.keyboard.press('Tab');
  await page.keyboard.type(fresh);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Área administrativa' })).toBeVisible();

  const other = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'pt-BR' });
  try {
    const anonymous = await other.newPage();
    await signInAsAdministrator(anonymous, { email: credentials.email, password: fresh });
    await expect(anonymous).toHaveURL(/\/admin$/);
  } finally {
    await other.close();
  }
});
