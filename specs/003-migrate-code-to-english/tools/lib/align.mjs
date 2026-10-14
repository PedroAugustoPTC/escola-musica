// Alinhamento token a token de dois fontes Java, antes e depois da renomeação (T028).
//
//   const result = alignJava(fonteBase, fonteTrabalho, { map, file });
//
// Exige o mesmo número de tokens (depois de tirar os `import`, que o Spotless reordena e que são comparados à parte) e
// classifica cada diferença:
//   identificador ↔ identificador   o par (antigo → novo) DEVE estar no mapa;
//   literal de texto / bloco        idêntico byte a byte (nome do PDF exibido inclusive);
//   literal de uma palavra          idêntico, ou par do mapa (informativo);
//   número, operador, anotação      idênticos (anotação própria pode seguir o mapa).
// Código embutido em bloco de texto (JSON, SQL) é verificado identificador a identificador pelo mapa; o que não
// confere vira erro de literal, nunca "texto".
// Edições manuais de literal (cadeias de pacote em regras ArchUnit, nomes de classe em strings) só passam se listadas
// em `explicit-pairs.json` → `literals` com motivo.
import { importText, isSingleWord, lex } from './java-lexer.mjs';
import { scanTypes } from './java-types.mjs';
import { classifyLiterals, literalBody, transformLiteral } from './literal-classes.mjs';

const isLower = (text) => /^[a-z_$]/.test(text);
const BASE_PACKAGE = ['br', '.', 'com', '.', 'felipeblaft', '.'];

/**
 * Cadeias de pacote do corpo: a declaração `package` e os nomes qualificados `br.com.felipeblaft.x.y.Tipo`.
 * Cada cadeia é a lista de índices dos tokens de segmento; o esperado vem da tabela por pacote inteiro.
 */
function packageChains(tokens) {
  const chains = [];
  let coveredUntil = -1;
  for (let i = 0; i < tokens.length; i++) {
    if (i <= coveredUntil) continue; // dentro da declaração `package`: já é uma cadeia inteira
    if (tokens[i].type === 'ident' && tokens[i].value === 'package' && i < 3) {
      const chain = [];
      let j = i + 1;
      for (; j < tokens.length && !(tokens[j].type === 'op' && tokens[j].value === ';'); j++) if (tokens[j].type === 'ident') chain.push(j);
      chains.push(chain);
      coveredUntil = j;
      continue;
    }
    if (BASE_PACKAGE.every((value, offset) => tokens[i + offset]?.value === value)) {
      const chain = [i, i + 2, i + 4];
      let j = i + BASE_PACKAGE.length;
      while (tokens[j]?.type === 'ident' && isLower(tokens[j].value) && tokens[j + 1]?.value === '.') {
        chain.push(j);
        j += 2;
      }
      chains.push(chain);
    }
  }
  return chains;
}

const NOT_A_TYPE = new Set(['return', 'new', 'throw', 'case', 'else', 'assert', 'yield', 'instanceof', 'import', 'package', 'extends', 'implements', 'throws', 'permits', 'default']);

/**
 * Papel de DECLARAÇÃO do identificador na posição i (null se for uso): 'type' (class/interface/enum/record),
 * 'method' (nome seguido de "(" e precedido de tipo) ou 'var' (campo, parâmetro, variável local ou componente de
 * record: precedido de tipo e seguido de "=", ";", ",", ")" ou ":"). Chamadas e referências não contam.
 */
function declarationRole(tokens, i) {
  const token = tokens[i];
  const previous = tokens[i - 1];
  const next = tokens[i + 1];
  if (previous?.type === 'ident' && ['class', 'interface', 'enum', 'record'].includes(previous.value)) return 'type';
  const afterType = previous && ((previous.type === 'ident' && !NOT_A_TYPE.has(previous.value)) || (previous.type === 'op' && (previous.value === '>' || previous.value === ']')));
  if (!afterType || token.type !== 'ident') return null;
  if (next?.type === 'op' && next.value === '(') return 'method';
  if (next?.type === 'op' && ['=', ';', ',', ')', ':'].includes(next.value)) return 'var';
  return null;
}

function parseImport(declaration) {
  const isStatic = declaration[0]?.value === 'static';
  const parts = declaration.slice(isStatic ? 1 : 0).filter((token) => token.type === 'ident' || token.value === '*').map((token) => token.value);
  return { isStatic, parts };
}

const importString = ({ isStatic, parts }) => `${isStatic ? 'static ' : ''}${parts.join('.')}`;

