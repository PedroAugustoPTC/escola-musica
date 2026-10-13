#!/usr/bin/env node
// Gerador do mapa completo de identificadores de uma fatia (tasks T006/T032, plan.md "Fatias").
//
//   node gen-identifier-map.mjs --slice <N> [--root <raiz>] [--glossary <caminho>] [--out <arquivo>]
//
// O português inverte a ordem substantivo-adjetivo, então a tradução NÃO é palavra a palavra:
//   RelatorioAula → LessonReport · codigo_acesso_hash → access_code_hash · dataNascimento → birthDate
//   ultimo_uso_em → last_used_at · RegistroCustoPorta → CostRecordPort · GravarAcoesCanal → SaveChannelActions
// A sugestão é um ponto de partida: tudo o que reordena, usa contexto, conectivo ou sufixo sai com
// `needsReview: true` e DEVE ser revisado item a item antes de a receita de renomeação rodar. Os identificadores que
// o glossário não conhece (inglês, nomes de terceiros) nunca entram no mapa.
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { loadGlossary, lookup, stripAccents } from './lib/words.mjs';

// ---------------------------------------------------------------------------------------------------------
// Tabelas de classificação (o glossário diz O QUE traduzir; estas dizem COMO reordenar)
// ---------------------------------------------------------------------------------------------------------

/** Sufixos padronizados da constituição (e os da emenda 2.1.0): ficam no fim do nome. */
const SUFFIXES = new Set([
  'porta', 'portas', 'servico', 'servicos', 'mapeador', 'repositorio', 'adaptador', 'controlador', 'teste', 'testes',
  'entidade', 'comando', 'filtro', 'conversor', 'codificador', 'requisicao', 'resposta', 'casodeuso', 'decorador',
]);
/** Marcadores de tipo que também ficam no fim ("relatorio_foco_tipo" → "…_type"). */
const TRAILING_KINDS = new Set(['tipo']);
/** Adjetivos que, em português, já vêm antes do substantivo: não entram na inversão. */
const PREPOSED = new Set([
  'ultimo', 'ultima', 'ultimos', 'ultimas', 'proximo', 'proxima', 'proximos', 'proximas', 'novo', 'nova', 'novos',
  'novas', 'primeiro', 'primeira', 'mesmo', 'mesma', 'outro', 'outra', 'antigo', 'antiga', 'nenhum', 'nenhuma',
  'todos', 'todas', 'muito', 'muita',
]);
/** Verbos no infinitivo/imperativo que não terminam em ar/er/ir ou que o sufixo confundiria com substantivo. */
const NOT_VERBS = new Set(['familiar', 'regular', 'complementar', 'secundaria', 'par', 'mar', 'ver', 'ter', 'ser', 'vir']);
/** Substantivo que, antes de `em` (… em → … at), vira particípio ("ultimo_uso_em" → "last_used_at"). */
const NOUN_AS_PARTICIPLE = { uso: 'used' };
/** Conectivos de posse: somem e invertem a ordem ("codigo_de_acesso" → "access_code"). */
const GENITIVE = new Set(['de', 'da', 'do', 'dos', 'das']);
const PREPOSITIONS = { em: 'in', para: 'for', sem: 'without' };

/**
 * Palavras polissêmicas: a primeira regra cujo vizinho (outra palavra do mesmo identificador) casa vence; se
 * nenhuma casar, vale o glossário e o item sai com `needsReview`. `context` força o valor a partir do chamador.
 */
const CONTEXTUAL = {
  inicio: [
    { en: 'start', when: ['periodo', 'intervalo', 'fluxo', 'data'], context: 'period' },
    { en: 'home', when: ['tela', 'comando', 'botao', 'pacote', 'casodeuso'], context: 'screen' },
  ],
  registro: [
    { en: 'registration', when: ['aula', 'fluxo', 'relatorio'], context: 'lesson' },
    { en: 'record', when: ['custo', 'retencao', 'updates', 'log', 'curso', 'linha'], context: 'log' },
  ],
  gravar: [
    { en: 'save', when: ['acoes', 'lote', 'banco', 'canal', 'dados'], context: 'persist' },
    { en: 'record', when: ['audio', 'voz', 'gravador'], context: 'audio' },
  ],
};

// ---------------------------------------------------------------------------------------------------------
// Estilo de escrita
// ---------------------------------------------------------------------------------------------------------

/** Detecta o estilo e separa em palavras (preserva as originais para o inglês desconhecido). */
export function parseIdentifier(identifier) {
  if (/[_]/.test(identifier)) {
    const style = identifier === identifier.toUpperCase() ? 'SCREAMING' : 'snake';
    return { style, parts: identifier.split('_').filter(Boolean) };
  }
  if (/-/.test(identifier)) return { style: 'kebab', parts: identifier.split('-').filter(Boolean) };
  const spaced = identifier
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');
  const parts = spaced.split(' ').filter(Boolean);
  if (identifier === identifier.toUpperCase() && /[A-Z]/.test(identifier)) return { style: 'SCREAMING', parts: [identifier] };
  if (/^[a-z0-9]+$/.test(identifier)) return { style: 'lower', parts };
  return { style: /^[A-Z]/.test(identifier) ? 'Pascal' : 'camel', parts };
}

