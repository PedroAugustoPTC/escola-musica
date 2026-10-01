import { defineConfig, devices } from '@playwright/test';

/**
 * Testes ponta a ponta contra a pilha de `infra/compose.test.yaml` (serviço `testes-e2e`), com IA
 * simulada. O banco é compartilhado: os testes rodam em série e cada um semeia os dados de que precisa.
 */
export default defineConfig({
  testDir: 'tests/e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 20_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_URL ?? 'https://localhost',
    ignoreHTTPSErrors: true,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
