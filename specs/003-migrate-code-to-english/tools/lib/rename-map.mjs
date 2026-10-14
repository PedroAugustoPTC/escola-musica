// Carrega o mapa completo de identificadores de uma fatia, as exceções explícitas e a lista de retenção (T029).
//
//   const map = loadRenameMap(root, 2);
//   map.mapIdent('RelatorioAula')      → 'LessonReport'   (ou o próprio nome, se não está no mapa)
//   map.expectedPackage(['br','com',…,'relatorios','casodeuso','inicio']) → segmentos novos do pacote (tabela por pacote inteiro)
//   map.isValidPair(antigo, novo)      → o par existe no mapa (a conferência palavra a palavra é só alerta)
//
// O mapa é global por nome: um identificador antigo tem um só novo nome. A conferência palavra a palavra com o
// glossário (`wordsWarning`) NÃO reprova: o português inverte a ordem substantivo-adjetivo.
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const TOOLS = 'specs/003-migrate-code-to-english/tools';

export function loadRenameMap(root, slice) {
  const mapFile = join(root, TOOLS, 'maps', `slice-${slice}.json`);
  const explicit = JSON.parse(readFileSync(join(root, TOOLS, 'explicit-pairs.json'), 'utf8'));
  const heldFile = join(root, TOOLS, 'held-back', `slice-${slice}.json`);
  const held = existsSync(heldFile) ? JSON.parse(readFileSync(heldFile, 'utf8')) : { B1: {}, B2: {}, B3: {}, permanent: [] };

  const packagesFile = join(root, TOOLS, 'rewrite', `slice-${slice}-packages.json`);
  const packages = new Map(Object.entries(existsSync(packagesFile) ? JSON.parse(readFileSync(packagesFile, 'utf8')) : {}));

  const ident = new Map();
  for (const item of JSON.parse(readFileSync(mapFile, 'utf8'))) if (item.novo !== item.antigo) ident.set(item.antigo, item.novo);
  for (const [antigo, novo] of Object.entries(explicit.identifiers ?? {})) ident.set(antigo, novo);

  /** Nomes de membros retidos (constantes B1 e componentes B2), para tolerar o nome antigo em `import static`. */
  const heldNames = new Set();
  for (const group of [held.B1, held.B2]) for (const entry of Object.values(group)) for (const name of entry.members) heldNames.add(name);

  return {
    ident,
    held,
    heldNames,
    explicit,
    mapIdent: (name) => ident.get(name) ?? name,
    packages,
    /** Segmentos esperados de um pacote antigo: o prefixo mais longo da tabela decide; o resto segue igual. */
    expectedPackage: (segments) => {
      for (let k = segments.length; k >= 1; k--) {
        const key = segments.slice(0, k).join('.');
        if (packages.has(key)) return [...packages.get(key).split('.'), ...segments.slice(k)];
      }
      return segments;
    },
    // vale o nome do mapa ou o nome específico do papel (método × variável), ver `explicit-pairs.json`
    isValidPair: (antigo, novo) =>
      ident.get(antigo) === novo ||
      explicit.methodIdentifiers?.[antigo] === novo ||
      explicit.variableIdentifiers?.[antigo] === novo ||
      explicit.cliTerms?.[antigo] === novo ||
      explicit.packageSegments?.[antigo] === novo ||
      Object.entries(explicit.qualifiedMethods ?? {}).some(([chave, valor]) => chave.endsWith(`#${antigo}`) && valor === novo) ||
      Object.entries(explicit.qualifiedFields ?? {}).some(([chave, valor]) => chave.endsWith(`#${antigo}`) && valor === novo),
  };
}

/** Palavras do identificador (camelCase, PascalCase, snake_case, kebab-case), em minúsculas. */
export function wordsOf(identifier) {
  return identifier
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .split(/[\s_\-.]+/)
    .filter(Boolean)
    .map((word) => word.toLowerCase());
}