const capitalize = (word) => word.charAt(0).toUpperCase() + word.slice(1);

/** Junta palavras (minúsculas) no estilo pedido: camel, Pascal, snake, SCREAMING, kebab, lower. */
export function joinWords(words, style) {
  const list = words.flatMap((word) => word.split('-')).filter(Boolean);
  switch (style) {
    case 'camel': return list.map((word, i) => (i === 0 ? word : capitalize(word))).join('');
    case 'Pascal': return list.map(capitalize).join('');
    case 'snake': return list.join('_');
    case 'SCREAMING': return list.join('_').toUpperCase();
    case 'kebab': return list.join('-');
    default: return list.join('');
  }
}

/** Converte um valor (com hífen, ponto ou underscore) para o estilo de identificador pedido: demo-data → DemoData. */
export function convertCase(text, style) {
  return joinWords(parseIdentifier(text).parts.map((part) => part.toLowerCase()), style);
}

// ---------------------------------------------------------------------------------------------------------
// Tradução de um identificador
// ---------------------------------------------------------------------------------------------------------

function pluralize(word) {
  if (/(s|x|ch|sh)$/.test(word)) return `${word}es`;
  if (/[^aeiou]y$/.test(word)) return `${word.slice(0, -1)}ies`;
  return `${word}s`;
}

/** Traduz uma palavra portuguesa; devolve `{ en, plural }` ou null. Respeita o plural quando só o singular existe. */
function translateWord(glossary, word) {
  const hit = lookup(glossary, word);
  if (!hit) return null;
  const exact = hit.term === word;
  const en = exact ? hit.english : pluralize(hit.english);
  return { en, term: hit.term };
}

/** Escolhe o valor de uma palavra polissêmica: pelo contexto do chamador, depois pelos vizinhos. */
function resolveContextual(word, neighbours, options) {
  const rules = CONTEXTUAL[word];
  if (!rules) return null;
  if (options.context) {
    const forced = rules.find((rule) => rule.context === options.context);
    if (forced) return { en: forced.en, resolved: true };
  }
  const found = rules.find((rule) => rule.when.some((other) => neighbours.includes(other)));
  return found ? { en: found.en, resolved: true } : { en: null, resolved: false };
}

/**
 * Traduz um identificador (ou parte dele). Devolve `{ novo, needsReview, motivo }`, ou `null` quando não há
 * nenhum termo do glossário (nada a renomear). `options.context`: 'screen' | 'period' | 'lesson' | 'log' …
 */
export function translateIdentifier(identifier, glossary, options = {}) {
  if (/[./:]/.test(identifier)) {
    // composto (caminho, pacote, rota): traduz cada segmento e preserva os separadores
    const pieces = identifier.split(/([./:])/);
    let changed = false;
    let review = false;
    const reasons = [];
    const mapped = pieces.map((piece) => {
      if (/^[./:]$/.test(piece) || !piece) return piece;
      const result = translateIdentifier(piece, glossary, options);
      if (!result) return piece;
      changed = true;
      review ||= result.needsReview;
      if (result.motivo) reasons.push(result.motivo);
      return result.novo;
    });
    return changed ? { novo: mapped.join(''), needsReview: review, motivo: [...new Set(reasons)].join('; ') } : null;
  }

  const { style, parts } = parseIdentifier(identifier);
  // "CasoDeUso" (caso + de + uso) é um termo só: UseCase
  for (let i = 0; i + 2 < parts.length; i++) {
    if (/^casos?$/i.test(parts[i]) && /^de$/i.test(parts[i + 1]) && /^uso$/i.test(parts[i + 2])) parts.splice(i, 3, 'casodeuso');
  }
  const lower = parts.map((part) => stripAccents(part).toLowerCase());
  const isAmbiguous = (word) => (glossary.matching?.ambiguousWords ?? []).includes(word);
  const isPt = (word) =>
    lookup(glossary, word) !== null || Object.hasOwn(glossary.words, word) || Boolean(CONTEXTUAL[word]);

  // classifica; palavra ambígua só entra na corrida quando encostada em palavra portuguesa
  const kinds = lower.map((word) => (isPt(word) ? 'pt' : 'en'));
  lower.forEach((word, index) => {
    if (kinds[index] === 'en' && isAmbiguous(word) && (kinds[index - 1] === 'pt' || kinds[index + 1] === 'pt')) kinds[index] = 'amb';
  });
  if (!kinds.includes('pt')) return null;

  const reasons = new Set();
  const out = [];
  let index = 0;
  while (index < lower.length) {
    if (kinds[index] === 'en') {
      out.push(parts[index].toLowerCase());
      index++;
      continue;
    }
    let end = index;
    while (end < lower.length && kinds[end] !== 'en') end++;
    out.push(...translateRun(lower.slice(index, end), glossary, options, reasons));
    index = end;
  }
  const novo = joinWords(out, style);
  return { novo, needsReview: reasons.size > 0, motivo: [...reasons].join('; ') };
}

