import { createCipheriv, createHmac, randomBytes, randomUUID } from 'node:crypto';
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

    const clara = await teacher('Clara Souza', 'clara@example.test', CODE_CLARA);
    const bruno = await teacher('Bruno Lima', 'bruno@example.test', CODE_BRUNO);
    return {
      clara,
      bruno,
      students: {
        matteo: await student(clara, 'Matteo Mafra', '52998224725', 'Piano'),
        olavo: await student(clara, 'Olavo Torrente', '12345678909', 'Violão'),
        miguel: await student(clara, 'Miguel de Castro Torrente', '70451283635', 'Violino'),
        helena: await student(bruno, 'Helena Prado', '31826459782', 'Flauta'),
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
