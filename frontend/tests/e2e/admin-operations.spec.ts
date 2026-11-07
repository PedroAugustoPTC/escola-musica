import { randomUUID } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import {
  CODE_CLARA,
  registerApprovedLesson,
  seed,
  signInAsAdministrator,
  signin,
  withDatabase,
  type Seeding,
} from './support';

/**
 * Feature 007 (US5) — custo e alertas de operação na Web: depois de uma aula processada, o administrador vê
 * o custo do mês por canal e por aluno; um alerta aberto aparece no indicador do cabeçalho de qualquer tela e
 * na tela de alertas, e a quebra da trilha é marcada como apurada.
 */
const TEST_TIMEOUT_MS = 240_000;

let data: Seeding;

async function openAlert(type: string, component: string, detail: string): Promise<string> {
  const id = randomUUID();
  await withDatabase((database) =>
    database.query(
      `insert into operational_alert (id, type, component, detail_code, status, started_at, last_seen_at, occurrences)
       values ($1, $2, $3, $4, 'ACTIVE', now(), now(), 1)`,
      [id, type, component, detail],
    ),
  );
  return id;
}

async function openAsAdministrator(page: Page) {
  await page.context().clearCookies();
  await signInAsAdministrator(page);
}

test.beforeEach(async () => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
});

test('o custo do mês aparece por canal e por aluno depois de uma aula processada', async ({ page }) => {
  await signin(page, CODE_CLARA);
  await registerApprovedLesson(page, data.students.matteo);
  await openAsAdministrator(page);

  await page.goto('/admin');
  await page.getByRole('navigation', { name: 'Área administrativa' }).getByRole('link', { name: 'Custos' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Tempo e custo do mês' })).toBeVisible();
  await expect(page.getByText('Total do mês')).toBeVisible();
  await expect(page.getByText('Por relatório semestral')).toBeVisible();
  const channels = page.getByRole('table', { name: 'Por canal' });
  await expect(channels.getByRole('rowheader', { name: 'Web' })).toBeVisible();
  const students = page.getByRole('table', { name: 'Por aluno' });
  await expect(students.getByRole('rowheader').first()).toBeVisible();
  // a rota guarda só o mês, nunca o nome do aluno
  expect(page.url()).not.toContain('Matteo');
});

test('um alerta aberto aparece no indicador de qualquer tela e na tela de alertas', async ({ page }) => {
  await openAsAdministrator(page);
  const nav = page.getByRole('navigation', { name: 'Área administrativa' });
  await expect(page.getByText('Sem alertas ativos')).toBeVisible();

  await openAlert('STORAGE_FAILED', 'pdf-storage', 'WRITE_FAILED');
  await page.reload();
  await nav.getByRole('link', { name: 'Professores' }).click();
  await expect(page.getByRole('link', { name: '1 alerta ativo' })).toBeVisible();

  await page.getByRole('link', { name: '1 alerta ativo' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Alertas de operação' })).toBeVisible();
  const active = page.getByRole('table', { name: 'Alertas ativos' });
  await expect(active.getByRole('row').filter({ hasText: 'Falha no armazenamento de PDFs' })).toBeVisible();
  await expect(active.getByRole('row').filter({ hasText: 'pdf-storage' })).toBeVisible();
  // alerta que normaliza sozinho não tem ação manual
  await expect(page.getByRole('button', { name: 'Marcar como apurado' })).toHaveCount(0);
  await expect(page.getByText('Prazos e limites em vigor')).toBeVisible();
});

test('a quebra da trilha é marcada como apurada e o indicador volta a "sem alertas"', async ({ page }) => {
  await openAlert('AUDIT_CHAIN_BROKEN', 'audit-trail', 'CHAIN_BROKEN');
  await openAsAdministrator(page);

  await page.goto('/admin/operations/alerts');
  await expect(page.getByRole('link', { name: '1 alerta ativo' })).toBeVisible();
  await page.getByRole('button', { name: 'Marcar como apurado' }).click();

  await expect(page.getByText('Alerta marcado como apurado.')).toBeVisible();
  const history = page.getByRole('table', { name: 'Histórico' });
  await expect(history.getByRole('row').filter({ hasText: 'Cadeia da trilha de auditoria quebrada' })).toBeVisible();
  await expect(page.getByText('Sem alertas ativos')).toBeVisible();
});
