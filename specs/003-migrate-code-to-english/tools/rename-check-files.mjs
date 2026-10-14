// Conferência dos arquivos que não são Java na fatia 3 (T057, T098): pareia cada arquivo da árvore-base com o do
// trabalho (pasta e nome novos pelo `identifier-map.md` §8) e prova que SÓ o nome mudou, pelo modo do arquivo:
//   identical   mesmo conteúdo, byte a byte (migrações V1–V6, fontes, imagens, catálogo de mensagens, texto simulado)
//   ident       todo identificador pelo mapa (esquemas de IA, JSON simulado e da carga, CSS, HTML de PDF, XML, shell)
//   config      configuração do Spring (`application*.yaml`): chaves, variáveis e valores de enumeração de configuração
//   env         variáveis de ambiente da lista fechada `envNames` (compose, `.env.example`, `local.sh`, Caddyfile)
//   prompt      prompts: só os nomes de campo do conjunto fechado (B2), palavra a palavra; o resto idêntico
//   path        arquivo de texto em que só caminhos/módulo mudam (Dockerfile, pom, script de mensagens)
// Devolve { errors, infos } no mesmo formato do `rename-check` (listas por categoria).
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { alignRestricted, alignSimple } from './lib/align-simple.mjs';
import { kindOf } from './lib/simple-lexers.mjs';

const R = 'backend/application/src/main/resources';
const OLD = 'backend/aplicacao/src/main/resources';
const PROMPTS = {
  'consolidacao-semestral.v1.txt': 'semester-consolidation.v1.txt',
  'correcao-json.v1.txt': 'json-correction.v1.txt',
  'estruturacao-aula.v1.txt': 'lesson-structuring.v1.txt',
  'incorporacao-respostas.v1.txt': 'answer-incorporation.v1.txt',
  'revisao-aula.v1.txt': 'lesson-revision.v1.txt',
  'revisao-semestral.v1.txt': 'semester-revision.v1.txt',
};

function* files(directory) {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* files(full);
    else yield full;
  }
}

/** Lista fechada de pares [base, trabalho, modo]. Caminhos relativos à raiz de cada árvore. */
export function filePairs(baseRoot) {
  const pairs = [];
  const add = (base, work, mode, extra = {}) => pairs.push({ base, work, mode, ...extra });
  add(`${OLD}/application.yaml`, `${R}/application.yaml`, 'config');
  add('backend/aplicacao/src/test/resources/application-teste.yaml', 'backend/application/src/test/resources/application-test.yaml', 'config');
  for (const file of files(join(baseRoot, `${OLD}/db/migration`))) {
    const name = file.split(/[\\/]/).at(-1);
    add(`${OLD}/db/migration/${name}`, `${R}/db/migration/${name}`, 'identical');
  }
  add(`${OLD}/ia/schemas/relatorio-aula.schema.json`, `${R}/ai/schemas/lesson-report.schema.json`, 'ident', { schema: true });
  add(`${OLD}/ia/schemas/relatorio-semestral.schema.json`, `${R}/ai/schemas/semester-report.schema.json`, 'ident', { schema: true });
  add(`${OLD}/ia/simulado/estruturacao-aula.json`, `${R}/ai/simulated/lesson-structuring.json`, 'ident');
  add(`${OLD}/ia/simulado/consolidacao-semestral.json`, `${R}/ai/simulated/semester-consolidation.json`, 'ident');
  add(`${OLD}/ia/simulado/transcricao.txt`, `${R}/ai/simulated/transcription.txt`, 'identical');
  add(`${OLD}/logback-spring.xml`, `${R}/logback-spring.xml`, 'ident');
  add(`${OLD}/mensagens/mensagens_pt_BR.properties`, `${R}/messages/messages_pt_BR.properties`, 'identical');
  add(`${OLD}/pdf/estilos-pdf.css`, `${R}/pdf/pdf-styles.css`, 'ident');
  add(`${OLD}/pdf/relatorio-aula.html`, `${R}/pdf/lesson-report.html`, 'ident');
  add(`${OLD}/pdf/relatorio-semestral.html`, `${R}/pdf/semester-report.html`, 'ident');
  for (const file of files(join(baseRoot, `${OLD}/pdf/fontes`))) {
    const name = file.split(/[\\/]/).at(-1);
    add(`${OLD}/pdf/fontes/${name}`, `${R}/pdf/fonts/${name}`, 'identical');
  }
  for (const file of files(join(baseRoot, `${OLD}/pdf/imagens`))) {
    const name = file.split(/[\\/]/).at(-1);
    add(`${OLD}/pdf/imagens/${name}`, `${R}/pdf/images/${name}`, 'identical');
  }
  for (const [antigo, novo] of Object.entries(PROMPTS)) add(`${OLD}/prompts/${antigo}`, `${R}/prompts/${novo}`, 'prompt');
  for (const name of ['.env.example', 'compose.yaml', 'compose.dev.yaml', 'compose.prod.yaml', 'compose.test.yaml', 'compose.banco.yaml', 'caddy/Caddyfile', 'scripts/local.sh']) {
    if (existsSync(join(baseRoot, 'infra', name))) add(`infra/${name}`, `infra/${name}`, 'env');
  }
  for (const file of files(join(baseRoot, 'infra/carga'))) {
    const name = file.split(/[\\/]/).at(-1);
    add(`infra/carga/${name}`, `infra/carga/${name}`, 'ident');
  }
  add('backend/aplicacao/docker-entrypoint.sh', 'backend/application/docker-entrypoint.sh', 'ident');
  add('backend/aplicacao/Dockerfile', 'backend/application/Dockerfile', 'path');
  add('backend/aplicacao/pom.xml', 'backend/application/pom.xml', 'path');
  add('backend/pom.xml', 'backend/pom.xml', 'path');
  add('frontend/Dockerfile', 'frontend/Dockerfile', 'path');
  add('frontend/scripts/gerar-mensagens.mjs', 'frontend/scripts/gerar-mensagens.mjs', 'path');
  return pairs;
}

