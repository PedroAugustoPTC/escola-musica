import { argon2Sync, createCipheriv, createHmac, randomBytes, randomUUID } from 'node:crypto';
import AxeBuilder from '@axe-core/playwright';
import { expect, type Locator, type Page } from '@playwright/test';
import pg from 'pg';

/*
 * Apoio aos testes ponta a ponta: semeia professores e alunos fictícios direto no banco da pilha de
 * testes (mesmo HMAC e mesma cifra do backend, com os segredos de testes de compose.test.yaml) e
 * oferece os passos comuns da interface.
 *
 * Régua do axe (SC-012): a spec pede "sem violação séria ou crítica", então `expectNoSeriousViolations` reprova
 * só os impactos `serious` e `critical` (tags WCAG 2.0/2.1/2.2 A e AA). Violações `moderate` e `minor` não
 * derrubam o build: são sugestões de boa prática, muitas sem critério de sucesso WCAG, e tratá-las como falha
 * trocaria a régua que a spec definiu. As specs antigas do professor (`accessibility.spec.ts`) usam régua mais
 * estrita (qualquer violação) e continuam assim.
 */

const DATABASE_URL = process.env.E2E_DATABASE_URL ?? 'postgres://reports:e2e@localhost:5432/reports';
const CPF_KEY = process.env.E2E_CPF_KEY ?? 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=';
const HMAC_SECRET = process.env.E2E_HMAC_SECRET ?? 'hmac-secret-for-e2e-tests-with-more-than-32-bytes';

export const CODE_CLARA = 'K7QM2XPA';
export const CODE_BRUNO = 'BRUN2XPA';

// CPFs sintéticos: dígitos verificadores válidos, mas de exemplo público de geradores de teste
// (e o CPF canário da feature 005, 11144477735); nenhum pertence a uma pessoa real.
export const CPF_MATTEO = '52998224725';
export const CPF_OLAVO = '12345678909';
export const CPF_MIGUEL = '11144477735';
export const CPF_HELENA = '39053344705';

/**
 * Data de nascimento (`AAAA-MM-DD`) de quem tem hoje `years` anos completos (menos um dia de folga, para
 * o aniversário não cair no limite). Os testes de "menor de idade" não podem fixar uma data: com o tempo
 * ela deixaria de ser de menor e o teste passaria a quebrar sem que nada tivesse mudado.
 */
