// Tabelas de nomes da fatia 5 (frontend), compartilhadas por `ts-rename.mjs` (que renomeia) e por `rename-check-frontend5.mjs`
// (que confere a renomeação): mapa de identificadores/classes/variáveis, literais de código e o novo caminho de cada arquivo.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

export function loadNames(tools) {
  const read = (path) => JSON.parse(readFileSync(join(tools, path), 'utf8'));
  const config = read('rewrite/slice-5-config.json');
  const map = read('maps/slice-5.json');

  const idMap = new Map(); // identificador → novo
  const classMap = new Map(); // classe sem ponto → nova
  const varMap = new Map(); // variável sem -- → nova
  for (const item of map) {
    if (item.tipo === 'classe-css') classMap.set(item.antigo.slice(1), item.novo.slice(1));
    else if (item.tipo === 'variavel-css') varMap.set(item.antigo.slice(2), item.novo.slice(2));
    else idMap.set(item.antigo, item.novo);
  }
  const kebab = (name) => name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
  const literalMap = new Map(); // valor de código em minúsculas/kebab → novo
  for (const [from, to] of idMap) {
    if (!/^[a-z][a-z0-9-]*$/.test(from)) continue;
    literalMap.set(from, /^[a-z0-9-]+$/.test(to) ? to : kebab(to));
  }
  for (const [from, to] of Object.entries(config.literalOverrides)) literalMap.set(from, to);
  for (const value of config.literalDeny) literalMap.delete(value);

  const folderNames = new Map(Object.entries(config.folderNames));
  const stemOverrides = config.stemOverrides;

  /** Novo caminho (relativo à raiz do repositório) de um arquivo de `frontend/{src,tests,scripts,public}`. */
  function newPathOf(file) {
    const parts = file.split('/');
    if (!['src', 'tests', 'scripts', 'public'].includes(parts[1])) return file;
    const out = parts.map((part, index) => {
      if (index < 2) return part;
      if (index < parts.length - 1) return folderNames.get(part) ?? part;
      const dot = part.indexOf('.');
      const stem = dot < 0 ? part : part.slice(0, dot);
      const extension = dot < 0 ? '' : part.slice(dot);
      if (/^language-guard/.test(stem) || /^(LICENSE|OFL)-/.test(stem) || /-wght/.test(stem) || stem === 'material-symbols-outlined') return part;
      const mapped = stemOverrides[stem] ?? idMap.get(stem);
      return mapped ? mapped + extension : part;
    });
    return out.join('/');
  }

  return { config, map, idMap, classMap, varMap, literalMap, kebab, newPathOf };
}