/** Textos aceitáveis para um import da base depois da renomeação (o último nome pode ficar se for membro retido). */
function expectedImports(declaration, map) {
  const { isStatic, parts } = parseImport(declaration);
  const firstType = parts.findIndex((part, index) => part === '*' || (index > 0 && !isLower(part)));
  const packageEnd = firstType < 0 ? parts.length : firstType;
  const mapped = [...map.expectedPackage(parts.slice(0, packageEnd)), ...parts.slice(packageEnd).map((part) => map.mapIdent(part))];
  const candidates = [importString({ isStatic, parts: mapped })];
  // tipo da aplicacao (ou de biblioteca) que tem o mesmo nome de um tipo do core: só o pacote muda nesta fatia
  candidates.push(importString({ isStatic, parts: [...mapped.slice(0, packageEnd), ...parts.slice(packageEnd)] }));
  const last = parts.at(-1);
  if (isStatic && map.heldNames.has(last)) candidates.push(importString({ isStatic, parts: [...mapped.slice(0, -1), last] }));
  return candidates;
}

function compareImports(baseImports, workImports, map) {
  const errors = [];
  const available = new Map();
  for (const declaration of workImports) {
    const text = importString(parseImport(declaration));
    available.set(text, (available.get(text) ?? 0) + 1);
  }
  for (const declaration of baseImports) {
    const candidates = expectedImports(declaration, map);
    const hit = candidates.find((candidate) => (available.get(candidate) ?? 0) > 0);
    if (hit) available.set(hit, available.get(hit) - 1);
    else errors.push({ kind: 'import', line: declaration[0]?.line ?? 0, from: importString(parseImport(declaration)), to: candidates[0] });
  }
  for (const [text, count] of available) if (count > 0) errors.push({ kind: 'import-extra', line: 0, from: '', to: text });
  return errors;
}

/** Substitui, em um bloco de texto, cada palavra de identificador pelo valor do mapa (código embutido: JSON, SQL). */
function mapEmbedded(text, map) {
  return text.replace(/[A-Za-z_$][A-Za-z0-9_$]*/g, (word) => map.mapIdent(word));
}

