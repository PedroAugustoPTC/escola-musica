# Evidência — Fatia 4: contratos, corte do fio e `V8`

Registro das tasks T104 a T148 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é registrado
aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T104)

Árvore: commit da fatia 3 (`96e4020`). Entre o fim da rodada de comparação da fatia 3 e este ponto de partida nenhum
arquivo de código mudou, então a linha de base da fatia 4 **é o resultado final da fatia 3** (`evidence/slice-3.md`),
sem rodar de novo suítes sobre uma árvore idêntica.

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — `core` (Surefire) | 324 | verde |
| `backend` — `application` unitários | 79 | verde |
| `backend` — Failsafe padrão | 264 (1 ignorado) | verde |
| `backend` — Failsafe `transporte-interno` | 71 | verde |
| `backend` — total | 738 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

## Decisões tomadas durante a fatia (registro corrente)

1. **Contratos no lugar (T111 a T113)**: os três contratos de fio de `specs/003-migrate-code-to-english/contracts/` passam a
   ser os de `specs/001-…/contracts/api-portal.openapi.yaml`, `specs/002-…/contracts/actions-v1.schema.json` e
   `specs/002-…/contracts/telegram-channel-internal.openapi.yaml` (os dois últimos com `git mv`). `rename-check --contracts`
   (`tools/rename-check-contracts.mjs`, `tools/lib/contracts.mjs`): **0 diferenças de forma** nos três pares; os nomes
   trocados saem em `tools/api-field-map.json` (216 do Portal, 63 das ações, 29 do canal interno).
2. **Renomeação por ferramenta**: constantes de enumeração (B1), componentes de DTO e parâmetros de rota com nome
   implícito (B2) pela receita `RenameMembersByMap` (`tools/rewrite/gen-recipe-slice-4.mjs`, mapa
   `tools/maps/slice-4.json` — o `gen-identifier-map` agora confere os 110 pares de `identifier-map.md` §1/§2 e o contrato:
   0 divergências). Literais Java pelo classificador `tools/lib/literal-classes.mjs` (classes novas `wire`,
   `callback-action`, anotações de rota, SQL com valor de enumeração entre aspas).
3. **`SemesterReportContentDto` (decisão 10 da fatia 3) deixa de existir**: o contrato novo tem os nomes do registro do
   núcleo, então o controlador volta a serializá-lo direto (`structuralEdits` em `explicit-pairs.json`).
4. **Vocabulário das ações de botão** (`slice4.callbackActions`, `v1:<ação>:<argumento>`): `inicio→home`, `reg→register`,
   `sem→semester`, `hist→history`, `hitem→history-item`, `hmais→history-more`, `aluno→student`, `confal→confirm-student`,
   `trocal→switch-student`, `foco→focus`, `aprov→approve`, `aprovok→approve-ok`, `alt→change`, `canc→cancel`,
   `cancok→cancel-ok`, `tent→retry`, `desc→discard`, `descok→discard-ok`, `gerar→generate`, `gerarsim→generate-yes`,
   `cont→continue`, `voltar→back`, `per→period`, `sair→sign-out`, `sairok→sign-out-ok`, `sim→yes`. As palavras digitadas
   pelo professor (`aprovar`, `cancelar`, `inicio`, `sair`, `sim`…) **não mudam** (exceção permanente `teacher-keywords`):
   `TelegramKeywords`, `TelegramConfig` (comandos do bot) e os testes que digitam texto ficam como estão.
5. **Nome do arquivo de download (`relatorio-aula-…pdf`) segue em português** (exceção `download-file-name`): o classificador
   protege o prefixo `relatorio-aula`/`relatorio-semestral` e `PdfFileNamesIT` o confirma.
6. **Chaves do catálogo e códigos de erro**: 217 chaves e 23 marcadores trocados (`tools/rewrite/edit-catalog.mjs`,
   `tools/message-key-map.json`, `tools/message-marker-map.json`); as chaves montadas em tempo de execução ficam
   componíveis (`STEP_<STEP>`, `STATUS_<STATUS>`, `TYPE_<TYPE>`, e `AUDIO_<motivo>`). Uma tradução palavra a palavra ruim
   (`FORMATOS_E_LIMITE`) foi corrigida à mão (`FORMATS_AND_LIMIT`).
