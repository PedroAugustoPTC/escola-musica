import { createCipheriv, createHmac, randomBytes, randomUUID } from 'node:crypto';
import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';
import pg from 'pg';

/*
 * Apoio aos testes ponta a ponta: semeia professores e alunos fictícios direto no banco da pilha de
 * testes (mesmo HMAC e mesma cifra do backend, com os segredos de testes de compose.test.yaml) e
 * oferece os passos comuns da interface.
 */

const DATABASE_URL = process.env.E2E_DATABASE_URL ?? 'postgres://reports:e2e@localhost:5432/reports';
const CPF_KEY = process.env.E2E_CPF_KEY ?? 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=';
const HMAC_SECRET = process.env.E2E_HMAC_SECRET ?? 'hmac-secret-for-e2e-tests-with-more-than-32-bytes';

export const CODE_CLARA = 'K7QM2XPA';
export const CODE_BRUNO = 'BRUN2XPA';

export interface Seeding {
  clara: string;
  bruno: string;
  students: Record<'matteo' | 'olavo' | 'miguel' | 'helena', string>;
}

function hmac(value: string): string {
  return createHmac('sha256', Buffer.from(HMAC_SECRET, 'utf8')).update(value, 'utf8').digest('hex');
}

function encryptCpf(digits: string): Buffer {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', Buffer.from(CPF_KEY, 'base64'), iv);
  const body = Buffer.concat([cipher.update(digits, 'utf8'), cipher.final()]);
  return Buffer.concat([iv, body, cipher.getAuthTag()]);
}

export async function withDatabase<T>(action: (client: pg.Client) => Promise<T>): Promise<T> {
  const client = new pg.Client({ connectionString: DATABASE_URL });
  await client.connect();
  try {
    return await action(client);
  } finally {
    await client.end();
  }
}

/** Apaga tudo (menos o histórico do Flyway) e cria Clara (3 alunos) e Bruno (1 aluno). */
export async function seed(): Promise<Seeding> {
  return withDatabase(async (database) => {
    const tables = await database.query<{ tablename: string }>(
      "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
    );
    await database.query(`truncate ${tables.rows.map((t) => `"${t.tablename}"`).join(', ')} cascade`);

    const now = new Date();
    const validity = new Date(now.getTime() + 7 * 24 * 3600 * 1000);
    const teacher = async (name: string, email: string, code: string) => {
      const id = randomUUID();
      await database.query(
        `insert into teacher (id, name, email, active, access_code_hash, access_code_expires_at,
           access_code_renewed_at, created_at, updated_at) values ($1, $2, $3, true, $4, $5, $6, $6, $6)`,
        [id, name, email, hmac(`codigo:${code}`), validity, now],
      );
      return id;
    };
    const student = async (teacherId: string, name: string, cpf: string, instrument: string) => {
      const id = randomUUID();
      await database.query(
        `insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, guardian_name, instrument,
           active, created_at, updated_at) values ($1, $2, '2012-05-10', $3, $4, null, $5, true, $6, $6)`,
        [id, name, encryptCpf(cpf), hmac(`cpf:${cpf}`), instrument, now],
      );
      await database.query(
        'insert into teacher_student_assignment (id, teacher_id, student_id, created_at) values ($1, $2, $3, $4)',
        [randomUUID(), teacherId, id, now],
      );
      return id;
    };

    // CPFs sintéticos: dígitos verificadores válidos, mas de exemplo público de geradores de teste
    // (e o CPF canário da feature 005, 11144477735); nenhum pertence a uma pessoa real.
    const clara = await teacher('Clara Souza', 'clara@example.test', CODE_CLARA);
    const bruno = await teacher('Bruno Lima', 'bruno@example.test', CODE_BRUNO);
    return {
      clara,
      bruno,
      students: {
        matteo: await student(clara, 'Matteo Mafra', '52998224725', 'Piano'),
        olavo: await student(clara, 'Olavo Torrente', '12345678909', 'Violão'),
        miguel: await student(clara, 'Miguel de Castro Torrente', '11144477735', 'Violino'),
        helena: await student(bruno, 'Helena Prado', '39053344705', 'Flauta'),
      },
    };
  });
}