/** Uma corrida de palavras portuguesas contíguas (ex.: [relatorio, aula], [ultimo, uso, em]). */
function translateRun(words, glossary, options, reasons) {
  let list = [...words];
  const originalOrder = [...words];
  let atSuffix = false;
  let suffix = null;

  if (list.length > 1 && list[list.length - 1] === 'em') {
    atSuffix = true;
    list.pop();
  }
  if (list.length > 1 && (SUFFIXES.has(list[list.length - 1]) || TRAILING_KINDS.has(list[list.length - 1]))) {
    suffix = list.pop();
    reasons.add('sufixo');
  }
  const leading = [];
  while (list.length > 1 && PREPOSED.has(list[0])) leading.push(list.shift());
  let verb = null;
  const head = list[0];
  if (list.length > 1 && /(ar|er|ir)$/.test(head) && head.length > 3 && !NOT_VERBS.has(head) && lookup(glossary, head)) {
    verb = list.shift();
  }

  const neighbours = originalOrder;
  const english = (word) => {
    const contextual = resolveContextual(word, neighbours.filter((other) => other !== word), options);
    if (contextual) {
      if (contextual.resolved) {
        reasons.add('contextual');
        return contextual.en;
      }
      reasons.add(`contextual: ${word} (${CONTEXTUAL[word].map((rule) => rule.en).join('|')})`);
    }
    if (GENITIVE.has(word)) return null;
    if (PREPOSITIONS[word]) {
      reasons.add('conectivo');
      return PREPOSITIONS[word];
    }
    if (atSuffix && NOUN_AS_PARTICIPLE[word]) return NOUN_AS_PARTICIPLE[word];
    if (word === 'data') return list.length > 1 || originalOrder.length > 1 ? 'date' : 'data';
    const translated = translateWord(glossary, word);
    if (translated) return translated.en;
    return word; // ambígua idêntica nos dois idiomas (audio, local, total…)
  };

  const rest = list.filter((word) => !GENITIVE.has(word));
  const reversed = [...rest].reverse();
  if (rest.length > 1) reasons.add('reordenado');
  if (list.some((word) => GENITIVE.has(word))) reasons.add('conectivo');

  const result = [
    ...leading.map(english),
    ...(verb ? [english(verb)] : []),
    ...reversed.map(english),
    ...(suffix ? [english(suffix)] : []),
    ...(atSuffix ? ['at'] : []),
  ].filter(Boolean);
  return result.flatMap((word) => (word === 'usecase' ? ['use', 'case'] : word.split('-')));
}

// ---------------------------------------------------------------------------------------------------------
// Execução por fatia
// ---------------------------------------------------------------------------------------------------------

/** Raízes de varredura de cada fatia (plan.md, "Fatias"). Os imports em outros módulos entram pelo compilador. */
const SLICE_ROOTS = {
  2: ['backend/nucleo'],
  3: ['backend/aplicacao'],
  4: ['backend/aplicacao/src/main/resources/mensagens', 'specs/001-fluxo-relatorios/contracts', 'specs/002-api-interna-canal-telegram/contracts'],
  5: ['frontend'],
  6: ['infra', '.github'],
  7: ['.specify', 'README.md', 'CLAUDE.md'],
};
const SKIP_DIRS = new Set(['.git', 'node_modules', 'target', 'dist', 'playwright-report', 'test-results']);
const CODE_EXTENSIONS = new Set(['.java', '.ts', '.tsx', '.mjs', '.js', '.css', '.sql', '.yaml', '.yml', '.json', '.properties', '.sh', '.xml', '.html']);

function* walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else if (entry.isFile()) yield full;
  }
}

/** Identificadores candidatos de um texto: palavras de código e segmentos de caminho, sem comentários. */
function identifiersOf(text, extension = '') {
  const withoutComments = text.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/(^|[^:])\/\/.*$/gm, '$1 ');
  // Em Java, texto para pessoas dentro de literais (espaço, acento, maiúscula isolada) não é identificador: só ficam
  // os literais de uma palavra (chaves, valores de código) e as constantes em caixa alta.
  const code =
    extension === '.java'
      ? withoutComments.replace(/"(?:[^"\\\n]|\\.)*"/g, (literal) =>
          /^"(?:[a-z][A-Za-z0-9_.-]*|[A-Z][A-Z0-9_]*)"$/.test(literal) ? literal : ' ')
      : withoutComments;
  const found = new Set();
  for (const match of code.matchAll(/[A-Za-z_$][A-Za-z0-9_$]*(?:-[A-Za-z0-9_$]+)*/g)) found.add(match[0]);
  return found;
}

