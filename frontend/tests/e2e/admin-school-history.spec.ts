import { randomUUID } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import {
  CODE_BRUNO,
  CODE_CLARA,
  registerApprovedLesson,
  seed,
  signInAsAdministrator,
  signin,
  withDatabase,
  type Seeding,
} from './support';

/**
 * Feature 006 — histórico da escola na Web (US1 e US2): o administrador chega ao histórico de um aluno em até
 * 3 interações a partir de `/admin` (SC-001), vê os relatórios aprovados de todos os professores e abre ou
 * baixa o PDF oficial. Sem credencial de administrador o PDF não é entregue.
 */
const INTERACTION_BUDGET = 3;
/** Duas aulas registradas e aprovadas pela interface levam mais que o limite padrão de 90 s. */
const TEST_TIMEOUT_MS = 240_000;

let data: Seeding;

/** Bruno passa a ensinar Matteo, que já é aluno da Clara; cada um registra e aprova uma aula. */
async function approveLessonsFromTwoTeachers(page: Page): Promise<void> {
  await withDatabase((database) =>
    database.query(
      'insert into teacher_student_assignment (id, teacher_id, student_id, created_at) values ($1, $2, $3, now())',
      [randomUUID(), data.bruno, data.students.matteo],
    ),
  );
  await signin(page, CODE_CLARA);
  await registerApprovedLesson(page, data.students.matteo);
  await page.context().clearCookies();
  await signin(page, CODE_BRUNO);
  await registerApprovedLesson(page, data.students.matteo);
  await page.context().clearCookies();
}

/** De `/admin` ao histórico de Matteo em 3 interações: abrir "Histórico", digitar e escolher o aluno. */
async function openStudentHistory(page: Page): Promise<number> {
  let interactions = 0;
  await signInAsAdministrator(page);
  await page.goto('/admin');
  await page.getByRole('navigation', { name: 'Área administrativa' }).getByRole('link', { name: 'Histórico' }).click();
  interactions += 1;
  await page.getByRole('combobox', { name: 'Procurar aluno' }).fill('Matteo');
  interactions += 1;
  await page.getByRole('option', { name: /Matteo Mafra/ }).click();
  interactions += 1;
  await expect(page.getByRole('button', { name: 'Trocar aluno' })).toBeVisible();
  return interactions;
}

test.beforeEach(async ({ page }) => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
  await approveLessonsFromTwoTeachers(page);
});

test('o administrador chega ao histórico do aluno em 3 interações e vê os dois professores', async ({
  page,
}) => {
  const interactions = await openStudentHistory(page);

  expect(interactions).toBeLessThanOrEqual(INTERACTION_BUDGET);
  const rows = page.getByRole('row').filter({ hasText: /Aula de \d{2}\/\d{2}\/\d{4}/ });
  await expect(rows).toHaveCount(2);
  await expect(page.getByRole('table')).toContainText('Clara Souza');
  await expect(page.getByRole('table')).toContainText('Bruno Lima');
  await expect(page.getByRole('table')).toContainText(/\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}/);
  // a rota guarda só o identificador do aluno, nunca o nome
  expect(page.url()).toContain(`studentId=${data.students.matteo}`);
  expect(page.url()).not.toContain('Matteo');
  await expect(page.getByRole('columnheader', { name: 'Aluno' })).toHaveCount(0);
});

