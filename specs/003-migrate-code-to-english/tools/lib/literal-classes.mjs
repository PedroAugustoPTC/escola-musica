// Classes de literal Java que são CÓDIGO e não texto (T065, T066, T076): o que o editor de literais troca e o que o
// `rename-check` aceita trocado. Tudo o mais (texto para pessoas, chave de JSON do fio, valor de enumeração) continua
// idêntico byte a byte.
//
//   const classes = classifyLiterals(tokens);            // Map(índice do token → classe)
//   transformLiteral('sql', 'select nome from aluno', map)   → 'select name from student'
//
// Classes:
//   jpa-name   argumento de `@Table`, `@Column`, `@JoinColumn`, `@Param`… (nome de tabela, coluna ou parâmetro)
//   sql        consulta nativa (`@Query(nativeQuery = true)`) ou instrução SQL em literal/bloco de texto
//   jpql       consulta JPQL de `@Query`
//   config     chave ou perfil de configuração em anotação (`@Profile`, `@ConditionalOnProperty`, `@Value`…)
//   prop-assign  atribuição de propriedade em teste: `chave.pontilhada=valor` (só a chave, e valor de enumeração de configuração)
//   jdbc-label  rótulo de coluna lido do `ResultSet` (`rs.getString("aluno_id")`)
//   cli         comando, opção e texto de uso do CLI do backend (`carga`, `--arquivo`…), só nos pacotes de carga
//   template-key  nome de variável do modelo de PDF (`variaveis.put("aluno", …)`) e de imagem, só no pacote de PDF
//   env-name    nome de variável de ambiente (`System.getenv("BANCO_URL")`), da lista fechada `envNames`
//   env-text    texto para pessoas que cita uma variável de ambiente da lista fechada: só o nome da variável muda
// A troca de cada classe usa SÓ o mapa de identificadores (palavra a palavra, fora de literais entre aspas simples).
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isSingleWord } from './java-lexer.mjs';

const JPA_ANNOTATIONS = new Set(['@Entity', '@Table', '@Column', '@JoinColumn', '@JoinTable', '@SequenceGenerator', '@Index', '@Param', '@UniqueConstraint', '@CollectionTable']);
const CONFIG_ANNOTATIONS = new Set(['@Profile', '@ActiveProfiles', '@ConditionalOnProperty', '@Value', '@ConfigurationProperties', '@TestPropertySource']);
const SQL_START = /^\s*(select|insert|update|delete|with|truncate|alter|create|drop)\b/i;
const JDBC_GETTERS = /^get(Object|String|Int|Long|Boolean|Timestamp|Bytes|BigDecimal|Date|Double|Short|Float|OffsetDateTime|LocalDate|UUID)$/;
const CLI_EXACT = new Set(['carga', 'massa', 'emitir-codigo', '--arquivo', '--simular']);
const TEMPLATE_RECEIVERS = new Set(['variaveis', 'variables', 'contexto', 'context']);
/** Chave de variável do modelo Thymeleaf: `variaveis.put("k", …)`, `contexto.setVariable("k", …)` ou o nome de imagem em `Map.of("k", imagem(…))`. */
function isTemplateKey(tokens, i) {
  const call = tokens[i - 2];
  if (tokens[i - 1]?.value === '(' && ['put', 'setVariable'].includes(call?.value) && tokens[i - 3]?.value === '.' && TEMPLATE_RECEIVERS.has(tokens[i - 4]?.value)) return true;
  return tokens[i + 1]?.value === ',' && ['imagem', 'image'].includes(tokens[i + 2]?.value) && tokens[i + 3]?.value === '(';
}
const PROPERTY_ASSIGN = /^[a-z][\w-]*(\.[a-z][\w-]*)+=/;

/** Índice do `)` que fecha o `(` em `open`. */
function closingParen(tokens, open) {
  let depth = 0;
  for (let i = open; i < tokens.length; i++) {
    if (tokens[i].value === '(' && tokens[i].type === 'op') depth++;
    else if (tokens[i].value === ')' && tokens[i].type === 'op' && --depth === 0) return i;
  }
  return tokens.length - 1;
}

/** Nomes de tabela do esquema V6 (único lugar onde ainda valem em português). */
const LEGACY_TABLES = new Set(JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'db-catalog-v6.json'), 'utf8')).tables.map((table) => table.name));
/** Auxiliares de teste que recebem o nome de uma tabela (`count("aluno")`, `statusOf("relatorio_aula")`). */
const TABLE_HELPERS = new Set(['count', 'statusOf']);
const JDBC_SQL_CALLS = new Set(['update', 'query', 'queryForObject', 'queryForList', 'queryForMap', 'queryForRowSet', 'execute', 'batchUpdate', 'prepareStatement', 'prepareCall']);

