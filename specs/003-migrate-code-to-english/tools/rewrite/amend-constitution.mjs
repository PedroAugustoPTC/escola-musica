#!/usr/bin/env node
// Fatia 7 (T222): emenda 2.1.0 da constituição. Texto das versões anteriores preservado; o relatório da 2.0.0 vira
// "Histórico — v2.0.0". Falha se algum trecho esperado não for achado (nada é aplicado pela metade).
//
//   node amend-constitution.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const root = resolve(process.cwd());
const path = join(root, '.specify/memory/constitution.md');
let s = readFileSync(path, 'utf8');
const replaceOnce = (from, to) => {
  const count = s.split(from).length - 1;
  if (count !== 1) throw new Error(`esperado 1 ocorrência, achei ${count}: ${from.slice(0, 70)}`);
  s = s.replace(from, to);
};

// 1) Sync Impact Report novo no topo; o da 2.0.0 passa a histórico
const report = `<!--
Sync Impact Report
- Version change: 2.0.0 → 2.1.0
- Rationale: MINOR — quitação da dívida do Princípio VI pela feature 003 (migração do código existente para
  inglês) e expansão material do princípio: (a) a **regra de transição** deixa de valer, porque o código, o banco,
  os contratos, os scripts, o CI e o frontend já estão em inglês, e o bloqueio "nenhuma feature nova pode ser
  iniciada" cai; (b) o glossário do domínio ganha um complemento oficial em forma de dados,
  \`.specify/memory/glossary.json\`, que alimenta o teste guardião de idioma (Java e TypeScript) e é parte da
  regra; (c) uma quinta categoria de exceção, "nomes próprios e material de referência", é ratificada; (d) a lista
  de sufixos padronizados ganha \`*Entity\`, \`*Command\`, \`*Filter\`, \`*Converter\`, \`*Codec\` e \`*Config\`, que a
  migração encontrou em uso e que o glossário já aceita.
- Modified principles: VI. Nomenclatura Previsível, Linguagem Ubíqua e Idioma (sufixos; exceções; regra de
  transição reescrita como registro da quitação; guardião de idioma)
- Modified sections: nenhuma além do Princípio VI e do cabeçalho de versão
- Added sections: nenhuma (o "Guardião de idioma" é subparte do Princípio VI)
- Removed sections: nenhuma; a **regra de transição** deixa de ser norma e fica como registro histórico
- Princípios I a V e VII a IX: inalterados
- Histórico: os textos das versões anteriores foram preservados, inclusive o item da 2.0.0 que diz que a
  feature 003 "ainda NÃO existe spec", que era verdade na época; o número 003 ficou com a migração, e a feature
  que traz o n8n passou a ser a 004 (documentos da spec 002 corrigidos nessa PR).
- Templates verificados (\`.specify/templates/\`): \`plan-template.md\`, \`spec-template.md\`, \`tasks-template.md\`,
  \`checklist-template.md\` e \`constitution-template.md\` — nenhum cita idioma de identificadores, sufixos,
  Hostinger nem a regra de transição; nenhum requer alteração. \`.specify/memory/.constitution-template.json\` não
  existe neste repositório.
- Artefatos dependentes atualizados na mesma PR: todos os \`CLAUDE.md\` (raiz, \`.specify\`, \`backend\`,
  \`backend/core\`, \`backend/application\`, \`frontend\`, \`infra\`, \`.github\`, \`specs\`, \`assets\`,
  \`portal-do-professor-design-system\`), \`README.md\` da raiz e de \`infra/\`, \`specs/001-*\` e \`specs/002-*\` (nota de
  histórico no topo) e \`PLANO_EXECUCAO_SPECKIT.md\` (bloco de comandos da seção 3.2).
- Aprovação: a emenda exige a aprovação do dono do produto (Governança); ela é pedida na descrição do PR da
  feature 003 e o merge do PR a registra.
- Deferred items: nenhum

Histórico — v2.0.0 (2026-10-10)
`;
replaceOnce('<!--\nSync Impact Report\n- Version change: 1.4.0 → 2.0.0\n', report + '- Version change: 1.4.0 → 2.0.0\n');

// 2) sufixos novos
replaceOnce(
  '  - `*Dto`, `*Request`, `*Response`: objetos de transporte na borda.\n',
  '  - `*Dto`, `*Request`, `*Response`: objetos de transporte na borda.\n' +
    '  - `*Entity`: entidade de persistência (mapeamento JPA), quando distinta da entidade de domínio.\n' +
    '  - `*Command`: comando operacional ou de linha de comando (ex.: `SeedCommand`).\n' +
    '  - `*Filter`: filtro de requisição na borda (ex.: de sessão ou de segurança).\n' +
    '  - `*Converter`: conversão de tipo de e para o banco ou o transporte.\n' +
    '  - `*Codec`: codificação e decodificação de um formato persistido ou trafegado.\n' +
    '  - `*Config`: classe de configuração (beans e propriedades).\n',
);

