import { expect, test } from '@playwright/test';
import {
  administratorCredentialsFromEnvironment,
  expectValueAbsentFromUrlAndStorage,
  maskedCpf,
  seed,
  signInAsTeacher,
  syntheticCpf,
} from './support';

/**
 * SC-001: o administrador cadastra um professor, copia o código e cadastra um aluno associado a ele em
 * até 180 s e 25 interações. O roteiro digita a 40 ms por tecla; cada preenchimento de campo, clique ou
 * tecla de comando conta como uma interação.
 */
const BUDGET_MS = 180_000;
const MAX_INTERACTIONS = 25;
const KEY_DELAY_MS = 40;
/** Folga sobre o orçamento: o teste só pode morrer por timeout depois de a asserção de 180 s ter a chance de falar. */
const TEST_TIMEOUT_MS = BUDGET_MS + 60_000;

test.beforeEach(async () => {
  await seed();
});

test('o administrador cadastra um professor, copia o código e cadastra um aluno associado a ele (SC-001)', async ({
  page,
  browser,
  context,
  baseURL,
}) => {
  // o timeout padrão do Playwright (90 s) mataria o roteiro antes do orçamento de 180 s
  test.setTimeout(TEST_TIMEOUT_MS);
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: baseURL });
  const credentials = administratorCredentialsFromEnvironment();
  const studentCpf = syntheticCpf('357951246');
  let interactions = 0;
  const step = <T>(action: Promise<T>): Promise<T> => {
    interactions += 1;
    return action;
  };
  const type = (label: string, text: string) =>
    step(page.getByLabel(label, { exact: true }).pressSequentially(text, { delay: KEY_DELAY_MS }));
  const nav = page.getByRole('navigation', { name: 'Área administrativa' });
  const started = Date.now();

  // entrar (A01)
  await page.goto('/admin/sign-in');
  await type('E-mail', credentials.email);
  await type('Senha', credentials.password);
  await step(page.getByRole('button', { name: 'Entrar' }).click());
  await expect(page.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible();

  // cadastrar o professor (A05 → A06 → A07)
  await step(nav.getByRole('link', { name: 'Professores' }).click());
  await step(page.getByRole('link', { name: 'Cadastrar professor' }).click());
  await type('Nome', 'Rafael Monteiro');
  await type('E-mail', 'rafael@example.test');
  await step(page.getByRole('button', { name: 'Cadastrar' }).click());
  const panel = page.getByRole('region', { name: 'Código de acesso de Rafael Monteiro' });
  await expect(panel).toBeVisible();
  const code = (await panel.locator('.secret-panel__code').textContent())?.trim() ?? '';
  await step(panel.getByRole('button', { name: 'Copiar' }).click());
  await expect(panel.getByRole('status')).toHaveText('Código copiado.');
  await step(panel.getByRole('button', { name: 'Já anotei o código' }).click());
  await expect(page.getByRole('heading', { level: 1, name: 'Rafael Monteiro' })).toBeVisible();

  // cadastrar o aluno associado a ele (A10 → A11 → A12)
  await step(nav.getByRole('link', { name: 'Alunos' }).click());
  await step(page.getByRole('link', { name: 'Cadastrar aluno' }).first().click());
  await type('Nome do aluno', 'Beatriz Nogueira');
  await step(page.getByLabel('Data de nascimento').fill('1996-09-14'));
  await type('CPF', maskedCpf(studentCpf));
  await type('Instrumento', 'Violoncelo');
  await step(page.getByRole('checkbox', { name: 'Rafael Monteiro' }).check());
  await step(page.getByRole('button', { name: 'Cadastrar aluno' }).click());
  await expect(page.getByRole('heading', { level: 1, name: 'Beatriz Nogueira' })).toBeVisible();
  await expect(
    page
      .getByRole('region', { name: 'Professores do aluno' })
      .getByRole('listitem')
      .filter({ hasText: 'Rafael Monteiro' }),
  ).toBeVisible();

  const elapsed = Date.now() - started;
  console.log(`SC-001: ${(elapsed / 1000).toFixed(1)} s e ${interactions} interações`);
  expect(interactions, 'SC-001: interações').toBeLessThanOrEqual(MAX_INTERACTIONS);
  expect(elapsed, 'SC-001: o roteiro passou de 180 s').toBeLessThanOrEqual(BUDGET_MS);

  // o código copiado é o código exibido e o CPF digitado não ficou no navegador
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(code);
  await expectValueAbsentFromUrlAndStorage(page, studentCpf);
  await expectValueAbsentFromUrlAndStorage(page, code);

  // fora do orçamento: o código entregue funciona e o professor vê o aluno que acabou de ser associado
  const teacherContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'pt-BR', baseURL });
  try {
    const teacherPage = await teacherContext.newPage();
    await signInAsTeacher(teacherPage, code);
    await teacherPage.goto('/my-students');
    await expect(teacherPage.getByText('Beatriz Nogueira')).toBeVisible();
  } finally {
    await teacherContext.close();
  }
});