/** O literal é o primeiro argumento de uma chamada JDBC (`jdbc.update("…")`, `connection.prepareStatement("…")`). */
function isJdbcSqlArgument(tokens, i) {
  return tokens[i - 1]?.value === '(' && tokens[i - 2]?.type === 'ident' && JDBC_SQL_CALLS.has(tokens[i - 2].value) && tokens[i - 3]?.value === '.';
}

/** Índices dos literais de uma expressão `"a" + b + "c"` cujo primeiro literal começa por SQL: todos são SQL. */
function sqlChainMembers(tokens) {
  const members = new Set();
  const opens = new Set(['(', '{', '[']);
  const closes = new Set([')', '}', ']']);
  const isString = (t) => t.type === 'string' || t.type === 'textblock';
  for (let i = 0; i < tokens.length; i++) {
    if (!isString(tokens[i]) || !SQL_START.test(tokens[i].value)) continue;
    // só o início da expressão: o literal não pode ser precedido por `+`
    if (tokens[i - 1]?.value === '+') continue;
    let depth = 0;
    for (let k = i + 1; k < tokens.length; k++) {
      const t = tokens[k];
      if (t.type === 'op') {
        if (opens.has(t.value)) depth++;
        else if (closes.has(t.value)) {
          if (depth === 0) break;
          depth--;
        } else if (depth === 0 && (t.value === ',' || t.value === ';')) break;
      }
      if (depth === 0 && isString(t)) members.add(k);
    }
  }
  return members;
}

export function classifyLiterals(tokens, { envNames = new Set(), path = '' } = {}) {
  const inSeed = /[\/](seed|carga)[\/]/.test(path);
  const inPdf = /[\/]pdf[\/]/.test(path);
  const classes = new Map();
  const inAnnotation = new Set();
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token.type !== 'annotation') continue;
    if (tokens[i + 1]?.type !== 'op' || tokens[i + 1].value !== '(') continue;
    const close = closingParen(tokens, i + 1);
    const name = token.value;
    const isNative = name === '@Query' && tokens.slice(i + 2, close).some((t, k, all) => t.value === 'nativeQuery' && all[k + 2]?.value === 'true');
    for (let j = i + 2; j < close; j++) {
      const candidate = tokens[j];
      if (candidate.type !== 'string' && candidate.type !== 'textblock') continue;
      inAnnotation.add(j);
      // só o valor do atributo `name`/`value`/`prefix`/`properties` (não `havingValue`, `nativeQuery`, `columnDefinition`…)
      const attribute = tokens[j - 1]?.value === '=' ? tokens[j - 2]?.value : tokens[j - 1]?.value === '{' || tokens[j - 1]?.value === ',' ? findAttribute(tokens, j, i + 2) : 'value';
      if (name !== '@Query' && /\${[a-z]/.test(candidate.value)) {
        classes.set(j, 'config-value');
        continue;
      }
      if (!['value', 'name', 'prefix', 'properties', 'columnNames'].includes(attribute)) continue;
      if (attribute === 'properties' && PROPERTY_ASSIGN.test(candidate.value)) classes.set(j, 'prop-assign');
      else if (name === '@Query') classes.set(j, isNative ? 'sql' : 'jpql');
      else if (JPA_ANNOTATIONS.has(name)) classes.set(j, 'jpa-name');
      else if (CONFIG_ANNOTATIONS.has(name)) classes.set(j, name === '@Value' ? 'config-value' : 'config');
    }
  }
  // fragmentos de uma concatenação que começa por SQL, e nome de tabela antigo passado como argumento (helper de teste)
  const chainSql = sqlChainMembers(tokens);
  const isTest = /[\/]src[\/]test[\/]/.test(path.replace(/\\/g, '/'));
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if ((token.type !== 'string' && token.type !== 'textblock') || classes.has(i) || inAnnotation.has(i)) continue;
    const value = token.value;
    const previous = tokens[i - 1];
    const call = tokens[i - 2];
    if (token.type === 'string' && previous?.value === '(' && call?.type === 'ident' && JDBC_GETTERS.test(call.value) && tokens[i - 3]?.value === '.' && isSingleWord(value)) classes.set(i, 'jdbc-label');
    else if (isTest && token.type === 'string' && /^[a-z]+(_[a-z]+)+$/.test(value) && previous?.value === '(' && ['get', 'containsKey', 'getOrDefault'].includes(call?.value) && tokens[i - 3]?.value === '.') classes.set(i, 'jdbc-label');
    else if (inSeed && token.type === 'string' && (CLI_EXACT.has(value) || value.startsWith('--arquivo=') || /^(Uso|Comandos): /.test(value))) classes.set(i, 'cli');
    else if (inPdf && token.type === 'string' && isSingleWord(value) && isTemplateKey(tokens, i)) classes.set(i, 'template-key');
    else if (SQL_START.test(value) || chainSql.has(i) || isJdbcSqlArgument(tokens, i)) classes.set(i, 'sql');
    else if (isTest && token.type === 'string' && LEGACY_TABLES.has(value) && previous?.value === '(' && call?.type === 'ident' && TABLE_HELPERS.has(call.value) && tokens[i + 1]?.value === ')') classes.set(i, 'sql');
    else if (envNames.has(value)) classes.set(i, 'env-name');
    else if ((value.match(/\b[A-Z][A-Z0-9_]+\b/g) ?? []).some((word) => envNames.has(word))) classes.set(i, 'env-text');
    else if (PROPERTY_ASSIGN.test(value)) classes.set(i, 'prop-assign');
    else if (/^relatorios(\.[a-z][\w-]*)+$/.test(value)) classes.set(i, 'config');
  }
  return classes;
}

