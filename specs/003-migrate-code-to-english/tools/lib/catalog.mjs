// Catálogo de mensagens (`.properties`) e conferência de que só as chaves e os marcadores mudaram (R-04, T054).
//
//   const catalog = parseCatalog(texto);            // { entries: Map(chave → valor), keys: [chaves em ordem] }
//   swapMarkers('Olá, {nome}', { nome: 'name' })    // 'Olá, {name}'  (só dentro de chaves `{}`)
//   compareCatalogs(antigo, novo, { keyMap, markerMap })
//
// Regra (SC-003): para cada chave antiga `k`, `novo[mapa[k]] == trocarMarcadores(antigo[k])` byte a byte; as chaves
// formam uma bijeção (mesmo total, sem sobra nem falta). Chave fora do mapa vale por ela mesma (nas fatias em que as
// chaves ainda não mudam, o mapa de chaves é vazio e a conferência vira "mesmo conteúdo").
const MARKER = /\{([A-Za-z][A-Za-z0-9]*)\}/g;

export function parseCatalog(text) {
  const entries = new Map();
  const keys = [];
  const duplicated = [];
  for (const raw of text.split(/\r?\n/)) {
    if (!raw.trim() || /^\s*[#!]/.test(raw)) continue;
    const at = raw.indexOf('=');
    if (at < 0) continue;
    const key = raw.slice(0, at).trim();
    const value = raw.slice(at + 1);
    if (entries.has(key)) duplicated.push(key);
    entries.set(key, value);
    keys.push(key);
  }
  return { entries, keys, duplicated };
}

/** Marcadores de um valor, em ordem. */
export function markersOf(value) {
  return [...value.matchAll(MARKER)].map((match) => match[1]);
}

/** Troca os marcadores `{nome}` pelo nome do mapa; o resto do texto fica byte a byte igual. */
export function swapMarkers(value, markerMap = {}) {
  return value.replace(MARKER, (whole, name) => `{${markerMap[name] ?? name}}`);
}

export function compareCatalogs(oldCatalog, newCatalog, { keyMap = {}, markerMap = {} } = {}) {
  const errors = [];
  for (const key of oldCatalog.duplicated ?? []) errors.push({ kind: 'chave-duplicada', key, detail: 'catálogo base' });
  for (const key of newCatalog.duplicated ?? []) errors.push({ kind: 'chave-duplicada', key, detail: 'catálogo novo' });
  const expectedKeys = new Map();
  for (const key of oldCatalog.keys) {
    const target = keyMap[key] ?? key;
    if (expectedKeys.has(target)) errors.push({ kind: 'chave-repetida-no-mapa', key, detail: `"${expectedKeys.get(target)}" e "${key}" viram "${target}"` });
    expectedKeys.set(target, key);
    if (!newCatalog.entries.has(target)) {
      errors.push({ kind: 'chave-faltando', key, detail: `esperada "${target}"` });
      continue;
    }
    const expectedValue = swapMarkers(oldCatalog.entries.get(key), markerMap);
    const actualValue = newCatalog.entries.get(target);
    if (expectedValue !== actualValue) errors.push({ kind: 'texto-alterado', key, detail: JSON.stringify({ esperado: expectedValue, encontrado: actualValue }) });
  }
  for (const key of newCatalog.keys) if (!expectedKeys.has(key)) errors.push({ kind: 'chave-sobrando', key, detail: 'sem chave de origem' });
  if (oldCatalog.keys.length !== newCatalog.keys.length) {
    errors.push({ kind: 'total-diferente', key: '', detail: `${oldCatalog.keys.length} chaves na base, ${newCatalog.keys.length} no novo` });
  }
  // marcador novo fora do mapa: aparece no valor novo sem ser o trocado do antigo
  for (const [key, target] of [...expectedKeys].map(([novo, antigo]) => [antigo, novo])) {
    const value = newCatalog.entries.get(target);
    if (value === undefined) continue;
    const expected = new Set(markersOf(swapMarkers(oldCatalog.entries.get(key), markerMap)));
    for (const marker of markersOf(value)) if (!expected.has(marker)) errors.push({ kind: 'marcador-fora-do-mapa', key, detail: `{${marker}}` });
  }
  return errors;
}

/** Catálogo gerado em JSON (`mensagens.json` do frontend): chave → valor. */
export function compareJsonMessages(oldObject, newObject, options = {}) {
  const toCatalog = (object) => ({ entries: new Map(Object.entries(object)), keys: Object.keys(object), duplicated: [] });
  return compareCatalogs(toCatalog(oldObject), toCatalog(newObject), options);
}
