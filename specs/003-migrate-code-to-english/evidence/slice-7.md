# Evidência — Fatia 7: documentação, emenda e quitação

Registro das tasks T192 a T232 (`tasks.md`). Nenhum valor de texto, CPF, nome de aluno, chave ou token é registrado
aqui; só contagens, nomes de arquivos e resultados.

## Linha de base (T192)

Árvore: commit da fatia 6 (`52f0b7f`). A fatia 6 terminou com as três suítes verdes sobre exatamente essa árvore
(`evidence/slice-6.md`), então a linha de base da fatia 7 **é o resultado final da fatia 6**, sem rodar de novo.

| Suíte | Testes | Resultado |
|---|---|---|
| `backend` — total (`core` 324, `application` unitários 84, Failsafe padrão 274 com 1 ignorado, Failsafe `internal-transport` 71) | 753 | verde |
| `frontend` | 77 (13 arquivos) | verde |
| `e2e` | 19 | verde |

Cobertura do `core` (`evidence/slice-6-jacoco.csv`): linhas 3.178/3.238, branches 806/875.

## Decisões tomadas durante a fatia (registro corrente)

1. **Documentação (T193 a T206)**: reescritos, com os nomes reais conferidos no código, os `CLAUDE.md` de `backend/`, `backend/core/`,
   `backend/application/`, `frontend/`, `infra/`, `.github/`, `.specify/`, `specs/` e da raiz; o `README.md` da raiz (era um esboço de
   duas linhas) ganhou estrutura, comandos `up`/`tests` e o aviso de regerar o `infra/.env`; `assets/CLAUDE.md` e
   `portal-do-professor-design-system/CLAUDE.md` tiveram um ajuste mínimo cada (`real-ai`, `src/styles/tokens.css`);
   `assets/README.md` e `portal-do-professor-design-system/README.md` não citam nome que deixou de existir: **sem alteração**
   (as pastas de exemplo do `assets/README.md`, `identidade-visual/` e `documentos/`, são exemplos de organização futura, fora de
   escopo). O bloco de comandos da seção 3.2 do `PLANO_EXECUCAO_SPECKIT.md` foi atualizado, e a frase sobre a feature 003 passou a dizer
   que já foi feito; o resto do documento (planejamento histórico) ficou como estava. O `infra/.env.example` foi revisado na fatia 6 (T180)
   e só ganhou aqui a correção do número da feature do n8n.
2. **Notas de histórico e "feature 003 → 004" (T207 a T221)**: nota de histórico no topo dos 6 documentos principais de cada uma das
   specs 001 e 002 (`spec`, `plan`, `research`, `data-model`, `quickstart`, `tasks`), abaixo do título ou do bloco de metadados das
   listas de tarefas; `tools/rewrite/add-history-note.mjs` é idempotente. A "feature 003" que significava o n8n virou **004**: 33 linhas
   em `spec.md`, `plan.md`, `research.md`, `quickstart.md`, `tasks.md` e `contracts/canal-telegram-interno.md` da spec 002, no comentário
   de `application.yaml`, no do `SimulatedActionsExecutorTest` e no cabeçalho do bloco "Canal Telegram interno" do `infra/.env.example`
   (`tools/rewrite/fix-n8n-references.mjs`, que só toca as expressões "feature 003", "da 003" e "workflow da 003"; `FR-003`,
   `SC-003` e `T003` ficam). **Varredura (T221)**: na spec 001 a busca acha só `FR-003`/`SC-003`/`T003` e as frases da própria
   migração; as demais ocorrências de "feature 003" em `backend/` e `frontend/` (Javadoc dos testes de migração e do guardião,
   `V7`/`V8`) são da migração e ficam; `PLANO_EXECUCAO_SPECKIT.md` é planejamento histórico e já registra a renumeração.
3. **Emenda 2.1.0 da constituição (T222)**: versão 2.0.0 → **2.1.0** (MINOR), última emenda 2026-10-15. O relatório da 2.0.0 virou
   "Histórico — v2.0.0" (texto preservado, inclusive o item que diz que a feature 003 "ainda NÃO existe spec"). Mudanças no
   Princípio VI: (a) seis sufixos novos (`*Entity`, `*Command`, `*Filter`, `*Converter`, `*Codec`, `*Config`); (b) a categoria "nomes próprios e
   material de referência" (e valores persistidos que não podem mudar) entra na lista de exceções, e toda exceção tem de estar no
   `glossary.json` com motivo e categoria; (c) o glossário em forma de dados e o **guardião de idioma** passam a ser parte da regra;
   (d) a **regra de transição** foi substituída por um registro da quitação, e o bloqueio de feature nova caiu. As exceções do
   `glossary.json` que diziam "proposta para a emenda 2.1.0" passaram a "nomes próprios e material de referência (Princípio VI,
   emenda 2.1.0)". Templates de `.specify/templates/` conferidos: nenhum cita idioma, sufixos, Hostinger nem a regra de transição;
   `.constitution-template.json` guarda só o hash do modelo. **A emenda depende da aprovação do dono do produto (Governança): é pedida na
   descrição do PR.**
