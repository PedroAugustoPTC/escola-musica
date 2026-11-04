import { expect, test } from '@playwright/test';
import {
  administratorCredentialsFromEnvironment,
  expectFocusOrder,
  expectNoSeriousViolations,
  seed,
  signInAsAdministrator,
  tabUntilFocused,
} from './support';

/**
 * T057 — A01 (entrada do administrador), A04 (início) e a moldura da área administrativa:
 * axe sem violação séria/crítica nos temas claro e escuro, em 390 e 1440 px; uso só por teclado; ordem
 * de foco; `lang="pt-BR"`; contagem regressiva do bloqueio anunciada (contracts/portal-screens.md §3, §5).
 */
test.beforeEach(async () => {
  await seed();
});

test.describe('A01 — entrar na área administrativa', () => {
  test('tem lang pt-BR, título único e axe limpo nos dois temas e larguras', async ({ page }) => {
    await page.goto('/admin/sign-in');
    await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
    await expect(page.getByRole('heading', { level: 1, name: 'Entrar na área administrativa' })).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A01 entrar' });
  });

  test('a ordem de foco é e-mail, senha, entrar e os dois links', async ({ page }) => {
    await page.goto('/admin/sign-in');
    await expectFocusOrder(page, [
      page.getByLabel('E-mail'),
      page.getByLabel('Senha', { exact: true }),
      page.getByRole('button', { name: 'Entrar' }),
      page.getByRole('link', { name: 'Tenho um código de ativação' }),
      page.getByRole('link', { name: 'Sou professor' }),
    ]);
  });

  test('os campos têm rótulo e autocompletar corretos', async ({ page }) => {
    await page.goto('/admin/sign-in');
    await expect(page.getByLabel('E-mail')).toHaveAttribute('autocomplete', 'username');
    await expect(page.getByLabel('Senha', { exact: true })).toHaveAttribute('autocomplete', 'current-password');
    await expect(page.getByLabel('Senha', { exact: true })).toHaveAttribute('type', 'password');
  });

  test('entra só pelo teclado e o foco vai ao título da área administrativa', async ({ page }) => {
    const { email, password } = administratorCredentialsFromEnvironment();
    await page.goto('/admin/sign-in');
    await tabUntilFocused(page, page.getByLabel('E-mail'));
    await page.keyboard.type(email);
    await page.keyboard.press('Tab');
    await page.keyboard.type(password);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible();
  });

  test('campos vazios e senha errada são anunciados e ligados ao campo', async ({ page }) => {
    await page.goto('/admin/sign-in');
    await page.getByRole('button', { name: 'Entrar' }).click();
    const email = page.getByLabel('E-mail');
    await expect(email).toHaveAttribute('aria-invalid', 'true');
    await expect(email).toHaveAccessibleDescription('Informe o e-mail.');
    await expect(page.getByLabel('Senha', { exact: true })).toHaveAccessibleDescription('Informe a senha.');
    await expectNoSeriousViolations(page, { name: 'A01 campos vazios', viewports: [390] });

    await email.fill(administratorCredentialsFromEnvironment().email);
    await page.getByLabel('Senha', { exact: true }).fill('wrong-password-123456');
    await page.getByRole('button', { name: 'Entrar' }).click();
    // mensagem única para qualquer falha, num alerta que o leitor de tela anuncia
    await expect(page.getByRole('alert')).toContainText('E-mail ou senha incorretos.');
    await expect(page.getByLabel('Senha', { exact: true })).toHaveValue('');
    await expectNoSeriousViolations(page, { name: 'A01 credenciais incorretas' });
  });

  test('o bloqueio mostra a contagem regressiva, anunciada sem repetir a cada segundo', async ({ page }) => {
    const { email } = administratorCredentialsFromEnvironment();
    await page.goto('/admin/sign-in');
    const submit = page.getByRole('button', { name: 'Entrar' });
    let blockedAt = 0;
    const password = page.getByLabel('Senha', { exact: true });
    for (let attempt = 1; attempt <= 8 && blockedAt === 0; attempt++) {
      await page.getByLabel('E-mail').fill(email);
      await password.fill(`errada-${attempt}-0123456789`);
      const [response] = await Promise.all([
        page.waitForResponse((r) => r.url().includes('/admin/session') && r.request().method() === 'POST'),
        submit.click(),
      ]);
      if (response.status() !== 401) blockedAt = attempt;
      // a tela limpa a senha ao tratar a resposta; sem esperar por isso, a próxima senha preenchida
      // seria apagada e o envio seguinte sairia vazio, sem requisição
      await expect(password).toHaveValue('');
    }
    expect(blockedAt, 'o bloqueio por tentativas incorretas ocorre em até 8 tentativas').toBeGreaterThan(0);

    const notice = page.getByText(/Acesso bloqueado temporariamente/);
    await expect(notice).toBeVisible();
    // a mensagem fica numa região de status educada; a contagem "mm:ss" fica fora dela
    const live = page.locator('[role="status"][aria-live="polite"]').filter({ has: notice });
    await expect(live).toHaveCount(1);
    await expect(submit).toBeDisabled();

    const countdown = page.getByText(/^Tempo restante: \d{2}:\d{2}$/);
    await expect(countdown).toBeVisible();
    const first = await countdown.textContent();
    const announced = await live.textContent();
    await expect.poll(async () => countdown.textContent(), { timeout: 5_000 }).not.toBe(first);
    expect(await live.textContent(), 'o texto anunciado não muda a cada segundo').toBe(announced);

    await expectNoSeriousViolations(page, { name: 'A01 bloqueado' });
  });
});

