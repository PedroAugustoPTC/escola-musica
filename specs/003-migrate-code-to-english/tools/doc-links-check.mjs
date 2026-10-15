#!/usr/bin/env node
// Verificador de referências da documentação (task T227, SC-007): para cada `CLAUDE.md`, `README.md` e o `quickstart.md` da
// feature 003, confere que o que o texto cita existe no repositório.
//
//   node doc-links-check.mjs [raiz]
//
// Confere (1) links relativos de Markdown; (2) caminhos e nomes de arquivo/pasta em crase; (3) subcomandos de
// `local.sh <comando>`; (4) variáveis de ambiente em crase (MAIÚSCULAS_COM_SUBLINHADO) que não aparecem em nenhum
// arquivo versionado fora da documentação; (5) nomes de classe com sufixo padronizado (`*UseCase`, `*Port`…), que têm de ter
// um `.java` correspondente. Os quickstarts e demais documentos das specs 001 e 002 são histórico (têm nota no topo) e ficam
// de fora. Sai com código 1 quando acha qualquer referência quebrada.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const root = resolve(process.argv[2] ?? process.cwd());
const tracked = execFileSync('git', ['ls-files'], { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).split('\n').filter(Boolean);
const trackedSet = new Set(tracked);

const docs = tracked.filter((file) => /(^|\/)(CLAUDE|README)\.md$/.test(file) || file === 'specs/003-migrate-code-to-english/quickstart.md');
const nonDocs = tracked.filter((file) => !/\.md$/.test(file) && !file.startsWith('specs/') && !file.endsWith('.csv'));
const corpus = nonDocs
  .filter((file) => !/(package-lock\.json|\.png|\.jpg|\.woff2?|\.ttf|\.otf|\.pdf|\.ogg|\.mp4)$/.test(file))
  .map((file) => {
    try {
      return readFileSync(join(root, file), 'utf8');
    } catch {
      return '';
    }
  })
  .join('\n');
const javaNames = new Set(tracked.filter((file) => file.endsWith('.java')).map((file) => file.split('/').pop().replace('.java', '')));
const localSh = readFileSync(join(root, 'infra/scripts/local.sh'), 'utf8');
const subcommands = new Set([...localSh.matchAll(/^ {2}([a-z|-]+)\)/gm)].flatMap((m) => m[1].split('|')));

const EXT = /\.(md|json|ya?ml|sh|java|tsx?|mjs|xml|sql|css|html|properties|png|csv|example|txt)$/;
const exists = (candidate) => trackedSet.has(candidate) || tracked.some((file) => file.startsWith(`${candidate}/`));

// Citados de propósito, embora não existam como arquivo versionado: saídas geradas/ignoradas pelo Git, o arquivo de exclusões que a
// migração apagou (os documentos explicam que ele NÃO existe mais) e as pastas de exemplo do `assets/README.md`.
const ALLOWED = [/playwright-report\/?$/, /test-results\//, /glossary-temporary-exclusions\.json$/, /^assets\/(identidade-visual|documentos)\/$/];

const broken = [];
let checked = 0;
for (const doc of docs) {
  const text = readFileSync(join(root, doc), 'utf8');
  const base = dirname(doc);
  const lines = text.split('\n');
  let fenced = false;
  lines.forEach((line, index) => {
    if (line.trim().startsWith('```')) {
      fenced = !fenced;
      return;
    }
    const where = `${doc}:${index + 1}`;
    // (1) links relativos
    for (const m of line.matchAll(/\]\(([^)\s]+)\)/g)) {
      const target = m[1].split('#')[0];
      if (!target || /^(https?:|mailto:)/.test(target)) continue;
      checked += 1;
      const resolved = join(base, target).replace(/\\/g, '/').replace(/\/$/, '');
      if (!exists(resolved)) broken.push(`${where}: link quebrado → ${m[1]}`);
    }
    // (3) subcomandos do local.sh (em crase ou em bloco)
    // comandos da `main` (nomes antigos) são citados de propósito quando a linha diz que são da `main` ou "antes da fatia 6"
    const legacy = /\bmain\b|antes da fatia 6/.test(line);
    for (const m of legacy ? [] : line.matchAll(/local\.sh ([a-z][a-z-]*)/g)) {
      checked += 1;
      if (!subcommands.has(m[1])) broken.push(`${where}: subcomando inexistente em local.sh → ${m[1]}`);
    }
    if (fenced) return;
    // linha de correspondência antigo → novo ("`A` → `B`"): só o lado novo precisa existir
    const afterArrow = line.includes('→') && /`[^`]+`\s*→\s*`/.test(line) ? line.slice(line.lastIndexOf('→')) : line;
    for (const m of afterArrow.matchAll(/`([^`\n]+)`/g)) {
      const token = m[1].trim();
      if (/[\s*<>{}$|=()[\]'"!,;:~]|\.\.\./.test(token) || token.startsWith('-') || /^https?:/.test(token)) {
        // (4) variável de ambiente com valor, ex.: `AI_PROVIDERS=real` → confere só o nome
        const variable = token.match(/^([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)(?:=.*)?$/);
        if (variable) {
          checked += 1;
          if (!corpus.includes(variable[1])) broken.push(`${where}: variável sem uso no repositório → ${variable[1]}`);
        }
        continue;
      }
      // (2) caminho ou arquivo
      if ((token.includes('/') && EXT.test(token)) || /^[A-Za-z0-9_.-]+\.(sh|ya?ml|json|mjs)$/.test(token) || (token.endsWith('/') && token.replace(/\/$/, '').includes('/'))) {
        checked += 1;
        const clean = token.replace(/\/$/, '');
        const candidates = [clean, join(base, clean).replace(/\\/g, '/'), `${base}/${clean}`, `infra/${clean}`, `backend/${clean}`, `.github/${clean}`, `frontend/${clean}`];
        const found = candidates.some(exists) || tracked.some((file) => file.endsWith(`/${clean}`) || file === clean);
        const generated = ALLOWED.some((rule) => rule.test(token) || rule.test(clean)) || /api-types\.ts$|messages\.json$/.test(clean) || /^infra\/\.env$|^\.env$|^infra\/seed\/\*/.test(clean) || clean.startsWith('target/') || clean.includes('/target/');
        if (!found && !generated) broken.push(`${where}: caminho inexistente → ${token}`);
        continue;
      }
      // (4) variável de ambiente
      if (/^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/.test(token)) {
        checked += 1;
        if (!corpus.includes(token)) broken.push(`${where}: variável sem uso no repositório → ${token}`);
        continue;
      }
      // (5) classe Java com sufixo padronizado
      if (/^[A-Z][A-Za-z0-9]*(UseCase|Port|Repository|Service|Adapter|Controller|Command|Filter|Executor|Test|IT)$/.test(token)) {
        checked += 1;
        if (!javaNames.has(token)) broken.push(`${where}: classe inexistente → ${token}`);
      }
    }
  });
}

console.log(`doc-links-check: ${docs.length} documentos, ${checked} referências conferidas, ${broken.length} quebradas`);
for (const item of broken) console.log(`  ${item}`);
process.exit(broken.length ? 1 : 0);
