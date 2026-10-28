import { expect, test, type Page } from '@playwright/test';
import {
  expectFocusOrder,
  expectModalDialogBehavior,
  expectNoSeriousViolations,
  expectValueAbsentFromUrlAndStorage,
  seed,
  signInAsAdministrator,
  tabUntilFocused,
  type Seeding,
} from './support';

/**
 * T092 — A05 a A08 (professores): axe nos dois temas e nas duas larguras, foco preso nos diálogos e
 * `OneTimeSecretPanel` acessível por teclado. O código de acesso aparece uma vez e não vai à URL nem ao
 * armazenamento do navegador.
 */
let data: Seeding;

test.beforeEach(async ({ page, context, baseURL }) => {
  data = await seed();
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: baseURL });
  await signInAsAdministrator(page);
});

/** Cadastra um professor pelo teclado até o painel do código (A06 → A07) e devolve o código exibido. */
async function registerTeacher(page: Page, name: string, email: string): Promise<string> {
  await page.goto('/admin/teachers/new');
  await page.getByLabel('Nome').fill(name);
  await page.getByLabel('E-mail').fill(email);
  await page.getByRole('button', { name: 'Cadastrar' }).click();
  await expect(page.getByRole('heading', { level: 2, name: `Código de acesso de ${name}` })).toBeVisible();
  const code = (await page.locator('.secret-panel__code').textContent())?.trim() ?? '';
  expect(code).toMatch(/^[A-Z0-9]{6,}$/);
  return code;
}