/** Atributo (`name`, `value`…) a que pertence o literal de um inicializador de lista, procurando para trás. */
function findAttribute(tokens, index, from) {
  for (let i = index - 1; i >= from; i--) {
    if (tokens[i].value === '=' && tokens[i - 1]?.type === 'ident') return tokens[i - 1].value;
  }
  return 'value';
}

const WORD = /[A-Za-z_][A-Za-z0-9_]*/g;
/** Mapa de uma chave de configuração: cada trecho entre pontos pelo mapa inteiro, ou palavra a palavra pelos hífens. */
export function mapConfigKey(key, map) {
  return key
    .split('.')
    .map((part) => map.ident.get(part) ?? part.split('-').map((piece) => map.mapIdent(piece)).join('-'))
    .join('.');
}

/** Chave de documento JSON gravado dentro de um literal SQL entre aspas: `'periodoChave'` e o caminho `'{observacoes}'`. */
function mapQuoted(part, map) {
  const names = new Set(map.explicit.jsonKeys ?? []);
  const inner = part.slice(1, -1);
  const key = (name) => (names.has(name) ? (map.ident.get(name) ?? name) : name);
  if (names.has(inner)) return `'${key(inner)}'`;
  const path = inner.match(/^\{([A-Za-z]+(?:,[A-Za-z]+)*)\}$/);
  return path ? `'{${path[1].split(',').map(key).join(',')}}'` : part;
}

/** Troca palavra a palavra, fora dos literais entre aspas simples (dados), salvo chave de JSON gravado. */
function mapCode(text, map) {
  return text
    .split(/('(?:[^']|'')*')/)
    .map((part, index) => (index % 2 === 1 ? mapQuoted(part, map) : part.replace(WORD, (word) => map.mapIdent(word))))
    .join('');
}

/** Conteúdo novo de um literal de classe `kind`. `literal` é o texto SEM as aspas. */
export function transformLiteral(kind, literal, map) {
  switch (kind) {
    case 'sql':
    case 'jpql':
      return mapCode(literal, map);
    case 'jpa-name':
    case 'jdbc-label':
      return map.mapIdent(literal);
    case 'config':
      return /^!?[a-z][\w.-]*$/.test(literal) ? (literal.startsWith('!') ? '!' : '') + mapConfigKey(literal.replace(/^!/, ''), map) : map.mapIdent(literal);
    case 'config-value':
      // `${chave:padrão}`: troca a chave (e o nome da variável de ambiente); o padrão fica
      return literal.replace(/\$\{([^}:]+)(:[^}]*)?\}/g, (_, key, fallback) => `\${${/^[A-Z][A-Z0-9_]*$/.test(key) ? map.mapIdent(key) : mapConfigKey(key, map)}${fallback ?? ''}}`);
    case 'template-key':
      return map.mapIdent(literal);
    case 'cli': {
      const terms = map.explicit.cliTerms ?? {};
      const escaped = Object.keys(terms)
        .sort((a, b) => b.length - a.length)
        .map((term) => term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
      const pattern = new RegExp(`(?<![\\w-])(${escaped.join('|')})(?![\\w-])`, 'g');
      return literal.replace(pattern, (term) => terms[term]);
    }
    case 'env-name':
      return map.ident.get(literal) ?? literal;
    case 'env-text': {
      const names = new Set(map.explicit.envNames ?? []);
      return literal.replace(/\b[A-Z][A-Z0-9_]+\b/g, (word) => (names.has(word) ? (map.ident.get(word) ?? word) : word));
    }
    case 'prop-assign': {
      const at = literal.indexOf('=');
      const value = literal.slice(at + 1);
      const mapped = map.explicit.configValues?.[value] ?? value;
      return `${mapConfigKey(literal.slice(0, at), map)}=${mapped}`;
    }
    default:
      return literal;
  }
}

/** Conteúdo bruto do literal (o lexer já tira as aspas de `"…"` e de `"""…"""`; os escapes ficam como no fonte). */
export const literalBody = (token) => token.value;
