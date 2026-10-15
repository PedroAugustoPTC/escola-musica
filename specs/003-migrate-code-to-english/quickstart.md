# Quickstart: como provar a migração para inglês

**Feature**: `003-migrate-code-to-english` | Guia de **validação** (não de implementação). Cada passo diz o
que rodar e o que esperar. Plano: [plan.md](./plan.md). Decisões: [research.md](./research.md).

## 0. Pré-requisitos

- Docker Engine 27+ com Compose v2 e Git (nada mais no host; Git Bash no Windows).
- `infra/.env` gerado a partir de `infra/.env.example`:

```bash
cp infra/.env.example infra/.env
```

- Branch da feature a partir da `main` atualizada (já criada: `spec/003-migrate-code-to-english`):

```bash
git fetch origin && git checkout spec/003-migrate-code-to-english
```

- **Não rode `npm ci` montando `frontend/` num contêiner Node sem volume nomeado para `node_modules`**: reinstala o `frontend/node_modules` do host com binários
  Linux (aconteceu na fatia 1). Use `sh infra/scripts/local.sh tests frontend` (o compose já usa o volume nomeado `node-modules`) ou, para desenvolver fora do
  contêiner, `npm ci` no host.

- Árvore-base para o verificador (a `main`, sem tocar o seu diretório de trabalho):

```bash
rm -rf /tmp/base && mkdir -p /tmp/base && git archive origin/main | tar -x -C /tmp/base
```

## 1. Em cada fatia: as três suítes (devem passar em TODOS os commits)

O subcomando é `tests` (a partir da fatia 6; **antes da fatia 6** era `testes`, e na `main` continua sendo, ver
`plan.md`, *Pontos de decisão* 2).

```bash
sh infra/scripts/local.sh tests backend
sh infra/scripts/local.sh tests frontend
sh infra/scripts/local.sh tests e2e
```

**Esperado**: `BUILD SUCCESS` (todos os testes, cobertura do núcleo ≥ 90% por linha e por branch), lint,
tipos e Vitest sem erro, e Playwright + axe sem falha e sem violação de acessibilidade. Nenhum teste
removido, nenhuma expectativa de texto alterada. O guardião (parte do `testes backend` e do
`testes frontend`) passa.

## 2. Prova de que só o nome mudou (US1)

Verificador lexical (R-02), sem dependências, em contêiner; compara a árvore-base com a de trabalho:

```bash
docker run --rm -v "$PWD":/work -v /tmp/base:/base:ro node:24-alpine \
  node /work/specs/003-migrate-code-to-english/tools/rename-check.mjs /base /work --slice <1..7>
```

**Esperado**: `0 pares fora do mapa`, `0 literais de texto alterados`, `0 arquivos sem par`,
`0 colisões`; a lista de comentários alterados é só informativa.

**Cobertura antes/depois** (linha e branch do `core`; não pode cair):

```bash
sh infra/scripts/local.sh tests backend    # gera backend/<core>/target/site/jacoco/
```

Compare o resumo do JaCoCo com o da `main` (`/tmp/base`, mesma execução): totais iguais ou maiores.

## 3. Catálogo de mensagens idêntico (SC-003) — fatias 3, 4 e 5

```bash
docker run --rm -v "$PWD":/work -v /tmp/base:/base:ro node:24-alpine \
  node /work/specs/003-migrate-code-to-english/tools/rename-check.mjs /base /work --catalog
```

**Esperado**: mesmo número de chaves; para cada chave antiga, `novo[mapa[k]]` igual ao texto antigo com os
marcadores `{…}` trocados pelo mapa; o `messages.json` gerado confere. Qualquer diferença de texto reprova.

## 4. Banco: V7 e V8 (US4-2, FR-013 a FR-015)

Os testes de migração rodam dentro de `testes backend` e cobrem: banco do zero e banco no esquema antigo com
dados; equivalência estrutural (mapa de [data-model.md](./data-model.md)); relatório, conversa do Telegram
e ação da outbox gravados antes continuam legíveis; zero chaves JSON antigas; valor desconhecido aborta a
`V8`; proteções (índices únicos e `CHECK`s) continuam ativas. **Esperado**: todos verdes.