/** WAV PCM mono de 3 s com um tom de 440 Hz (o backend normaliza com FFmpeg; a IA é simulada). */
export function testAudio(seconds = 3): Buffer {
  const rate = 16_000;
  const samples = rate * seconds;
  const wav = Buffer.alloc(44 + samples * 2);
  wav.write('RIFF', 0);
  wav.writeUInt32LE(36 + samples * 2, 4);
  wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(rate, 24);
  wav.writeUInt32LE(rate * 2, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write('data', 36);
  wav.writeUInt32LE(samples * 2, 40);
  for (let i = 0; i < samples; i++) {
    wav.writeInt16LE(Math.round(Math.sin((2 * Math.PI * 440 * i) / rate) * 0.3 * 32767), 44 + i * 2);
  }
  return wav;
}

export async function signin(page: Page, code = CODE_CLARA): Promise<void> {
  await page.goto('/sign-in');
  await page.getByLabel('Código de acesso').fill(code);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Olá');
}

/** Entra como professor pela tela 01 (código de acesso); o padrão é a Clara semeada. */
export async function signInAsTeacher(page: Page, code = CODE_CLARA): Promise<void> {
  await signin(page, code);
}

export interface AdministratorCredentials {
  email: string;
  password: string;
}

/**
 * Credenciais do administrador da pilha de testes, lidas do ambiente (`E2E_ADMIN_EMAIL` e
 * `E2E_ADMIN_PASSWORD`): a senha nunca fica no código nem em log.
 */
export function administratorCredentialsFromEnvironment(): AdministratorCredentials {
  const password = process.env.E2E_ADMIN_PASSWORD;
  if (!password) throw new Error('Defina E2E_ADMIN_PASSWORD para entrar como administrador nos testes e2e.');
  return { email: process.env.E2E_ADMIN_EMAIL ?? 'admin@example.test', password };
}

/** Tela A01: entra como administrador (e-mail e senha) e espera a área administrativa abrir. */
export async function signInAsAdministrator(
  page: Page,
  credentials: AdministratorCredentials = administratorCredentialsFromEnvironment(),
): Promise<void> {
  await page.goto('/admin/sign-in');
  await page.getByLabel('E-mail').fill(credentials.email);
  await page.getByLabel('Senha', { exact: true }).fill(credentials.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).toHaveURL(/\/admin(\/change-password)?$/);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
}

export type ColorTheme = 'light' | 'dark';

export interface AccessibilityCheckOptions {
  themes?: readonly ColorTheme[];
  /** Larguras da janela, em pixels. */
  viewports?: readonly number[];
}

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
const SERIOUS_IMPACTS = new Set(['serious', 'critical']);

/**
 * SC-012: axe sem violação séria ou crítica na página atual, em cada tema e largura dados
 * (padrão: claro e escuro, 390 px e 1440 px), e sem rolagem horizontal. Só informa a regra e o
 * seletor das violações (nunca o HTML nem o texto da página), para não vazar dado nos relatórios.
 * Devolve a janela e o tema ao que eram.
 */
export async function expectNoSeriousViolations(
  page: Page,
  options: AccessibilityCheckOptions = {},
): Promise<void> {
  const themes = options.themes ?? (['light', 'dark'] as const);
  const viewports = options.viewports ?? ([390, 1440] as const);
  const original = page.viewportSize();
  try {
    for (const width of viewports) {
      for (const theme of themes) {
        const label = `${width}px, tema ${theme}`;
        await page.setViewportSize({ width, height: original?.height ?? 900 });
        await page.emulateMedia({ colorScheme: theme });
        await page.waitForLoadState('domcontentloaded');
        const result = await new AxeBuilder({ page }).withTags(AXE_TAGS).exclude('iframe').analyze();
        const serious = result.violations
          .filter((v) => SERIOUS_IMPACTS.has(v.impact ?? ''))
          .map((v) => `${v.id} (${v.nodes.map((n) => n.target).join(' | ')})`);
        expect(serious, label).toEqual([]);
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, `${label}: rolagem horizontal`).toBeLessThanOrEqual(0);
      }
    }
  } finally {
    await page.emulateMedia({ colorScheme: null });
    if (original) await page.setViewportSize(original);
  }
}