test('abre o PDF numa nova aba, baixa com o nome sugerido e nega o PDF sem sessão de administrador', async ({
  page,
  request,
}) => {
  await openStudentHistory(page);
  const first = page.getByRole('row').filter({ hasText: /Aula de \d{2}\/\d{2}\/\d{4}/ }).first();

  // o Chromium sem interface baixa o PDF em vez de exibi-lo; por isso os destinos são observados desde a
  // criação da aba (sem corrida), como em history.spec.ts
  const destinations: string[] = [];
  const observe = (target: Page) => {
    target.on('download', (download) => destinations.push(download.url()));
    target.on('framenavigated', (frame) => destinations.push(frame.url()));
  };
  observe(page);
  page.context().on('page', observe);
  const [tab] = await Promise.all([
    page.waitForEvent('popup'),
    first.getByRole('button', { name: /Abrir PDF/ }).click(),
  ]);
  expect(tab).toBeTruthy();
  await expect.poll(() => destinations.some((destination) => destination.startsWith('blob:'))).toBe(true);

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    first.getByRole('button', { name: /Baixar PDF/ }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^relatorio-aula-\d{4}-\d{2}-\d{2}\.pdf$/);

  // contexto sem cookie: o PDF não é entregue
  const reportId = await withDatabase(async (database) => {
    const result = await database.query<{ id: string }>(
      'select id from lesson_report where student_id = $1 limit 1',
      [data.students.matteo],
    );
    return result.rows[0]!.id;
  });
  for (const suffix of ['pdf', 'pdf/download']) {
    const denied = await request.get(`/api/v1/admin/school-history/lesson-reports/${reportId}/${suffix}`);
    expect(denied.status()).toBe(401);
  }
});

/**
 * US3 — filtros da escola inteira: sem aluno escolhido, por professor, por tipo e por período; a lista e o
 * total conferem e a rota guarda só identificadores, tipo e datas.
 */
const LESSON_ROW = /Aula de \d{2}\/\d{2}\/\d{4}/;

function todayIso(offsetDays = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

async function applyFilters(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Aplicar' }).click();
}

test('sem aluno escolhido, filtra por professor, por tipo e por período e confere itens e total', async ({
  page,
}) => {
  await signInAsAdministrator(page);
  await page.goto('/admin/history');
  const rows = page.getByRole('row').filter({ hasText: LESSON_ROW });
  await expect(rows).toHaveCount(2);
  await expect(page.getByText('2 relatórios aprovados')).toBeVisible();

  // por professor
  await page.getByRole('combobox', { name: 'Professor' }).selectOption(data.bruno);
  await applyFilters(page);
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('Bruno Lima');
  await expect(page.getByText('1 relatórios aprovados')).toBeVisible();
  expect(page.url()).toContain(`teacherId=${data.bruno}`);
  expect(page.url()).not.toContain('Bruno');

  // por tipo: "Semestral" não tem aprovado nesta massa; "Aula" volta a trazer o do professor
  await page.getByRole('combobox', { name: 'Tipo' }).selectOption('SEMESTER');
  await applyFilters(page);
  await expect(page.getByText('Nenhum relatório aprovado encontrado para estes filtros.')).toBeVisible();
  await page.getByRole('combobox', { name: 'Tipo' }).selectOption('LESSON');
  await applyFilters(page);
  await expect(rows).toHaveCount(1);

  // por período: um intervalo futuro esvazia; um que cobre hoje devolve o item
  await page.getByLabel('De').fill(todayIso(30));
  await page.getByLabel('Até').fill(todayIso(60));
  await applyFilters(page);
  await expect(page.getByText('Nenhum relatório aprovado encontrado para estes filtros.')).toBeVisible();
  await page.getByLabel('De').fill(todayIso(-1));
  await page.getByLabel('Até').fill(todayIso(1));
  await applyFilters(page);
  await expect(rows).toHaveCount(1);
  expect(page.url()).toContain(`from=${todayIso(-1)}`);

  // limpar volta aos dois professores
  await page.getByRole('button', { name: 'Limpar filtros' }).first().click();
  await expect(rows).toHaveCount(2);
  await expect(page.getByText('2 relatórios aprovados')).toBeVisible();
});

test('período invertido mostra o erro do servidor junto do campo', async ({ page }) => {
  await signInAsAdministrator(page);
  await page.goto('/admin/history');
  await page.getByLabel('De').fill(todayIso(5));
  await page.getByLabel('Até').fill(todayIso(-5));
  await applyFilters(page);
  await expect(page.getByText('A data inicial deve ser igual ou anterior à data final.')).toBeVisible();
});