test.describe('A05 — lista de professores', () => {
  test('axe, busca em role=search e filtro de situação com aria-pressed', async ({ page }) => {
    await page.goto('/admin/teachers');
    await expect(page.getByRole('heading', { level: 1, name: 'Professores' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Clara Souza' })).toBeVisible();
    await expect(page.getByRole('search')).toBeVisible();
    await expect(page.getByRole('group', { name: 'Situação' }).getByRole('button', { name: 'Todos' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expectNoSeriousViolations(page, { name: 'A05 professores' });
  });

  test('busca e filtro funcionam só pelo teclado; busca sem resultado tem estado vazio', async ({ page }) => {
    await page.goto('/admin/teachers');
    await tabUntilFocused(page, page.getByRole('searchbox'));
    await page.keyboard.type('Bruno');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('link', { name: 'Bruno Lima' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Clara Souza' })).toHaveCount(0);

    await page.getByRole('searchbox').fill('inexistente');
    await page.keyboard.press('Enter');
    await expect(page.getByText(/Nenhum professor encontrado/)).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A05 busca sem resultado' });

    await tabUntilFocused(page, page.getByRole('group', { name: 'Situação' }).getByRole('button', { name: 'Inativos' }));
    await page.keyboard.press('Space');
    await expect(page.getByRole('group', { name: 'Situação' }).getByRole('button', { name: 'Inativos' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  test('a linha mostra a situação com texto e ícone e nunca mostra o código', async ({ page }) => {
    await page.goto('/admin/teachers');
    const row = page.getByRole('listitem').filter({ hasText: 'Clara Souza' });
    await expect(row).toContainText('Ativo');
    await expect(row).toContainText('clara@example.test');
    await expect(page.locator('body')).not.toContainText('K7QM2XPA');
  });
});

test.describe('A06 e A07 — cadastrar professor e código exibido uma vez', () => {
  test('A06: axe, ordem de foco e erros anunciados', async ({ page }) => {
    await page.goto('/admin/teachers/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Cadastrar professor' })).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A06 cadastrar professor' });
    await page.setViewportSize({ width: 1440, height: 900 });
    await expectFocusOrder(page, [
      page.getByLabel('Nome'),
      page.getByLabel('E-mail'),
      page.getByRole('button', { name: 'Cadastrar' }),
      page.getByRole('link', { name: 'Cancelar' }),
    ]);

    await page.getByRole('button', { name: 'Cadastrar' }).click();
    const summary = page.getByRole('alert');
    await expect(summary).toBeFocused();
    await expect(summary).toContainText('Informe o nome.');
    await expect(page.getByLabel('Nome')).toHaveAccessibleDescription('Informe o nome.');
    await expectNoSeriousViolations(page, { name: 'A06 com erros' });

    // e-mail repetido: recusa do servidor com o nome de quem já o usa
    await page.getByLabel('Nome').fill('Outra Pessoa');
    await page.getByLabel('E-mail').fill('clara@example.test');
    await page.getByRole('button', { name: 'Cadastrar' }).click();
    await expect(page.getByRole('alert')).toContainText('Já existe um cadastro com este e-mail');
    await expect(page.getByLabel('E-mail')).toHaveAttribute('aria-invalid', 'true');
  });

  test('A07: o painel do código é acessível, copia por teclado e some ao confirmar', async ({ page }) => {
    const code = await registerTeacher(page, 'Elisa Prado', 'elisa@example.test');
    const panel = page.getByRole('region', { name: 'Código de acesso de Elisa Prado' });
    await expect(panel).toBeVisible();
    await expect(panel).toContainText('Este código aparece uma única vez');
    await expect(panel.getByText(/^Válido até \d{2}\/\d{2}\/\d{4} às \d{2}:\d{2}\.$/)).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A07 código exibido' });

    // só teclado: Copiar → Já anotei o código; a cópia é anunciada numa região de status
    await page.setViewportSize({ width: 1440, height: 900 });
    await tabUntilFocused(page, panel.getByRole('button', { name: 'Copiar' }));
    await page.keyboard.press('Enter');
    await expect(panel.getByRole('status')).toHaveText('Código copiado.');
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(code);
    await expectFocusOrder(page, [panel.getByRole('button', { name: 'Já anotei o código' })]);

    // o código não vai à URL nem ao armazenamento
    await expectValueAbsentFromUrlAndStorage(page, code);
    await expect(page).not.toHaveTitle(new RegExp(code));

    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/admin\/teachers\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Elisa Prado' })).toBeVisible();
    await expect(page.locator('body')).not.toContainText(code);
    await page.reload();
    await expect(page.locator('body')).not.toContainText(code);
  });

  test('A07: sair da página sem anotar o código abre um diálogo com foco preso', async ({ page }) => {
    await registerTeacher(page, 'Fabiana Reis', 'fabiana@example.test');
    const nav = page.getByRole('navigation', { name: 'Área administrativa' });
    await page.setViewportSize({ width: 1440, height: 900 });
    const leave = nav.getByRole('link', { name: 'Alunos' });
    await leave.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Sair sem anotar o código?' });
    await expectModalDialogBehavior(page, dialog, leave);
    // Esc cancelou a saída: o painel e a página continuam
    await expect(page).toHaveURL(/\/admin\/teachers\/new$/);
    await expect(page.getByRole('region', { name: 'Código de acesso de Fabiana Reis' })).toBeVisible();

    await leave.focus();
    await page.keyboard.press('Enter');
    await expectNoSeriousViolations(page, { name: 'A07 diálogo de saída' });
    await page.getByRole('button', { name: 'Sair mesmo assim' }).click();
    await expect(page).toHaveURL(/\/admin\/students$/);
  });
});

test.describe('A08 — professor', () => {
  test('axe da tela do professor ativo', async ({ page }) => {
    await page.goto(`/admin/teachers/${data.clara}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Clara Souza' })).toBeVisible();
    await expect(page.getByText('Telegram não vinculado')).toBeVisible();
    await expectNoSeriousViolations(page, { name: 'A08 professor' });
  });

  test('o diálogo de inativar prende o foco, Esc cancela sem gravar e o foco volta ao botão', async ({ page }) => {
    await page.goto(`/admin/teachers/${data.clara}`);
    const trigger = page.getByRole('button', { name: 'Inativar' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Inativar professor' });
    await expect(dialog).toContainText('Inativar Clara Souza?');
    await expectNoSeriousViolations(page, { name: 'A08 diálogo inativar' });
    await expectModalDialogBehavior(page, dialog, trigger);
    // cancelar não grava
    await expect(page.locator('.status-badge--active')).toBeVisible();
  });

  test('inativar e reativar: a reativação mostra o código novo uma única vez', async ({ page }) => {
    await page.goto(`/admin/teachers/${data.bruno}`);
    await page.getByRole('button', { name: 'Inativar' }).click();
    await page.getByRole('dialog', { name: 'Inativar professor' }).getByRole('button', { name: 'Inativar' }).click();
    await expect(page.getByText('Este professor está inativo.')).toBeVisible();
    // a ação de situação troca os botões: o foco volta ao título
    await expect(page.getByRole('heading', { level: 1, name: 'Bruno Lima' })).toBeFocused();
    await expectNoSeriousViolations(page, { name: 'A08 professor inativo' });

    await page.getByRole('button', { name: 'Reativar' }).click();
    const panel = page.getByRole('region', { name: 'Código de acesso de Bruno Lima' });
    await expect(panel).toBeVisible();
    const code = (await panel.locator('.secret-panel__code').textContent())?.trim() ?? '';
    await expectValueAbsentFromUrlAndStorage(page, code);
    await expectNoSeriousViolations(page, { name: 'A08 código na reativação' });
    await panel.getByRole('button', { name: 'Já anotei o código' }).click();
    await expect(panel).toHaveCount(0);
    await expect(page.locator('body')).not.toContainText(code);
  });
});
