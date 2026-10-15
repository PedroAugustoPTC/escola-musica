# Evidência — Fatia 5: frontend

Registro das tasks T149 a T168 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é registrado
aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T149)

Árvore: commit da fatia 4 (`7879ab4`). Entre o fim da rodada de comparação da fatia 4 e este ponto de partida só mudaram
arquivos de evidência e de `tasks.md`, então a linha de base da fatia 5 **é o resultado final da fatia 4**
(`evidence/slice-4.md`), sem rodar de novo suítes sobre uma árvore idêntica.

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — `core` (Surefire) | 324 | verde |
| `backend` — `application` unitários | 81 | verde |
| `backend` — Failsafe padrão | 274 (1 ignorado) | verde |
| `backend` — Failsafe `internal-transport` | 71 | verde |
| `backend` — total | 750 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

Cobertura do `core` (`evidence/slice-4-jacoco.csv`): linhas 3.178/3.238, branches 806/875, métodos 745/766.

## Decisões tomadas durante a fatia (registro corrente)

1. **Inventário fechado do frontend (T150)**: `tools/frontend-inventory.mjs` (ts-morph) levanta só nomes em posição de código: 1.373
   identificadores, 502 literais de uma palavra, 212 classes CSS (184 usadas em `className`), 25 variáveis, 128 rotas e os 99 arquivos.
   O gerador de mapa ganhou `--inventory` (`gen-identifier-map.mjs --slice 5 --inventory …`) e entende `.classe` e `--variável` nas
   revisões; texto para pessoas nunca entra no mapa.
2. **Mapa revisado à mão (T151)**: `tools/maps/slice-5.json` (654 identificadores, 210 classes, 4 variáveis) com as revisões
   `slice-5.review.01…04.json`. Onde a tradução palavra a palavra erra a ordem ou colide com palavra reservada ou global, o nome foi
   escolhido à mão: `novo→nextValue` (não `new`), `Objeto→JsonObject`, `Parametros→MessageParams` (não `Parameters`),
   `renderizar→renderWithProviders` (não `render`), `protegida→protectedRoute`, `falso→fakeFetch`, `Estrutura→Layout`. Telas:
   `Tela*→*Screen` (`TelaRelatorioEmAberto→OpenReportScreen`). Os nomes que também são chave do documento do semestral seguem
   exatamente o fio (`frequenciaEEstudo→attendanceAndStudy`, `estudosRealizados→completedStudies`…, a tabela da `V7`).
3. **Ferramenta de renomeação (T153)**: `tools/ts-rename.mjs` (ts-morph, no contêiner) em fases `symbols` (símbolos pelo compilador,
   com detecção de colisão), `names` (propriedades sem declaração), `literals` (classes, rotas da SPA, parâmetros de consulta,
   valores de união, ids, caminhos de ativos), `css` e `moves`. Tabelas em `tools/rewrite/slice-5-config.json`; nomes e caminhos
   novos em `tools/lib/slice5-names.mjs`. Resultado: ~1.260 símbolos, 37 nomes sem declaração, ~815 literais, ~415 classes em
   strings, 311 trocas em CSS e 92 arquivos movidos (imagens da marca, fontes, componentes, páginas, serviços, estilos e testes).
4. **Rotas da SPA (T157)**: `/entrar→/sign-in`, `/alunos…→/students…`, `/trabalhos/:trabalhoId→/jobs/:jobId`,
   `/relatorios-aula/:relatorioId…→/lesson-reports/:reportId…`, `/relatorios-semestrais…→/semester-reports…`, e os parâmetros de
   consulta `busca→search`, `foco→focus`, `registrar→register`, `destino→destination`, `motivo→reason`. Rotas antigas deixam de
   existir, sem redirecionamento (FR-024). `specs/001-fluxo-relatorios/contracts/rotas-portal.md` ficou com os nomes novos (T161).
5. **Ativos da marca (T158)**: `public/marca→public/brand`, `fb-filete-*→fb-divider-*`, `fb-monograma*→fb-monogram*`, cores
   `marfim/ouro/ouroclaro/preto→ivory/gold/lightgold/black`. Os 16 PNG e as fontes têm SHA-256 idêntico (o verificador compara todos
   os binários: 0 diferenças).
6. **Scripts (T159)**: `gerar-mensagens.mjs→generate-messages.mjs` (variáveis locais traduzidas à mão, arquivo registrado em
   `config.manualFiles`), scripts do `package.json` (`generate`, `generate:api`, `generate:messages`, `format`), saída
   `src/services/api-types.ts` e `src/messages/messages.json`, `.gitignore`, `.prettierignore`, `eslint.config.js` e nome do pacote
   `teacher-portal`.
7. **REGRESSÃO DA FATIA 4 ACHADA E CORRIGIDA**: `services/semestral.ts` lia as seções do relatório semestral por **chaves de texto em
   português** (`parte(conteudo, 'frequenciaEEstudo')`, `f.frequenciaAulas`…) sobre um `Record<string, unknown>`. O renomeador de
   propriedades da fatia 4 não alcança isso e o `tsc` não reclama, então desde a fatia 4 a tela de conferência do semestral buscava
   chaves que o fio já não tem (`attendanceAndStudy`, `lessonAttendance`…) e mostraria os campos vazios. Os testes de unidade e o e2e
   passavam porque as fixtures repetiam as chaves antigas e o e2e não confere o conteúdo dos campos. A fatia 5 troca código **e**
   fixtures para os nomes do fio (decisão 2); as fixtures agora refletem o JSON real do backend. Nenhum teste novo foi acrescentado.