4. **Arquivo de exclusões apagado (T223)**: `git rm .specify/memory/glossary-temporary-exclusions.json`. O passo `test ! -e …` do
   `ci.yml` continua e agora passa (o comentário dele foi atualizado). O código dos guardiões ainda sabe ler o arquivo se existir (útil para
   quem quiser, numa branch, introduzir uma regra nova), mas o CI o reprova.
5. **Achados do inventário final (T224)** — a lista de palavras desconhecidas (2.896) não podia ser classificada à mão, então cruzei com os
   dicionários `pt_BR` e `en` do aspell (em contêiner): 61 palavras existem só em português; as ocorrências em **identificador** são o
   que o guardião não via. Renomeei (renomeação pura, `tools/rewrite/rename-leftovers.mjs`) e acrescentei ao `words` do glossário:
   `leaseAte`→`leaseUntil`, `payloadAte`→`payloadUntil` (`ate`→`until`); `hora`→`time` (o formatador `MessageFormatting.time`, a chave
   `time` dos parâmetros de mensagem e o marcador `{hora}`→`{time}` do catálogo, mais as telas e o `history.ts` do frontend); `Plano`/`PLANOS`/`plano`→`Plan`/`PLANS`/`plan`
   (massa de demonstração); `alto`→`high` (UUID); `cadeia`→`chain` (2 filtros); `classe`→`javaClass` (2 testes ArchUnit); `QUINZE`/`TRINTA`/`trinta`→`FIFTEEN`/`THIRTY`/
   `thirtySeconds`; `maio`→`may`; `doPar`→`forPair`; `par`→`pair`; `signOutPeloButton`→`signOutByButton`; a classe CSS `tabs__aba`→`tabs__tab`.
   Palavras acrescentadas ao glossário: `aba`, `ate`, `alto`, `cadeia`, `classe`, `hora`, `maio`, `musical`, `musicais`, `par`, `plano`,
   `planos`, `quinze`, `trinta`. O que sobrou é texto para pessoas (fixtures, frases de tela), nomes de pessoas, nomes de instrumentos da
   massa e abreviações de meses do histórico, que não são identificador. `data` (calendário × dado) é homônimo aceito (nota no glossário):
   o marcador `{data}` fica.
6. **Achado grave: o mesmo erro da fatia 5 voltou a aparecer no semestral (corrigido, com teste)**. A fatia 5 achou que `semestral.ts` lia as
   seções por chaves de texto e as corrigiu, mas **duas chaves foram traduzidas pela metade** e passaram: o frontend lia `m.commentMusical` e
   `p.goalsMusicais`, enquanto o fio traz `musicalComment` e `musicalGoals` (esquema `semester-report.schema.json`, `V7`). Efeito: os campos
   "Comentário musical" e "Metas musicais" apareceriam vazios na conferência do relatório semestral (o `tsc` não vê acesso por texto e as
   fixtures dos testes repetiam o nome errado, ou nem o tinham). Pegou-o o cruzamento com o dicionário (`musicais` só existe em português).
   Correção em `services/semester.ts` e teste novo `tests/services/semesterSections.test.ts`, que lê o esquema do backend, gera um
   conteúdo com **todas** as chaves e exige que cada valor apareça em alguma seção; verifiquei que ele **reprova** (vermelho) com os dois
   nomes errados e passa com os certos. Frontend: 77 → 78 testes.
7. **Ferramentas de verificação da fatia** (em `specs/003-migrate-code-to-english/tools/`): `doc-links-check.mjs` (T227; links relativos,
   caminhos, subcomandos do `local.sh`, variáveis de ambiente e classes citadas em crase), `synonym-check.mjs` (T225; mapas de
   identificadores × glossário e pares concorrentes no código final), `rewrite/rename-leftovers.mjs`, `rewrite/fix-n8n-references.mjs`,
   `rewrite/add-history-note.mjs` e `rewrite/amend-constitution.mjs`. Rodam no Node do host (as mesmas versões do contêiner
   `node:24-alpine`, só leem arquivos).
8. **Verificação com o LLM real (T229)**: **pendente**. Exige `AI_PROVIDERS=real` e as chaves reais de transcrição e LLM no
   `infra/.env`, que só o dono tem (o `infra/.env` atual usa IA simulada). O roteiro está em `quickstart.md` §8. O T101 (fatia 3) segue pendente
   pelo mesmo motivo. Os dois são registrados na descrição do PR como "pendente do usuário".