export function birthDateYearsAgo(years: number, today: Date = new Date()): string {
  const date = new Date(today.getFullYear() - years, today.getMonth(), today.getDate() - 1);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Data de nascimento de um menor de idade (10 anos), sempre relativa a hoje. */
export function minorBirthDate(): string {
  return birthDateYearsAgo(10);
}

export interface Seeding {
  clara: string;
  bruno: string;
  administrator: string;
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

async function insertTeacher(
  database: pg.Client,
  name: string,
  email: string,
  code: string,
  now: Date,
  validity: Date,
): Promise<string> {
  const id = randomUUID();
  await database.query(
    `insert into teacher (id, name, email, active, access_code_hash, access_code_expires_at,
       access_code_renewed_at, created_at, updated_at) values ($1, $2, $3, true, $4, $5, $6, $6, $6)`,
    [id, name, email, hmac(`codigo:${code}`), validity, now],
  );
  return id;
}

export const CODE_DIANA = 'DRAN2XPA';

/** Professora sem alunos (estado vazio de "Meus alunos"), acrescentada depois da semeadura. */
export async function addTeacherWithoutStudents(): Promise<{ id: string; code: string }> {
  const now = new Date();
  const id = await withDatabase((database) =>
    insertTeacher(
      database,
      'Diana Rocha',
      'diana@example.test',
      CODE_DIANA,
      now,
      new Date(now.getTime() + 7 * 24 * 3600 * 1000),
    ),
  );
  return { id, code: CODE_DIANA };
}

/** CPF sintético com dígitos verificadores válidos, a partir de 9 dígitos-base (nenhum é de pessoa real). */
export function syntheticCpf(base: string): string {
  const digits = base.split('').map(Number);
  const verifier = (length: number) => {
    const sum = digits
      .slice(0, length)
      .reduce((total, digit, index) => total + digit * (length + 1 - index), 0);
    const rest = (sum * 10) % 11;
    return rest === 10 ? 0 : rest;
  };
  digits.push(verifier(9));
  digits.push(verifier(10));
  return digits.join('');
}

/** O mesmo CPF com a máscara `000.000.000-00`, como o aluno o digita. */
export function maskedCpf(digits: string): string {
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
}

/** Parâmetros do Argon2id do backend (padrões de `ADMIN_PASSWORD_ARGON2_*`); o formato guarda os parâmetros. */
const ARGON2_MEMORY_KIB = 19_456;
const ARGON2_ITERATIONS = 2;

/**
 * Hash Argon2id no formato codificado que o backend lê (`$argon2id$v=19$m=…,t=…,p=1$sal$hash`, sem
 * preenchimento). A senha é gerada na hora da execução e nunca é gravada no repositório: vive só na
 * memória do processo do Playwright e no banco descartável do teste.
 */
function argon2idHash(password: string): string {
  const salt = randomBytes(16);
  const tag = argon2Sync('argon2id', {
    message: Buffer.from(password.normalize('NFC'), 'utf8'),
    nonce: salt,
    parallelism: 1,
    tagLength: 32,
    memory: ARGON2_MEMORY_KIB,
    passes: ARGON2_ITERATIONS,
  });
  const base64 = (bytes: Buffer) => bytes.toString('base64').replace(/=+$/, '');
  return `$argon2id$v=19$m=${ARGON2_MEMORY_KIB},t=${ARGON2_ITERATIONS},p=1$${base64(salt)}$${base64(tag)}`;
}

/** Senha aleatória de teste (alfanumérica mais `-`/`_`), longa o bastante para a regra de 12 a 128 caracteres. */
export function randomPassword(): string {
  return `Sx${randomBytes(18).toString('base64url')}9`;
}

const generatedAdministratorPassword = randomPassword();

export const ADMIN_EMAIL = 'admin@example.test';
export const ADMIN_NAME = 'Administradora de Teste';

export interface SeedOptions {
  /** Semeia o administrador com senha provisória (bootstrap): o primeiro acesso exige a troca (A03). */
  provisionalAdministrator?: boolean;
}

/**
 * Apaga tudo (menos o histórico do Flyway) e cria o administrador de teste, Clara (3 alunos) e Bruno
 * (1 aluno).
 *
 * O administrador entra pelo banco, já ativado e com hash Argon2id gerado em tempo de execução: o
 * bootstrap do backend (`ADMIN_BOOTSTRAP_*`) cria o administrador só uma vez, na subida, e cada teste
 * começa apagando as tabelas; semear aqui deixa cada teste com a conta num estado conhecido. A senha é
 * `E2E_ADMIN_PASSWORD`, se definida, ou uma senha aleatória do processo (nunca em arquivo versionado).
 */
export async function seed(options: SeedOptions = {}): Promise<Seeding> {
  return withDatabase(async (database) => {
    const tables = await database.query<{ tablename: string }>(
      "select tablename from pg_tables where schemaname = 'public' and tablename not in ('flyway_schema_history', 'authorship_record', 'audit_chain_anchor', 'erased_student')",
    );
    await database.query(`truncate ${tables.rows.map((t) => `"${t.tablename}"`).join(', ')} cascade`);
    // O registro de autoria é somente de acréscimo (gatilhos da V10): o teste, dono do banco descartável,
    // suspende os gatilhos de usuário na mesma instrução composta (uma transação) só para limpá-lo.
    await database.query(
      'alter table authorship_record disable trigger user; alter table audit_chain_anchor disable trigger user; alter table erased_student disable trigger user; truncate table authorship_record, audit_chain_anchor, erased_student; alter table authorship_record enable trigger user; alter table audit_chain_anchor enable trigger user; alter table erased_student enable trigger user',
    );

    const now = new Date();
    const validity = new Date(now.getTime() + 7 * 24 * 3600 * 1000);
    // alunos semeados são menores (14 anos), sempre relativos a hoje
    const seededBirthDate = birthDateYearsAgo(14);
    const credentials = administratorCredentialsFromEnvironment();
    const administrator = randomUUID();
    const provisional = options.provisionalAdministrator === true;
    await database.query(
      `insert into administrator (id, name, email, active, password_hash, password_provisional,
         password_provisional_expires_at, activated_at, created_at, updated_at)
       values ($1, $2, $3, true, $4, $5, $6, $7, $7, $7)`,
      [
        administrator,
        ADMIN_NAME,
        credentials.email,
        argon2idHash(credentials.password),
        provisional,
        provisional ? validity : null,
        now,
      ],
    );
    // termo de consentimento vigente e consentimento válido dos alunos semeados (feature 007): sem ele a IA
    // fica bloqueada para menor
    const term = randomUUID();
    await database.query(
      `insert into consent_term_version (id, number, published_on, text, requires_new_consent, created_by, created_at)
       values ($1, 1, current_date - 30, $2, false, $3, $4)`,
      [term, 'Termo de teste.', administrator, now],
    );
    const teacher = (name: string, email: string, code: string) =>
      insertTeacher(database, name, email, code, now, validity);
    const student = async (teacherId: string, name: string, cpf: string, instrument: string) => {
      const id = randomUUID();
      await database.query(
        `insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, guardian_name, instrument,
           active, created_at, updated_at) values ($1, $2, $7, $3, $4, null, $5, true, $6, $6)`,
        [id, name, encryptCpf(cpf), hmac(`cpf:${cpf}`), instrument, now, seededBirthDate],
      );
      await database.query(
        'insert into teacher_student_assignment (id, teacher_id, student_id, created_at) values ($1, $2, $3, $4)',
        [randomUUID(), teacherId, id, now],
      );
      await database.query(
        `insert into student_consent (id, student_id, consenting_name, relationship, consented_on, term_version_id,
           collection_method, recorded_origin, recorded_at) values ($1, $2, 'Responsável de Teste', 'MOTHER', current_date - 20, $3, 'SIGNED_PAPER', 'INITIAL_LOAD', $4)`,
        [randomUUID(), id, term, now],
      );
      return id;
    };

    const clara = await teacher('Clara Souza', 'clara@example.test', CODE_CLARA);
    const bruno = await teacher('Bruno Lima', 'bruno@example.test', CODE_BRUNO);
    return {
      clara,
      bruno,
      administrator,
      students: {
        matteo: await student(clara, 'Matteo Mafra', CPF_MATTEO, 'Piano'),
        olavo: await student(clara, 'Olavo Torrente', CPF_OLAVO, 'Violão'),
        miguel: await student(clara, 'Miguel de Castro Torrente', CPF_MIGUEL, 'Violino'),
        helena: await student(bruno, 'Helena Prado', CPF_HELENA, 'Flauta'),
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
 * Credenciais do administrador da pilha de testes: `E2E_ADMIN_EMAIL` e `E2E_ADMIN_PASSWORD`, se
 * definidos; senão, o e-mail de teste e a senha aleatória deste processo. A senha nunca fica no
 * código nem em log.
 */
export function administratorCredentialsFromEnvironment(): AdministratorCredentials {
  return {
    email: process.env.E2E_ADMIN_EMAIL ?? ADMIN_EMAIL,
    password: process.env.E2E_ADMIN_PASSWORD || generatedAdministratorPassword,
  };
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
  /** Nome da tela, para identificar a falha (por exemplo, "A05 professores"). */
  name?: string;
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
  const originalTheme = await page.evaluate(() => document.documentElement.dataset.theme ?? null);
  try {
    for (const width of viewports) {
      for (const theme of themes) {
        const label = `${options.name ? `${options.name} · ` : ''}${width}px, tema ${theme}`;
        await page.setViewportSize({ width, height: original?.height ?? 900 });
        await page.emulateMedia({ colorScheme: theme });
        // O ThemeProvider lê o tema uma vez, na carga; trocar o tema depois dela é o que o botão "Modo escuro"
        // faz: gravar `data-theme` na raiz. Sem isso o tema escuro nunca seria conferido numa página já aberta.
        await page.evaluate((value) => {
          document.documentElement.dataset.theme = value;
        }, theme);
        await page.waitForLoadState('domcontentloaded');
        // dois quadros para o React refazer o layout (o `useIsMobile` escuta a mudança de largura)
        await page.evaluate(
          () => new Promise<void>((done) => requestAnimationFrame(() => requestAnimationFrame(() => done()))),
        );
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
    await page.evaluate((value) => {
      if (value === null) delete document.documentElement.dataset.theme;
      else document.documentElement.dataset.theme = value;
    }, originalTheme);
    if (original) await page.setViewportSize(original);
  }
}

/** Todas as telas conferidas em claro/escuro e 390/1440 px, para o relatório de evidência (SC-012). */
export const SCREEN_MATRIX = { themes: ['light', 'dark'], viewports: [390, 1440] } as const;

/**
 * Teclado: aperta Tab até o foco chegar ao alvo (no máximo `limit` vezes) e devolve quantas vezes foi
 * preciso. Falha se o foco não chega ao alvo ou se sai da página.
 */
export async function tabUntilFocused(page: Page, target: Locator, limit = 60): Promise<number> {
  for (let presses = 0; presses <= limit; presses++) {
    const focused = await target
      .first()
      .evaluate((element) => element === document.activeElement, undefined, { timeout: 1_000 })
      .catch(() => false);
    if (focused) return presses;
    await page.keyboard.press('Tab');
  }
  throw new Error(`O foco não chegou ao elemento esperado em ${limit} toques de Tab.`);
}

/** A ordem de foco por Tab segue a dos alvos dados (cada um alcançado depois do anterior). */
export async function expectFocusOrder(page: Page, targets: readonly Locator[], limit = 60): Promise<void> {
  for (const target of targets) await tabUntilFocused(page, target, limit);
}

/**
 * Diálogo modal acessível: tem nome e descrição, o foco entra nele, Tab e Shift+Tab ficam presos dentro
 * dele, Esc o fecha e o foco volta ao disparador.
 */
export async function expectModalDialogBehavior(
  page: Page,
  dialog: Locator,
  trigger: Locator,
): Promise<void> {
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  await expect(dialog).toHaveAccessibleName(/\S/);
  await expect(dialog).toHaveAccessibleDescription(/\S/);
  const insideDialog = () => dialog.evaluate((box) => box.contains(document.activeElement));
  expect(await insideDialog(), 'o foco entra no diálogo ao abrir').toBe(true);
  for (const key of ['Tab', 'Tab', 'Tab', 'Tab', 'Tab', 'Shift+Tab', 'Shift+Tab', 'Shift+Tab']) {
    await page.keyboard.press(key);
    expect(await insideDialog(), `o foco continua preso no diálogo após ${key}`).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(trigger).toBeFocused();
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

/** Vincula uma conta fictícia do Telegram ao professor (o vínculo real nasce no bot; aqui só interessa a tela). */
export async function linkTelegram(teacherId: string): Promise<void> {
  await withDatabase((database) =>
    database.query(
      `insert into telegram_link (id, teacher_id, telegram_user_id, chat_id, linked_at, flow, flow_step,
         flow_data, updated_at, lock_version) values ($1, $2, $3, $3, now(), 'NONE', 'NONE', '{}'::jsonb, now(), 0)`,
      [randomUUID(), teacherId, 700_000_001 + Math.floor(Math.random() * 1_000_000)],
    ),
  );
}

/** Preenche a seção "Consentimento do responsável" (aparece quando a data de nascimento indica menor de 18 anos). */
export async function fillConsent(page: Page, consentingName = 'Sandra Lopes'): Promise<void> {
  await page.getByLabel('Nome de quem consentiu').fill(consentingName);
  await page.getByLabel('Relação com o aluno').selectOption('MOTHER');
  await page.getByLabel('Data do consentimento').fill(new Date().toISOString().slice(0, 10));
  await page.getByLabel('Forma de coleta').selectOption('SIGNED_PAPER');
}