7. **Códigos de evento de log** (`erro-inesperado`, `trabalho-falhou`…): são identificadores; os 26 códigos de
   `tools/maps/slice-4.review.05.json` ganham nomes em inglês (`unexpected-error`, `job-failed`…), junto com os nomes de
   campo (`tipo={}` → `type={}`); o texto livre das mensagens de log segue em português.
8. **`V8__rename_enum_values.sql`** (gerada por `tools/gen-v8.mjs`): descarta 9 índices parciais e 6 `CHECK`s, troca os
   valores de 31 colunas, as chaves e o vocabulário do JSON de `channel_action` e o token de confirmação de `flow_data`,
   recria os 15 objetos com os mesmos nomes, e **aborta** se sobrar valor antigo em coluna de conjunto fechado ou se um valor
   novo não couber na coluna. `MigrationV7ReadableDataIT` virou `MigrationV8ReadableDataIT` (migra até a V8): depois do
   corte os adaptadores só leem os valores novos, então os dados da V6 só ficam legíveis a partir da V8.
9. **Mudança externa**: as rotas do canal interno (`/interno/v1/canal/telegram/…` → `/internal/v1/channel/telegram/…`) e o
   cabeçalho `X-Canal-Entrega-Token` → `X-Channel-Delivery-Token` mudam para quem chama o backend (o n8n, fora do repositório).
   O Caddy só publica `/api/*`, então o Portal não é afetado.
10. **Frontend** (`tools/rewrite/apply-slice-4-frontend.sh`, `frontend-wire.mjs` com `ts-morph`): propriedades dos esquemas
    gerados renomeadas como símbolo (120), chaves `Esquemas['X']`, valores de enumeração, códigos de erro, chaves do
    catálogo, marcadores, caminhos de API e parâmetros de consulta; o tema guardado no navegador passa a
    `reports.theme` com `light`/`dark` (o tema interno só muda na fatia 5).
11. **Formato gravado do HMAC (achado da auditoria de literais)**: a ferramenta havia trocado os prefixos `codigo:` e `origem:`
    que entram no HMAC do código de acesso e da origem em `DataProtectionAdapter`. Os hashes ficam gravados no banco, então
    trocar o prefixo invalidaria todos os códigos e vínculos já emitidos. **Revertido** (o arquivo voltou ao conteúdo do
    commit 3), registrado como exceção permanente `hmac-domain-prefixes` no glossário e fixado por
    `DataProtectionStoredFormatTest` (cálculo independente do HMAC; 2 testes).
12. **Exceções permanentes novas no glossário** (todas com motivo): `hmac-domain-prefixes`, `feature-folder-names` e
    `user-text-samples`; `legacy-schema-names` ganhou os testes da `V8` e `OldButtonFormatIT`; `teacher-keywords` ganhou
    `TelegramConfig`, `SimulatedLlmAdapter` e os testes; `download-file-name` ganhou o termo `oficial.pdf`.
    `glossary-temporary-exclusions.json` regenerado: **0 termos da fatia 4**.
13. **Ferramentas**: `rename-check --frontend` (`tools/rename-check-frontend.mjs`, comparação palavra a palavra dos 44
    arquivos de `frontend/src` e `frontend/tests` fora os gerados e o remendo do tema); `rename-check` aceita `removedFiles`
    em `explicit-pairs.json`, os tipos `import*` em `structuralEdits` e o despejo `RC_DUMP` dos literais divergentes.
14. **Chave de formulário multipart (`versaoBase`→`baseVersion`)** escapou da reescrita por `ts-morph` (é literal de texto,
    não propriedade): o e2e acusou (2 testes) e o teste unitário de `AudioPerguntasConferencia` também guardava a chave
    antiga. Corrigidos `relatoriosAula.ts`, `semestral.ts` e o teste (nome de campo do fio, sem texto de usuário).
