import { createCipheriv, createHmac, randomBytes, randomUUID } from 'node:crypto';
import { expect, type Page } from '@playwright/test';
import pg from 'pg';

/*
 * Apoio aos testes ponta a ponta: semeia professores e alunos fictícios direto no banco da pilha de
 * testes (mesmo HMAC e mesma cifra do backend, com os segredos de testes de compose.test.yaml) e
 * oferece os passos comuns da interface.
 */

const BANCO_URL = process.env.E2E_DATABASE_URL ?? 'postgres://relatorios:e2e@localhost:5432/relatorios';
const CPF_CHAVE = process.env.E2E_CPF_KEY ?? 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=';
const SEGREDO_HMAC = process.env.E2E_HMAC_SECRET ?? 'segredo-hmac-de-testes-e2e-com-mais-de-32-bytes';

export const CODIGO_CLARA = 'K7QM2XPA';
export const CODIGO_BRUNO = 'BRUN2XPA';

export interface Semeadura {
  clara: string;
  bruno: string;
  alunos: Record<'matteo' | 'olavo' | 'miguel' | 'helena', string>;
}

function hmac(valor: string): string {
  return createHmac('sha256', Buffer.from(SEGREDO_HMAC, 'utf8')).update(valor, 'utf8').digest('hex');
}

function cifrarCpf(digitos: string): Buffer {
  const iv = randomBytes(12);
  const cifra = createCipheriv('aes-256-gcm', Buffer.from(CPF_CHAVE, 'base64'), iv);
  const corpo = Buffer.concat([cifra.update(digitos, 'utf8'), cifra.final()]);
  return Buffer.concat([iv, corpo, cifra.getAuthTag()]);
}

export async function comBanco<T>(acao: (cliente: pg.Client) => Promise<T>): Promise<T> {
  const cliente = new pg.Client({ connectionString: BANCO_URL });
  await cliente.connect();
  try {
    return await acao(cliente);
  } finally {
    await cliente.end();
  }
}

/** Apaga tudo (menos o histórico do Flyway) e cria Clara (3 alunos) e Bruno (1 aluno). */
export async function semear(): Promise<Semeadura> {
  return comBanco(async (banco) => {
    const tabelas = await banco.query<{ tablename: string }>(
      "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
    );
    await banco.query(`truncate ${tabelas.rows.map((t) => `"${t.tablename}"`).join(', ')} cascade`);

    const agora = new Date();
    const validade = new Date(agora.getTime() + 7 * 24 * 3600 * 1000);
    const professor = async (nome: string, email: string, codigo: string) => {
      const id = randomUUID();
      await banco.query(
        `insert into teacher (id, name, email, active, access_code_hash, access_code_expires_at,
           access_code_renewed_at, created_at, updated_at) values ($1, $2, $3, true, $4, $5, $6, $6, $6)`,
        [id, nome, email, hmac(`codigo:${codigo}`), validade, agora],
      );
      return id;
    };
    const aluno = async (professorId: string, nome: string, cpf: string, instrumento: string) => {
      const id = randomUUID();
      await banco.query(
        `insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, guardian_name, instrument,
           active, created_at, updated_at) values ($1, $2, '2012-05-10', $3, $4, null, $5, true, $6, $6)`,
        [id, nome, cifrarCpf(cpf), hmac(`cpf:${cpf}`), instrumento, agora],
      );
      await banco.query(
        'insert into teacher_student_assignment (id, teacher_id, student_id, created_at) values ($1, $2, $3, $4)',
        [randomUUID(), professorId, id, agora],
      );
      return id;
    };

    const clara = await professor('Clara Souza', 'clara@example.test', CODIGO_CLARA);
    const bruno = await professor('Bruno Lima', 'bruno@example.test', CODIGO_BRUNO);
    return {
      clara,
      bruno,
      alunos: {
        matteo: await aluno(clara, 'Matteo Mafra', '52998224725', 'Piano'),
        olavo: await aluno(clara, 'Olavo Torrente', '12345678909', 'Violão'),
        miguel: await aluno(clara, 'Miguel de Castro Torrente', '70451283635', 'Violino'),
        helena: await aluno(bruno, 'Helena Prado', '31826459782', 'Flauta'),
      },
    };
  });
}

/** WAV PCM mono de 3 s com um tom de 440 Hz (o backend normaliza com FFmpeg; a IA é simulada). */
export function audioDeTeste(segundos = 3): Buffer {
  const taxa = 16_000;
  const amostras = taxa * segundos;
  const wav = Buffer.alloc(44 + amostras * 2);
  wav.write('RIFF', 0);
  wav.writeUInt32LE(36 + amostras * 2, 4);
  wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(taxa, 24);
  wav.writeUInt32LE(taxa * 2, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write('data', 36);
  wav.writeUInt32LE(amostras * 2, 40);
  for (let i = 0; i < amostras; i++) {
    wav.writeInt16LE(Math.round(Math.sin((2 * Math.PI * 440 * i) / taxa) * 0.3 * 32767), 44 + i * 2);
  }
  return wav;
}

export async function entrar(page: Page, codigo = CODIGO_CLARA): Promise<void> {
  await page.goto('/entrar');
  await page.getByLabel('Código de acesso').fill(codigo);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Olá');
}

/** Tela 05: anexa o áudio de teste e envia. */
export async function enviarAudio(page: Page, alunoId: string): Promise<void> {
  await page.goto(`/alunos/${alunoId}/nova-aula`);
  await page.getByLabel('Arquivo de áudio').setInputFiles({
    name: 'aula.wav',
    mimeType: 'audio/wav',
    buffer: audioDeTeste(),
  });
  await page.getByRole('button', { name: 'Enviar áudio e gerar relatório' }).click();
}

/** Tela 03 → tela 04: "Registrar aula" no cartão de um aluno com relatório em aberto. */
export async function abrirRelatorioEmAberto(page: Page, nomeAluno: string): Promise<void> {
  await page.goto('/alunos');
  await page.getByRole('button', { name: `Registrar aula — ${nomeAluno}` }).click();
  await expect(page.getByRole('heading', { name: 'Você tem um relatório em aberto' })).toBeVisible();
}

/** Da tela 07 (perguntas simuladas) até a conferência da versão 1. */
export async function responderPerguntas(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Faltam algumas informações' })).toBeVisible();
  await page.getByLabel('Sua resposta').fill('Estudou todos os dias; vai preparar a valsa.');
  await page.getByRole('button', { name: 'Enviar resposta' }).click();
  await expect(page.getByRole('heading', { name: 'Confira o relatório antes de aprovar' })).toBeVisible();
}

export async function aprovar(page: Page, versao: number): Promise<void> {
  await page.getByRole('button', { name: `Aprovar relatório · versão ${versao}` }).click();
  await page
    .getByRole('dialog', { name: `Aprovar a versão ${versao}?` })
    .getByRole('button', { name: `Sim, aprovar a versão ${versao}` })
    .click();
}

/** Registra e aprova uma aula pela Web, do envio do áudio à tela 09. */
export async function registrarAulaAprovada(page: Page, alunoId: string): Promise<void> {
  await enviarAudio(page, alunoId);
  await responderPerguntas(page);
  await aprovar(page, 1);
  await expect(page.getByText(/Ele já consta no histórico do aluno\./)).toBeVisible();
}