8. **Tema (CSS e código)**: o seletor `:root[data-tema='escuro']` de `tokens.css` e `base.css` foi para `[data-theme='dark']` junto
   com `dataset.theme` e o valor guardado (o guardião acusou; sem isso o tema escuro deixaria de se aplicar).
9. **Exceções permanentes do glossário (novas ou ampliadas, todas com motivo)**: `download-file-name` vale também no frontend (nome
   sugerido ao baixar o PDF e asserções), `hmac-domain-prefixes` inclui `frontend/tests/e2e/support.ts` (o semeador calcula o mesmo
   HMAC que o backend; o prefixo `codigo:` não pode mudar) e `spec-folder-references` (nova, só `artifacts`): o caminho
   `specs/001-fluxo-relatorios` em `frontend/package.json` e no `frontend/Dockerfile`.
10. **Infra tocada por necessidade (não é a fatia 6)**: `infra/caddy/Caddyfile` (`/marca/*→/brand/*`, o matcher de cache dos ativos)
    e `infra/compose.test.yaml` (o segredo HMAC dos testes e2e passou a `hmac-secret-for-e2e-tests-with-more-than-32-bytes` nos dois
    serviços, igual ao padrão de `support.ts`). `/fontes/*` no Caddyfile é caminho morto (o Vite emite as fontes em `/assets`):
    anotado para a fatia 6, não corrigido.
11. **Exclusões temporárias (T163)**: `glossary-temporary-exclusions.json` regenerado: 0 arquivos do frontend e **1 termo** do
    frontend (`relatorios` na URL padrão do banco do e2e em `support.ts`, dono = fatia 6, porque o nome do banco e do usuário é da
    fatia 6). Restam 15 arquivos de `infra/`, `.github/`, `.gitignore` e `.dockerignore` e 48 termos do `application` (fatia 6).
12. **Texto**: nenhuma expectativa de texto para pessoas mudou. Nos testes mudaram só nomes: rotas, parâmetros de consulta, ids,
    valores de união, classes, nomes de arquivos fictícios (`aula.m4a→lesson.m4a`…), o valor do cookie CSRF simulado, o segredo HMAC
    padrão do e2e, o título de dois `describe` (`GravadorAudio`, `DialogoConfirmacao`) e a mensagem de erro de desenvolvimento do
    `useTheme` (`config.reviewedText`). Os títulos `describe('TelaPainel (02)', …)` etc. seguem como estão: citam as telas do
    protótipo (texto).
13. **Verificador da fatia (T165)**: `tools/rename-check-frontend5.mjs` compara os tokens do compilador, arquivo a arquivo (pareados
    pela mesma função de caminho): identificador trocado precisa estar no mapa; literal só pode trocar palavra de código; JSX e
    textos para pessoas idênticos; binários por SHA-256; CSS por palavras. Normaliza o que o Prettier reflui (vírgula final,
    parênteses de JSX e de `return`, `{' '}`, `;` de membros de tipo).

## Resultados (T164 a T166)

| Suíte | Linha de base (T149) | Fatia 5 | Resultado |
|---|---|---|---|
| `backend` | 750 | código idêntico ao da fatia 4 (ver nota) | guardiões Java e de artefatos verdes |
| `frontend` (lint, `tsc -b` e guardião TypeScript incluídos) | 77 (13 arquivos) | 77 (13 arquivos) | verde |
| `e2e` (Playwright + axe, 390 px e 1440 px, claro e escuro) | 19 | 19 | verde, 0 violações de acessibilidade |

- **Nota do backend**: esta fatia não altera nenhum arquivo de `backend/`; a cobertura do `core` é, portanto, a da fatia 4 (linhas
  3.178/3.238, branches 806/875). Rodei só o que depende do que mudou: `LanguageGuardTest`, `LanguageGuardSelfTest` e
  `ArtifactsLanguageGuardIT` (glossário e exclusões novos), todos verdes. A suíte completa do backend (750 testes) não foi repetida.
- `rename-check --frontend` (fatia 5): 97 arquivos pareados, 96 comparados (1 revisado à mão), **6.903** trocas pelo mapa, **0** pares
  fora do mapa, **0** textos alterados, **0** arquivos sem par, **0** hashes diferentes, **0** outras divergências
  (`evidence/slice-5-rename-check.txt`).
- `rename-check --catalog` sobre o `messages.json` gerado: 217 chaves, **0 divergências** (`evidence/slice-5-catalog.txt`).
- Guardião TypeScript sem exclusões do frontend (salvo o termo da decisão 11); guardião de artefatos sem violação em `frontend/`.

## Achados e avisos para o checkpoint

- **Regressão da fatia 4** (decisão 7): corrigida aqui; vale conferir a tela de conferência do semestral com uma IA real (T101 segue
  pendente do usuário) para ver os 10 campos preenchidos.
- **Teste instável que já existia** (`SignInScreen.test.tsx`, "bloqueio (01c)"): ao falhar a primeira tentativa, `setNow(Date.now())`
  e `until: Date.now() + …` leem relógios diferentes; se o milissegundo virar entre as duas leituras, o aviso mostra "3 minuto(s)"
  em vez de "2". Falha só sob carga (passou 3 de 3 isolado e na rodada oficial). Não corrigido, só anotado.
- **O glossário não conhece algumas palavras do código** (`hora`, `aba`, `cor`, `variante`, `ate`, `monograma`…): o guardião não as
  acusa. Renomeei à mão as que apareciam no frontend; os marcadores `{data}` e `{hora}` do catálogo (fatia 4) continuam em
  português e ficam como estão (mudariam o catálogo).
- `frontend/CLAUDE.md` e os demais `CLAUDE.md` seguem descrevendo os nomes antigos (fatia 7).