15. **Expectativas de texto**: nenhuma expectativa de **texto de usuário** foi alterada. Mudaram, em testes, só identificadores
    consequência de nomes migrados: códigos de erro, chaves e marcadores do catálogo, códigos de evento de log
    (`webhook-nao-registrado`→`webhook-not-registered`, `telegram-erro-interpretacao`→`telegram-interpretation-error`,
    `canal-acao-falha-definitiva`→`channel-action-permanent-failure`), rotas, valores de enumeração em linhas de roteiro de
    paridade, chaves do JSON do fio e dados arbitrários de teste (nomes de arquivo, códigos fictícios). Os trechos de
    texto de usuário que a ferramenta tocou por engano (`parecer=`, `alteração`, `relatorio-aula.pdf`) foram restaurados.
    A ordem alfabética de custos de IA em `LessonReportParityScriptIT` mudou porque os valores de enumeração foram
    renomeados (lista reordenada, mesmos itens).

## Resultados (T143 a T146)

| Suíte | Linha de base (T104) | Fatia 4 | Resultado |
|---|---|---|---|
| `backend` — `core` (Surefire) | 324 | 324 | verde |
| `backend` — `application` unitários | 79 | 81 (+2 `DataProtectionStoredFormatTest`) | verde |
| `backend` — Failsafe padrão | 264 (1 ignorado) | 274 (1 ignorado) | verde |
| `backend` — Failsafe `transporte-interno` | 71 | 71 | verde |
| `backend` — total | 738 | 750 (+12) | verde |
| `frontend` | 77 (13 arquivos) | 77 (13 arquivos) | verde |
| `e2e` | 19 | 19 | verde |

Os +12 são só testes novos: `MigrationV8StructureIT`, `MigrationV8ReadableDataIT` (substitui a `MigrationV7ReadableDataIT` para a
leitura dos dados da V6, ver decisão 8), `MigrationV8ProtectionsIT`, `MigrationV8JsonKeysAndUnknownValueIT`, `OldButtonFormatIT` e
`DataProtectionStoredFormatTest`. Nenhum teste foi removido.

Nota de procedimento: a suíte completa do backend foi rodada uma vez neste checkpoint; depois dela só mudaram o frontend
(chave multipart, 3 arquivos), que teve `frontend` e `e2e` rodados de novo, e os arquivos de evidência. O Failsafe com
`-Dit.test=` roda as classes nas duas execuções contra o mesmo banco, então a segunda execução de `ChannelActionsDeliveryIT`
falha por artefato em rodada estreita; na suíte completa ela passa.

- `rename-check --slice 4` (backend): 454 arquivos Java pareados, **0** em cada contador; `--frontend`: 44 arquivos, 1.305
  palavras trocadas pelo mapa, **0** fora do mapa, **0** de contagem diferente (`evidence/slice-4-rename-check.txt`).
- `rename-check --catalog`: 217 chaves na base e no novo, bijeção, 23 marcadores, `messages_pt_BR.properties` e
  `mensagens.json` com **0 divergências** (`evidence/slice-4-catalog.txt`).
- `rename-check --contracts`: **0 diferenças de forma** nos três contratos (216, 63 e 29 nomes trocados); as cópias de
  `specs/001` e `specs/002` são idênticas byte a byte às de `specs/003` (`evidence/slice-4-contracts.txt`).
- Cobertura do `core` (`evidence/slice-4-jacoco.csv`): linhas 3.178/3.238 (98,15 %) contra 3.176/3.237 (98,12 %) da fatia 3,
  branches 806/875 e métodos 745/766, iguais. Não caiu.

## Pendências e avisos para o checkpoint

- **Mudança externa**: rotas do canal interno `/internal/v1/channel/telegram/…` e cabeçalho `X-Channel-Delivery-Token`
  (o n8n, fora do repositório, precisa acompanhar).
- O cookie `sessao`→`session` desconecta todos uma vez; a chave do tema no navegador vira `reports.theme`.
- Botões antigos do Telegram respondem "Esta opção não vale mais." (`OldButtonFormatIT`).
- `AccessCode.retry` (nome da fatia 2) é um nome questionável para o que é uma leitura do texto digitado; fica anotado, sem
  alterar nesta fatia.
- `backend/CLAUDE.md` e `frontend/CLAUDE.md` ainda descrevem os nomes antigos (fatia 7); o javadoc de `ActionDto` ainda cita
  nomes antigos de contrato.
- As palavras digitadas pelo professor (`aprovar`, `cancelar`…) ficam em português por decisão (exceção `teacher-keywords`).
- As exceções permanentes novas do glossário devem entrar na emenda 2.1.0 da constituição (fatia 7).
- **T101 (LLM real, fatia 3)** segue pendente do usuário.