/** Formas em que um valor sensível poderia aparecer: cru, codificado e, se for CPF, só dígitos ou mascarado. */
function sensitiveVariants(value: string): string[] {
  const variants = new Set([value, encodeURIComponent(value)]);
  const digits = value.replace(/\D/g, '');
  if (digits.length >= 6) variants.add(digits);
  if (digits.length === 11) {
    variants.add(`${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`);
  }
  return [...variants];
}

/**
 * Confere que a URL atual e o armazenamento do navegador (`localStorage`, `sessionStorage` e cookies)
 * não contêm o valor dado (por exemplo, o CPF digitado). A falha diz só onde achou, nunca o valor.
 */
export async function expectValueAbsentFromUrlAndStorage(page: Page, value: string): Promise<void> {
  const variants = sensitiveVariants(value);
  const contains = (text: string) => variants.some((variant) => text.includes(variant));

  expect(contains(page.url()), 'a URL contém o valor sensível').toBe(false);

  const stored = await page.evaluate(() => {
    const dump = (storage: Storage) =>
      Object.keys(storage).map((key) => `${key}=${storage.getItem(key) ?? ''}`);
    return { local: dump(window.localStorage), session: dump(window.sessionStorage) };
  });
  expect(stored.local.some(contains), 'o localStorage contém o valor sensível').toBe(false);
  expect(stored.session.some(contains), 'o sessionStorage contém o valor sensível').toBe(false);

  const cookies = await page.context().cookies();
  expect(
    cookies.some((cookie) => contains(`${cookie.name}=${cookie.value}`)),
    'um cookie contém o valor sensível',
  ).toBe(false);
}

/** Tela 05: anexa o áudio de teste e envia. */
export async function sendAudio(page: Page, studentId: string): Promise<void> {
  await page.goto(`/students/${studentId}/new-lesson`);
  await page.getByLabel('Arquivo de áudio').setInputFiles({
    name: 'lesson.wav',
    mimeType: 'audio/wav',
    buffer: testAudio(),
  });
  await page.getByRole('button', { name: 'Enviar áudio e gerar relatório' }).click();
}

/** Tela 03 → tela 04: "Registrar aula" no cartão de um aluno com relatório em aberto. */
export async function openOpenReport(page: Page, studentName: string): Promise<void> {
  await page.goto('/students');
  await page.getByRole('button', { name: `Registrar aula — ${studentName}` }).click();
  await expect(page.getByRole('heading', { name: 'Você tem um relatório em aberto' })).toBeVisible();
}

/** Da tela 07 (perguntas simuladas) até a conferência da versão 1. */
export async function answerQuestions(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible();
  await page.getByLabel('Sua resposta').fill('Estudou todos os dias; vai preparar a valsa.');
  await page.getByRole('button', { name: 'Enviar resposta' }).click();
  await expect(page.getByRole('heading', { name: 'Confira o relatório antes de aprovar' })).toBeVisible();
}

export async function approve(page: Page, version: number): Promise<void> {
  await page.getByRole('button', { name: `Aprovar relatório · versão ${version}` }).click();
  await page
    .getByRole('dialog', { name: `Aprovar a versão ${version}?` })
    .getByRole('button', { name: `Sim, aprovar a versão ${version}` })
    .click();
}

/** Registra e aprova uma aula pela Web, do envio do áudio à tela 09. */
export async function registerApprovedLesson(page: Page, studentId: string): Promise<void> {
  await sendAudio(page, studentId);
  await answerQuestions(page);
  await approve(page, 1);
  await expect(page.getByText(/Ele já consta no histórico do aluno\./)).toBeVisible();
}
