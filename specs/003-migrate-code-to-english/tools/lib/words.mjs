// Funções de palavras compartilhadas pelas ferramentas da feature 003 (sem dependências).
// Mesma regra de casamento dos três guardiões (research.md R-05): divide por camelCase/PascalCase/snake_case/
// kebab-case/ponto/barra/`: = ? & ;`, compara a palavra sem acento, e também sem `s` e sem `es` finais.
import { readFileSync } from 'node:fs';

export function stripAccents(text) {
  return text.normalize('NFD').replace(/\p{M}/gu, '');
}

/** Divide um identificador (ou caminho, rota, ação de botão) em palavras minúsculas e sem acento. */
export function splitWords(text) {
  const spaced = stripAccents(text)
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');
  return (spaced.match(/[A-Za-z]+/g) ?? []).map((word) => word.toLowerCase());
}

/** Candidatas de casamento de uma palavra: ela mesma, sem `s` e sem `es` finais. */
export function candidates(word) {
  const list = [word];
  if (word.endsWith('s') && word.length > 2) list.push(word.slice(0, -1));
  if (word.endsWith('es') && word.length > 3) list.push(word.slice(0, -2));
  return list;
}

export function loadGlossary(path) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

/** Palavras cujo valor entre parênteses indica "conectivo": não geram termo próprio no guardião. */
export function isConnector(english) {
  return english.startsWith('(');
}

/**
 * Procura a palavra no glossário. Devolve `{ term, english }` ou `null`.
 * Conectivos (de, da, em, sem, para…) não casam: só o mapa de identificadores os trata.
 */
export function lookup(glossary, word) {
  for (const candidate of candidates(word)) {
    if (!Object.hasOwn(glossary.words, candidate)) continue; // evita `constructor`, `toString`…
    const english = glossary.words[candidate];
    if (!isConnector(english)) return { term: candidate, english };
  }
  return null;
}

/** Todas as ocorrências do glossário em um texto, na ordem em que aparecem. */
export function findTerms(glossary, text) {
  const found = [];
  for (const word of splitWords(text)) {
    const hit = lookup(glossary, word);
    if (hit) found.push({ word, ...hit });
  }
  return found;
}

/**
 * Um literal é "texto para pessoas" (FR-023) quando tem espaço, letra acentuada ou é uma única palavra
 * iniciada em maiúscula ("Carregando"). Os demais são tratados como código (chaves, rotas, valores).
 */
export function isTextForPeople(literal) {
  return /\s/.test(literal) || /[^\x00-\x7F]/.test(literal) || /^[A-Z][a-z]+[.!?:,;]*$/.test(literal);
}