export function alignJava(baseSource, workSource, { map, file = '' }) {
  const base = lex(baseSource);
  const work = lex(workSource);
  const result = {
    errors: [],
    pairs: new Map(), // antigo → novo (identificadores)
    producers: new Map(), // novo → Set(antigo), para detectar colisões
    declared: new Map(), // novo → Map(antigo → Set(papel de declaração)), colisão que importa
    singleWordLiterals: [],
    codeLiterals: [], // literais que são código (consulta, nome de tabela/coluna, chave de configuração), pela classe
    manualLiterals: [],
    commentsChanged: 0,
    tokens: base.body.length,
  };
  result.errors.push(...compareImports(base.imports, work.imports, map));

  const allowed = (map.explicit.literals ?? []).filter((entry) => !entry.file || file.endsWith(entry.file));
  const tokenEdits = (map.explicit.tokenEdits ?? []).filter((entry) => file.endsWith(entry.file));
  const baseTokens = base.body;
  const workTokens = work.body;
  if (baseTokens.length !== workTokens.length) {
    result.errors.push({ kind: 'tokens', line: 0, from: String(baseTokens.length), to: String(workTokens.length) });
    return result;
  }

  const seen = new Map(); // ocorrências já vistas de cada identificador (para `tokenEdits.occurrence`)
  const packageTokens = new Set();
  for (const chain of packageChains(baseTokens)) {
    const expected = map.expectedPackage(chain.map((index) => baseTokens[index].value));
    chain.forEach((index, position) => {
      packageTokens.add(index);
      if (workTokens[index]?.type === 'ident' && workTokens[index].value === expected[position]) return;
      // o pacote novo pode ter mais ou menos segmentos que o antigo: o número de tokens do arquivo então difere
      result.errors.push({ kind: 'par', line: baseTokens[index].line, from: baseTokens[index].value, to: workTokens[index]?.value ?? '' });
    });
  }
  const heldStarts = heldPositions(baseTokens, map);
  const literalClasses = map.codeLiterals === false ? new Map() : classifyLiterals(baseTokens, { envNames: new Set(map.explicit.envNames ?? []), path: file });
  for (let i = 0; i < baseTokens.length; i++) {
    const a = baseTokens[i];
    const b = workTokens[i];
    if (a.type !== b.type) {
      const typeEdit = tokenEdits.find((entry) => entry.from === a.value && entry.to === b.value);
      if (typeEdit) result.manualLiterals.push({ line: a.line, from: a.value, to: b.value, reason: typeEdit.reason });
      else result.errors.push({ kind: 'tipo-de-token', line: a.line, from: a.value, to: b.value });
      continue;
    }
    if (a.type === 'ident') {
      const nth = (seen.get(a.value) ?? 0) + 1;
      seen.set(a.value, nth);
      if (!result.producers.has(b.value)) result.producers.set(b.value, new Set());
      result.producers.get(b.value).add(a.value);
      const role = declarationRole(baseTokens, i);
      if (role) {
        if (!result.declared.has(b.value)) result.declared.set(b.value, new Map());
        const byOld = result.declared.get(b.value);
        if (!byOld.has(a.value)) byOld.set(a.value, new Set());
        byOld.get(a.value).add(role);
      }
      if (packageTokens.has(i)) continue; // validado pela cadeia de pacote
      if (a.value === b.value) continue;
      const edit = tokenEdits.find((entry) => entry.from === a.value && entry.to === b.value && (!entry.occurrence || entry.occurrence === nth));
      if (edit) result.manualLiterals.push({ line: a.line, from: a.value, to: b.value, reason: edit.reason });
      else if (!map.isValidPair(a.value, b.value)) result.errors.push({ kind: 'par', line: a.line, from: a.value, to: b.value });
      else result.pairs.set(a.value, b.value);
    } else if (a.type === 'annotation') {
      if (a.value !== b.value && !(a.value.startsWith('@') && b.value === `@${map.mapIdent(a.value.slice(1))}`)) {
        result.errors.push({ kind: 'par', line: a.line, from: a.value, to: b.value });
      }
    } else if (a.type === 'string' || a.type === 'char' || a.type === 'textblock') {
      if (a.value === b.value) continue;
      const manual = allowed.find((entry) => entry.from === a.value && entry.to === b.value);
      if (manual) result.manualLiterals.push({ line: a.line, from: a.value, to: b.value, reason: manual.reason });
      else if (literalClasses.has(i) && transformLiteral(literalClasses.get(i), literalBody(a), map) === literalBody(b)) {
        result.codeLiterals.push({ line: a.line, kind: literalClasses.get(i), from: a.value, to: b.value });
      } else if (a.type === 'string' && isSingleWord(a.value) && isSingleWord(b.value) && map.isValidPair(a.value, b.value)) {
        result.singleWordLiterals.push({ line: a.line, from: a.value, to: b.value });
      } else if (a.type === 'textblock' && mapEmbedded(a.value, map) === b.value) {
        result.singleWordLiterals.push({ line: a.line, from: '(bloco de texto)', to: '(identificadores pelo mapa)' });
      } else result.errors.push({ kind: 'literal', line: a.line, from: a.value, to: b.value });
    } else if (a.value !== b.value) {
      result.errors.push({ kind: 'token', line: a.line, from: a.value, to: b.value });
    }
  }

  // retido deve permanecer igual: constantes de enumeração (B1) e componentes de `record` (B2)
  for (const { index, name, owner } of heldStarts) {
    if (workTokens[index]?.value !== name) {
      result.errors.push({ kind: 'retido-alterado', line: baseTokens[index]?.line ?? 0, from: `${owner}.${name}`, to: workTokens[index]?.value ?? '' });
    }
  }

  const baseComments = new Set(base.comments.map((comment) => comment.text));
  result.commentsChanged = work.comments.filter((comment) => !baseComments.has(comment.text)).length;
  return result;
}

/** Posições (no corpo, sem imports) das declarações retidas deste arquivo, segundo `held-back/slice-<N>.json`. */
function heldPositions(baseTokens, map) {
  const positions = [];
  const types = scanTypes(baseTokens);
  for (const type of types) {
    const b1 = map.held.B1?.[type.path];
    const b2 = map.held.B2?.[type.path];
    for (const constant of type.constants ?? []) {
      if (b1?.members.includes(constant.name)) positions.push({ index: constant.nameIndex, name: constant.name, owner: type.path });
    }
    for (const component of type.components ?? []) {
      if (b2?.members.includes(component.name)) positions.push({ index: component.nameIndex, name: component.name, owner: type.path });
    }
  }
  return positions;
}

/** Colisões: um nome novo produzido por dois nomes antigos diferentes no mesmo arquivo. */
export function collisionsOf(result, file) {
  const found = [];
  for (const [novo, byOld] of result.declared) {
    for (const role of ['type', 'method', 'var']) {
      const olds = [...byOld].filter(([, roles]) => roles.has(role)).map(([old]) => old).sort();
      if (olds.length > 1) found.push({ file, novo, role, antigos: olds });
    }
  }
  return found;
}

export { importText };