**Ensaio manual com dados existentes** (opcional, recomendado antes do merge). Na `main`, com a pilha antiga
populada (na `main` o comando ainda é `sh infra/scripts/local.sh subir --massa=true`), salve um dump e pare a pilha:

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml exec -T postgres   pg_dump -U relatorios -d relatorios > /tmp/antigo.sql
sh infra/scripts/local.sh parar   # na main; na branch da feature é stop
```

Na branch da feature (já com a fatia 6, o banco e o usuário são `reports`), suba **só o banco**, restaure o
dump (com o backend parado) e então suba o resto; é o backend que, ao iniciar, executa a `V7` e a `V8` sobre
os dados restaurados:

```bash
git checkout spec/003-migrate-code-to-english
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d postgres
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml exec -T postgres   psql -U reports -d reports < /tmp/antigo.sql
sh infra/scripts/local.sh up
```

**Esperado**: a aplicação inicia sem erro; `flyway_schema_history` mostra `1` a `8`; tabelas e colunas com os
nomes de [data-model.md](./data-model.md); relatórios, versões e PDFs antigos abrem no Portal com o mesmo
conteúdo. O dump contém dados fictícios de demonstração e fica só na sua máquina.

## 5. O guardião funciona (US2)

Rodado em `testes backend` (Java, SQL, YAML, JSON, catálogo do banco) e `testes frontend` (TypeScript, CSS).
**Esperado**: passa com zero identificadores em português fora das exceções; cada exceção tem motivo e
casa com algo.

**Prova de que ele barra** (em branch descartável, nunca na da feature):

```bash
git switch -c scratch/guard-proof
# introduza, um por vez: classe/método/variável Java, componente/rota/classe CSS no frontend,
# tabela/coluna no banco (V9), chave em YAML, variável de ambiente, script, workflow.
# exemplos de nomes proibidos (termos do glossário em português): AlunoService, listarAlunos,
# tabela_aluno, ALUNO_URL, alunos.sh
sh infra/scripts/local.sh tests backend
sh infra/scripts/local.sh tests frontend
```

**Esperado**: falha apontando `arquivo:linha`, o termo e o equivalente (`aluno → student`); remover a
violação volta a passar. Depois: `git switch - && git branch -D scratch/guard-proof`.

**Estado final**: `.specify/memory/glossary-temporary-exclusions.json` **não existe** (FR-035) e o passo
correspondente do `ci.yml` passa.

## 6. Contratos (FR-008, FR-012)

- Forma idêntica ao anterior (já feito no plano: 0 diferenças nos 5 contratos); repetida pelo verificador
  na fatia 4 com `--contracts`.
- Os testes de contrato do backend validam respostas reais contra os contratos novos; o `typecheck` do
  frontend, depois de `npm run generate`, compila contra os tipos gerados.

## 7. Ambiente do zero e fluxo principal (US4, SC-005)

```bash
rm -f infra/.env && cp infra/.env.example infra/.env        # preencha as chaves obrigatórias
sh infra/scripts/local.sh up --demo-data=true               # antes da fatia 6: subir --massa=true
```

**Esperado**: todos os serviços saudáveis, carga inicial aplicada com os campos novos. Entre no Portal
com o código da Clara mostrado ao final e percorra, **nos dois canais (Web e Telegram)**: registrar aula
por áudio → perguntas → conferência → alterar → aprovar → baixar o PDF; e o semestral. **Esperado**:
telas, mensagens, botões, rótulos, nome do arquivo baixado (em português) e conteúdo do PDF idênticos aos
da `main`.

**`.env` antigo (US4-4)**: com um `infra/.env` anterior à feature, o `local.sh` aborta listando as chaves
novas que faltam e orienta a regerar a partir do `infra/.env.example`.

**Botão antigo do Telegram (FR-025)**: enviar `v1:aprovar:3` como `callback_data` devolve a resposta segura
de ação desconhecida (teste de regressão em `testes backend`).

## 8. IA real (FR-034, SC-008) — antes do merge

Com `AI_PROVIDERS=real` e as chaves no `infra/.env`:

```bash
sh infra/scripts/local.sh tests real-ai       # antes da fatia 6: testes ia-real
```

Percorra um fluxo de aula e um semestral. **Registre no PR**: campos preenchidos, perguntas geradas,
qualidade percebida e custo estimado. Queda de qualidade atribuível aos nomes novos é **reportada**, não
corrigida mudando instrução de prompt.

## 9. Fechamento

Checklist do PR (descrição): matriz de suítes por commit, saída do `rename-check`, comparação do catálogo,
dos contratos e da cobertura, resultado dos testes de migração, ensaio manual com dados (§4), prova do
guardião (§5) e verificação com o LLM real (§8). Depois: `code-review-graph build`. O merge é do usuário.