9. **Rodadas e correções durante a fatia**: a primeira rodada das três suítes parou na compilação (a renomeação do parâmetro `classe` deixou duas
   referências no `ArchitectureRulesTest`; corrigidas à mão); a segunda, no guardião Java, porque as palavras novas do glossário `classe` e `musical`
   casam por radical com palavras do inglês (`classes`, `musicalGoals`): as duas saíram do `words` e ficou uma nota no glossário. A terceira passou no backend e
   parou no `tsc` do teste novo do semestral (`noUncheckedIndexedAccess`); depois do ajuste, `tests frontend` e `tests e2e` foram repetidos e passaram.

## Resultados (T223, T228, T232)

Comando: `sh infra/scripts/local.sh tests all` (backend) e `tests frontend` / `tests e2e` repetidos depois do ajuste do teste, sobre a árvore final.

| Suíte | Linha de base (T192) | Fatia 7 | Resultado |
|---|---|---|---|
| `backend` — `core` (Surefire) | 324 | 324 | verde |
| `backend` — `application` unitários | 84 | 84 | verde |
| `backend` — Failsafe padrão | 274 (1 ignorado) | 274 (1 ignorado) | verde |
| `backend` — Failsafe `internal-transport` | 71 | 71 | verde |
| `backend` — total | 753 | 753 | verde |
| `frontend` (lint, `tsc -b`, guardião TypeScript incluídos) | 77 (13 arquivos) | **78** (14 arquivos) | verde |
| `e2e` (Playwright + axe) | 19 | 19 | verde, 0 violações de acessibilidade |

- **Número de testes**: o frontend ganha 1 (`semesterSections.test.ts`, decisão 6). É a única mudança; nenhum teste removido ou alterado.
- **Cobertura do `core`**: `jacoco.csv` **idêntico byte a byte** ao da fatia 6 e ao da fatia 4 (`evidence/slice-7-jacoco.csv`): linhas 3.178/3.238, branches 806/875.
- **Guardiões sem exclusões (T223)**: `LanguageGuardTest`, `LanguageGuardSelfTest`, `ArtifactsLanguageGuardIT` e seu autoteste, e o guardião TypeScript passam com o arquivo de
  exclusões **apagado**; o passo `test ! -e …` do `ci.yml` passa (`test ! -e .specify/memory/glossary-temporary-exclusions.json` → o arquivo não existe).
- **Inventário final (T224)**: `evidence/word-inventory-final.txt` (3.447 palavras distintas: 559 no glossário, 2.888 a classificar). As 559 do glossário que ainda aparecem estão em
  migrações imutáveis (`V1`–`V8`), contratos e documentos de specs, textos de tela e de teste, palavras do professor e prefixos do HMAC: todas cobertas por exceção.
  As 2.888 restantes foram cruzadas com os dicionários pt_BR e en do aspell: o que existe só em português e é identificador foi renomeado (decisão 5).
- **Sinônimos (T225, T226)**: `evidence/slice-7-synonyms.txt`: 0 achados. 16 pares de abreviação/termo aceitos com motivo (`db`/`database`, `msg`/`message`, `delete`/`remove`…);
  102 palavras do glossário que o código traduziu por um termo idiomático diferente (`emitir`→`issue`, `gravar`→`record`…), listadas só como informação, porque o glossário guarda
  um equivalente por palavra e o nome final foi escolhido na revisão de cada fatia.
- **Referências da documentação (T227, T228)**: `evidence/slice-7-doc-links.txt`: 17 documentos, 243 referências conferidas, **0 quebradas**. O `quickstart.md` da feature 003 passou
  a usar `tests` e marca como "antes da fatia 6" ou "na `main`" os comandos antigos que cita de propósito.
- **Texto**: nenhuma expectativa de texto para pessoas mudou em nenhuma fatia desta PR. Na fatia 7 o marcador `{hora}` do catálogo virou `{time}` (5 mensagens), junto com o código que o preenche;
  o texto exibido é o mesmo (conferido pelos testes de mensagem, de Telegram, de PDF e do e2e).
- **T229 (LLM real)**: pendente, ver decisão 8.

## Tasks de convergência (T236 a T241)

- **T236 (referência da cobertura)**: a medida da `main` pura variou de um dia para outro (3.243/806 em 2026-10-11, 3.244/807 em 2026-10-10: `ObterDocumentoRelatorio…`). A comparação final usa a regra que
  vale para a PR inteira: o `jacoco.csv` do `core` é **byte a byte idêntico** de uma fatia para a seguinte desde a fatia 4 (fatias 4, 5, 6 e 7), e o percentual de linhas (**98,15%**)
  é o mesmo da `main` (3.243/3.304 = 98,15%); a contagem de linhas muda porque os arquivos foram reformatados (google-java-format) depois dos nomes novos, e as branches são as mesmas (806/875).
- **T237** feita em `evidence/slice-1.md`; **T238** em `evidence/slice-2.md`; **T239** em `evidence/slice-1.md`; **T241** em `quickstart.md`.
- **T240**: as exceções `generated-files` e `glossary-data` estão na tabela de exceções da descrição do PR (categoria "nomes de terceiros ou exigidos por ferramenta", que o Princípio VI já admite e não precisa ratificar).