function main() {
  const args = process.argv.slice(2);
  const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
  const slice = Number(option('--slice', '0'));
  const root = resolve(option('--root', process.cwd()));
  const glossary = loadGlossary(resolve(root, option('--glossary', '.specify/memory/glossary.json')));
  const roots = option('--paths') ? option('--paths').split(',') : SLICE_ROOTS[slice];
  if (!roots) {
    console.error('Use --slice <2..7> ou --paths <a,b>.');
    process.exit(2);
  }
  const identifiers = new Map();
  const add = (identifier, where) => {
    if (!identifiers.has(identifier)) identifiers.set(identifier, new Set());
    if (identifiers.get(identifier).size < 3) identifiers.get(identifier).add(where);
  };
  for (const rootPath of roots) {
    const absolute = resolve(root, rootPath);
    if (!existsSync(absolute)) continue;
    const files = statSync(absolute).isDirectory() ? [...walk(absolute)] : [absolute];
    for (const file of files) {
      const relativePath = relative(root, file).split(sep).join('/');
      for (const segment of relativePath.split('/')) for (const part of segment.split('.')) if (part) add(part, relativePath);
      if (CODE_EXTENSIONS.has(extname(file)) && statSync(file).size < 2_000_000) {
        for (const identifier of identifiersOf(readFileSync(file, 'utf8'), extname(file))) add(identifier, relativePath);
      }
    }
  }
  const map = [];
  for (const [identifier, where] of [...identifiers].sort(([a], [b]) => a.localeCompare(b))) {
    const result = translateIdentifier(identifier, glossary);
    if (!result || result.novo === identifier) continue;
    map.push({ antigo: identifier, novo: result.novo, needsReview: result.needsReview, motivo: result.motivo, exemplos: [...where] });
  }
  // Revisão humana (T032): `maps/slice-<N>.review.json` = { "antigo": "novo" | null }. O valor substitui a sugestão
  // e encerra o `needsReview`; `null` diz "revisado: não renomear". Chave fora da sugestão entra no mapa.
  // A revisão pode ser dividida em partes (`slice-<N>.review.*.json`), lidas em ordem de nome.
  const mapsDirectory = resolve(root, 'specs/003-migrate-code-to-english/tools/maps');
  const reviewFiles = option('--review')
    ? [resolve(root, option('--review'))]
    : existsSync(mapsDirectory)
      ? readdirSync(mapsDirectory).filter((name) => new RegExp(`^slice-${slice}\\.review(\\..+)?\\.json$`).test(name)).sort().map((name) => join(mapsDirectory, name))
      : [];
  const reviewed = {};
  for (const reviewFile of reviewFiles) {
    for (const [antigo, novo] of Object.entries(JSON.parse(readFileSync(reviewFile, 'utf8')))) {
      if (Object.hasOwn(reviewed, antigo) && reviewed[antigo] !== novo) {
        console.error(`Revisão conflitante para "${antigo}" em ${relative(root, reviewFile)}`);
        process.exit(1);
      }
      reviewed[antigo] = novo;
    }
  }
  const known = new Set(map.map((item) => item.antigo));
  for (const [antigo, novo] of Object.entries(reviewed)) {
    if (novo === '=' && !known.has(antigo)) {
      console.error(`"=" em "${antigo}", que não está na sugestão do gerador`);
      process.exit(1);
    }
    if (!known.has(antigo)) map.push({ antigo, novo: novo ?? antigo, needsReview: false, motivo: 'revisado', exemplos: [] });
  }
  for (const item of map) {
    if (!Object.hasOwn(reviewed, item.antigo)) continue;
    item.sugestao = item.novo;
    // "=" aceita a sugestão como está (revisada); null deixa o nome como está
    item.novo = reviewed[item.antigo] === '=' ? item.novo : (reviewed[item.antigo] ?? item.antigo);
    item.needsReview = false;
    item.revisado = true;
  }
  const finalMap = map.filter((item) => item.novo !== item.antigo || item.needsReview);
  const outPath = resolve(root, option('--out', `specs/003-migrate-code-to-english/tools/maps/slice-${slice}.json`));
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, JSON.stringify(finalMap, null, 2) + '\n');
  const review = finalMap.filter((item) => item.needsReview).length;
  console.log(`${finalMap.length} identificadores no mapa; ${review} precisam de revisão (needsReview) → ${relative(root, outPath)}`);
  if (args.includes('--strict') && review > 0) process.exit(1);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
