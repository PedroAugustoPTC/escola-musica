import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import {
  CODE_CLARA,
  expectNoSeriousViolations,
  registerApprovedLesson,
  seed,
  signInAsAdministrator,
  signin,
  withDatabase,
  type Seeding,
} from './support';

/**
 * Feature 007 (US5) — A17 "Custos" e A18 "Alertas" e o indicador do cabeçalho: axe (sem violação séria ou
 * crítica) em claro e escuro, a 390 e 1440 px, com dados, vazios e com alerta ativo.
 */
const TEST_TIMEOUT_MS = 240_000;

let data: Seeding;

test.beforeEach(async () => {
  test.setTimeout(TEST_TIMEOUT_MS);
  data = await seed();
});

async function insertAlert(type: string, component: string, status: 'ACTIVE' | 'NORMALIZED') {
  await withDatabase((database) =>
    database.query(
      `insert into operational_alert (id, type, component, detail_code, status, started_at, last_seen_at,
         normalized_at, occurrences)
       values ($1, $2, $3, 'DETAIL', $4::varchar, now(), now(), case when $4::varchar = 'NORMALIZED' then now() else null end, 2)`,
      [randomUUID(), type, component, status],
    ),
  );
}

test('A17 custos com o mês processado', async ({ page }) => {
  await signin(page, CODE_CLARA);
  await registerApprovedLesson(page, data.students.matteo);
  await page.context().clearCookies();
  await signInAsAdministrator(page);

  await page.goto('/admin/operations/costs');
  await expect(page.getByRole('heading', { level: 1, name: 'Tempo e custo do mês' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Por canal' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A17 com dados' });
});

test('A17 custos de um mês sem chamadas', async ({ page }) => {
  await signInAsAdministrator(page);

  await page.goto('/admin/operations/costs?month=2020-01');
  await expect(page.getByText('Nenhuma chamada de IA neste mês.')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A17 vazia' });
});

test('A18 alertas ativos, histórico e indicador do cabeçalho', async ({ page }) => {
  await insertAlert('AUDIT_CHAIN_BROKEN', 'audit-trail', 'ACTIVE');
  await insertAlert('STORAGE_FAILED', 'pdf-storage', 'NORMALIZED');
  await signInAsAdministrator(page);

  await page.goto('/admin/operations/alerts');
  await expect(page.getByRole('heading', { level: 1, name: 'Alertas de operação' })).toBeVisible();
  await expect(page.getByRole('link', { name: '1 alerta ativo' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Histórico' })).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A18 com alertas' });
});

test('A18 sem alertas', async ({ page }) => {
  await signInAsAdministrator(page);

  await page.goto('/admin/operations/alerts');
  await expect(page.getByText('Nenhum alerta ativo.')).toBeVisible();
  await expect(page.getByText('Sem alertas ativos')).toBeVisible();
  await expectNoSeriousViolations(page, { name: 'A18 vazia' });
});