/** Aplica à base as edições de texto exato registradas em `fileEdits` (as mesmas de `rewrite/apply-file-edits.mjs`). */
function applyFileEdits(text, edits = []) {
  return edits.reduce((current, edit) => current.split(edit.from).join(edit.to), text);
}

const sha256 = (file) => createHash('sha256').update(readFileSync(file)).digest('hex');

export function checkFiles({ baseRoot, workRoot, map, heldFields }) {
  const errors = { pair: [], literal: [], unpaired: [], support: [] };
  const infos = { files: 0, tokens: 0, commentsChanged: 0, singleWord: 0, manual: [], byMode: new Map() };
  const envNames = new Set(map.explicit.envNames ?? []);
  const keepProse = new Set();
  for (const pair of filePairs(baseRoot)) {
    const baseFile = join(baseRoot, pair.base);
    const workFile = join(workRoot, pair.work);
    if (!existsSync(baseFile)) continue; // ainda não existe na base (arquivo opcional de uma árvore)
    if (!existsSync(workFile)) {
      errors.unpaired.push(`${pair.base} → ${pair.work}: arquivo da base sem par no trabalho`);
      continue;
    }
    infos.files++;
    infos.byMode.set(pair.mode, (infos.byMode.get(pair.mode) ?? 0) + 1);
    const report = (result) => {
      infos.tokens += result.tokens ?? 0;
      infos.commentsChanged += result.commentsChanged ?? 0;
      infos.singleWord += (result.singleWordLiterals ?? []).length;
      for (const manual of result.manualLiterals ?? []) infos.manual.push({ file: pair.work, ...manual });
      for (const error of result.errors) {
        const where = `${pair.work}:${error.line}`;
        if (error.kind === 'par' || error.kind === 'par-fora-do-conjunto') errors.pair.push(`${where}: "${error.from}" → "${error.to}" fora do que o modo ${pair.mode} aceita`);
        else if (error.kind === 'literal') errors.literal.push(`${where}: literal alterado ${JSON.stringify(error.from)} → ${JSON.stringify(error.to)}`);
        else errors.support.push(`${where}: ${error.kind} ${JSON.stringify(error.from)} → ${JSON.stringify(error.to)}`);
      }
    };
    if (pair.mode === 'identical') {
      if (sha256(baseFile) !== sha256(workFile)) errors.literal.push(`${pair.work}: conteúdo diferente do de ${pair.base} (deveria ser idêntico)`);
      continue;
    }
    const baseText = applyFileEdits(readFileSync(baseFile, 'utf8'), map.explicit.fileEdits?.[pair.work]);
    const workText = readFileSync(workFile, 'utf8');
    const kind = kindOf(pair.work);
    if (pair.mode === 'prompt') report(alignRestricted('text', baseText, workText, { map, file: pair.work, only: heldFields, wordLevel: true }));
    else if (pair.mode === 'env') {
      const mapEnvText = (text) => text.replace(/\b[A-Z][A-Z0-9_]*\b/g, (word) => (envNames.has(word) ? (map.ident.get(word) ?? word) : word)).replace(/(AI_PROVIDERS[=:] ?)simulado/g, '$1simulated');
      report(alignRestricted(kind, baseText, workText, { map, file: pair.work, only: new Set([...envNames, ...(map.explicit.configValues ? Object.keys(map.explicit.configValues) : [])]), stringTransform: mapEnvText }));
    }
    else if (pair.mode === 'path') {
      if (baseText !== workText) errors.literal.push(`${pair.work}: difere de ${pair.base} além das edições registradas em fileEdits`);
    }
    else report(alignSimple(kind, baseText, workText, { map, file: pair.work, schema: Boolean(pair.schema) }));
  }
  void keepProse;
  void statSync;
  return { errors, infos };
}