test.describe('A04 — início e moldura da área administrativa', () => {
  test.beforeEach(async ({ page }) => {
    await signInAsAdministrator(page);
  });

  test('tem lang pt-BR, título, relógio da sessão e axe limpo nos dois temas e larguras', async ({ page }) => {
    await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
    await expect(page.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible();
    await expect(page.getByText(/^Sua sessão expira às \d{2}:\d{2}\.$/)).toBeVisible();
    await expect(page.locator('.admin-shortcuts__count').first()).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A04 início' });
  });

  test('a ordem de foco no layout largo: pular, marca, navegação, tema, sair e conteúdo', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    const nav = page.getByRole('navigation', { name: 'Área administrativa' });
    await expectFocusOrder(page, [
      page.getByRole('link', { name: 'Ir para o conteúdo' }),
      page.getByRole('link', { name: 'Início da área administrativa' }),
      nav.getByRole('link', { name: 'Professores' }),
      nav.getByRole('link', { name: 'Alunos' }),
      nav.getByRole('link', { name: 'Administradores' }),
      page.getByRole('button', { name: 'Modo escuro' }),
      page.getByRole('button', { name: 'Sair' }),
      page.getByRole('main').getByRole('link').first(),
    ]);
  });

  test('o link de pular leva o foco ao conteúdo principal', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await tabUntilFocused(page, page.getByRole('link', { name: 'Ir para o conteúdo' }));
    await page.keyboard.press('Enter');
    await expect(page.getByRole('main')).toBeFocused();
  });

  test('a página atual leva aria-current na navegação, nas duas larguras', async ({ page }) => {
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/admin/teachers');
      const nav = page.getByRole('navigation', { name: 'Área administrativa' });
      await expect(nav).toBeVisible();
      await expect(nav.getByRole('link', { name: 'Professores' })).toHaveAttribute('aria-current', 'page');
      await expect(nav.getByRole('link', { name: 'Alunos' })).not.toHaveAttribute('aria-current', 'page');
    }
  });

  test('o foco vai ao título da página ao navegar pelo teclado', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    const nav = page.getByRole('navigation', { name: 'Área administrativa' });
    await tabUntilFocused(page, nav.getByRole('link', { name: 'Alunos' }));
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/admin\/students$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Alunos' })).toBeFocused();
  });

  test('o botão Modo escuro alterna o tema e informa o estado com aria-pressed', async ({ page }) => {
    const toggle = page.getByRole('button', { name: 'Modo escuro' });
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await toggle.focus();
    await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });

  test('Sair encerra a sessão e leva à entrada', async ({ page }) => {
    await page.getByRole('button', { name: 'Sair' }).click();
    await expect(page).toHaveURL(/\/admin\/sign-in$/);
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/admin\/sign-in/);
  });
});