// 3) exceções
replaceOnce(
  '- Exceções permitidas, e somente estas: palavras reservadas da linguagem, APIs de terceiros, nomes\n  exigidos por ferramentas ou frameworks e siglas consagradas (PDF, LLM, HTTP, DTO).\n',
  '- Exceções permitidas, e somente estas: palavras reservadas da linguagem, APIs de terceiros, nomes\n' +
    '  exigidos por ferramentas ou frameworks, siglas consagradas (PDF, LLM, HTTP, DTO) e **nomes próprios e\n' +
    '  material de referência**: o nome da organização no pacote Java (`br.com.felipeblaft`), o prefixo de marca\n' +
    '  dos arquivos de identidade visual (`fb-`), o domínio público do portal, as pastas de material de referência\n' +
    '  que não são código (`assets/`, `portal-do-professor-design-system/`, `.claude/`), os nomes das pastas de\n' +
    '  feature em `specs/` (ex.: `001-fluxo-relatorios`) e os valores já persistidos que não podem mudar sem\n' +
    '  invalidar dados (os prefixos de domínio do HMAC dos códigos de acesso). Texto mostrado ao usuário e\n' +
    '  palavras digitadas por ele (ex.: o nome sugerido ao baixar o PDF, a palavra de confirmação do\n' +
    '  `local.sh clean`) seguem em português, como já diz esta seção.\n' +
    '- Toda exceção DEVE estar registrada em `.specify/memory/glossary.json`, com o motivo e a categoria; exceção\n' +
    '  que não casa com nada no código é "morta" e reprova o guardião.\n',
);

// 4) regra de transição → registro da quitação + guardião
replaceOnce(
  '**Regra de transição**\n\n- O código existente ainda está em português e constitui **dívida registrada** em relação a este\n  princípio, a ser quitada pela **feature 003** (migração para inglês).\n- Enquanto a feature 003 não for mesclada, **nenhuma feature nova pode ser iniciada**; a própria\n  003 é a única exceção.\n',
  '**Guardião de idioma**\n\n' +
    '- Um teste guardião DEVE rodar no CI e reprovar o build quando uma palavra em português aparecer em\n' +
    '  identificador, nome de arquivo ou de pasta, tabela, coluna, chave de catálogo, variável de ambiente, YAML,\n' +
    '  script, workflow ou classe CSS. Ele existe em Java (código e artefatos de dados e configuração) e em\n' +
    '  TypeScript (frontend), consome `.specify/memory/glossary.json` e NÃO examina texto mostrado ao usuário,\n' +
    '  logs, comentários nem documentação.\n' +
    '- Termo novo DEVE entrar no `glossary.json`, com um único equivalente em inglês, **antes** de ser usado. Não existe\n' +
    '  lista de exclusão: o que não pode ser traduzido é uma exceção documentada (acima).\n\n' +
    '**Regra de transição (quitada)**\n\n' +
    '- Até a versão 2.0.0, o código existente estava em português e constituía dívida registrada em relação a este\n' +
    '  princípio, e nenhuma feature nova podia ser iniciada enquanto ela não fosse quitada.\n' +
    '- A dívida foi **quitada pela feature 003** (`specs/003-migrate-code-to-english/`), em sete fatias, sem mudança\n' +
    '  de comportamento. A partir da 2.1.0 não há mais regra de transição nem bloqueio de feature nova: todo código\n' +
    '  novo segue este princípio desde o primeiro commit.\n',
);

// 5) versão
replaceOnce('**Version**: 2.0.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-10-10', '**Version**: 2.1.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-10-15');

writeFileSync(path, s);
console.log('constituição 2.1.0 gravada');

// 6) a categoria das exceções do glossário deixa de ser "proposta" (agora ratificada)
const glossaryPath = join(root, '.specify/memory/glossary.json');
let g = readFileSync(glossaryPath, 'utf8');
const proposed = 'proposta para a emenda 2.1.0: nomes próprios e material de referência (fora das 4 categorias fechadas do Princípio VI)';
const ratified = 'nomes próprios e material de referência (Princípio VI, emenda 2.1.0)';
const hits = g.split(proposed).length - 1;
g = g.split(proposed).join(ratified);
JSON.parse(g);
writeFileSync(glossaryPath, g);
console.log(`glossário: ${hits} categorias ratificadas`);
