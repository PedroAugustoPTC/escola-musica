# Quickstart: validar o fluxo de relatórios (Web e Telegram)

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

**Feature**: `001-fluxo-relatorios` | **Plano**: [plan.md](./plan.md)

Guia para subir o ambiente e provar, de ponta a ponta, que a feature funciona nos dois canais. Os
detalhes de API, conversa e dados estão nos contratos e no modelo de dados, que este guia apenas
referencia.

## 1. Pré-requisitos

- Docker Engine 27+ com Docker Compose v2. É a **única** dependência da máquina (constituição,
  "Containerização e implantação").
> **Nota (feature 004):** o canal Telegram passa pelo n8n, que sobe junto com a pilha (`sh infra/scripts/local.sh up`). Para ligá-lo como transporte: `sh infra/scripts/telegram-webhook.sh switch`; para voltar: `rollback`; para conferir: `status` e `verify`. Ensaio sem bot real: `sh infra/scripts/local.sh tests rehearsal`. Passo a passo em `specs/004-n8n-telegram-transport/quickstart.md`.

- Para o canal Telegram: um bot **de testes** criado no @BotFather (token), `api_id` e `api_hash`
  obtidos em my.telegram.org (exigidos pelo servidor local da API de bots, R-05) e uma conta de
  Telegram de teste.
- Chaves dos provedores de IA (transcrição e LLM) para os cenários com IA real. Sem elas, use o
  perfil `ia-simulada` (seção 5).

## 2. Configuração

```bash
cp infra/.env.example infra/.env
```

Preencha em `infra/.env` (nunca versionado):

| Variável | Uso |
|---|---|
| `POSTGRES_PASSWORD` | banco |
| `CPF_CHAVE_CIFRAGEM` | Base64 de 32 bytes (AES-256-GCM) |
| `SEGREDO_HMAC` | HMAC de códigos de acesso, CPF e origens (≥ 32 bytes) |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `TELEGRAM_SEGREDO_WEBHOOK` | canal Telegram |
| `TRANSCRICAO_URL`, `TRANSCRICAO_MODELO`, `TRANSCRICAO_CHAVE` | provedor de transcrição (padrão: Groq, `whisper-large-v3-turbo`) |
| `LLM_URL`, `LLM_MODELO`, `LLM_CHAVE` | provedor de LLM (padrão: Groq, `llama-3.3-70b-versatile`) |
| `PROVEDORES_IA` | `real` ou `simulado` |

Valores com padrão (ajustáveis): `AUDIO_LIMITE_MB=50`, `SESSAO_WEB_HORAS=12`,
`ACESSO_MAX_TENTATIVAS=5`, `ACESSO_BLOQUEIO_MINUTOS=15`, `CODIGO_VALIDADE_DIAS=7`,
`AUDIO_PENDENTE_MINUTOS=30`, `ESCOLA_FUSO_HORARIO=America/Sao_Paulo`.

Se o bot de testes já foi usado na API da nuvem, chame `logOut` uma vez antes de apontá-lo para o
servidor local:

```bash
curl -s "https://api.telegram.org/bot<TOKEN>/logOut"
```

## 3. Subir o ambiente (um comando)

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d --build
```

Resultado esperado: `web`, `backend`, `postgres` e `telegram-bot-api` saudáveis
(`docker compose ps`). O portal responde em `https://localhost` (certificado local do Caddy), e só
o `web` publica portas. O backend registra o webhook no servidor local ao iniciar (log
`webhook-registrado`).

## 4. Carga inicial

```bash
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml run --rm backend carga --arquivo /carga/exemplo.json
```

Esperado: um resumo com professores e alunos criados e, para cada professor novo, a linha
`email → CÓDIGO (válido até …)`. O exemplo fictício cria a professora "Clara" com os alunos Matteo
Mafra, Olavo Torrente e Miguel de Castro Torrente (nomes das gravações de `assets/`) e o professor
"Bruno", com um aluno próprio para o teste de acesso cruzado. Formato: [contracts/carga-inicial.md](./contracts/carga-inicial.md).

## 5. Rodar os testes (em contêiner)

```bash
docker compose -f infra/compose.test.yaml run --rm testes-backend
```

```bash
docker compose -f infra/compose.test.yaml run --rm testes-frontend
```

```bash
docker compose -f infra/compose.test.yaml run --rm testes-e2e
```

Esperado: tudo verde; o JaCoCo aprova **≥ 90% de linhas e de branches** no módulo `nucleo` (senão,
o build falha); o ArchUnit aprova as regras de camada e de nomenclatura; os testes de contrato
validam a API contra [contracts/api-portal.openapi.yaml](./contracts/api-portal.openapi.yaml); os
testes de conversa seguem [contracts/telegram-conversa.md](./contracts/telegram-conversa.md); o
roteiro de paridade passa pelos dois canais; o axe não aponta violações AA a 390 px e 1440 px, nos
temas claro e escuro. `testes-e2e` usa `PROVEDORES_IA=simulado`, com respostas determinísticas
gravadas.

Suíte opcional com IA real (fora da cobertura), que mede tempo e custo com as gravações de
`assets/`:

```bash
docker compose -f infra/compose.test.yaml run --rm testes-ia-real
```

Esperado: o relatório de saída lista, por gravação, o tempo até "Pronto para conferência" (meta
SC-001: ≤ 2 min em 90%) e o custo estimado, e confirma que os `.mp4` de 24 MB e 31 MB foram aceitos.

## 6. Cenários de validação manual

Execute cada cenário **uma vez em cada canal**, com as mesmas entradas, e compare os resultados
(SC-006).

| # | Cenário | Passos | Resultado esperado |
|---|---|---|---|
| V1 | Acesso (US2) | Web: entrar com o código de Clara. Telegram: enviar o mesmo código ao bot. | Painel com "Olá, Clara", 3 alunos e nenhum relatório em aberto; bot com "Conta vinculada com sucesso, Clara!" e as mesmas informações e ações. |
| V2 | Bloqueio (FR-007) | Errar o código 5 vezes em cada canal (outra conta ou navegador). | 6ª tentativa, mesmo com o código certo: "Acesso bloqueado temporariamente…" com o tempo restante. |
| V3 | Registro com perguntas e alteração por áudio (US1) | Web: aluno Matteo → enviar `Aula Matteo Mafra_21_09_2026.ogg` → responder às perguntas (se houver) por texto → pedir alteração **gravando** um áudio curto → aprovar. | Etapas visíveis; conferência com cabeçalho, 5 seções e PDF "em conferência"; após o áudio, "Alteração aplicada. Confira a versão 2." + texto entendido; aprovação pede confirmação citando a versão; histórico mostra "Aula de {hoje}" com PDF oficial. |
| V4 | Mesmo fluxo pelo Telegram (US1) | Enviar "Olavo" → enviar `Aula Olavo Torrente_24_09_2026.ogg` como arquivo → "Confirmar Olavo Torrente" → responder por **mensagem de voz** → pedir alteração por texto → "Aprovar relatório" → "Sim, aprovar a versão N". | Mesmo ciclo de estados; PDF recebido como documento; áudio na conferência aplicado direto como alteração (sem confirmar aluno); seleção de aluno limpa após aprovar. |
| V5 | Vídeo grande pelo Telegram (FR-015) | Enviar `Aula Miguel de Castro Torrente_20_09_26.mp4` (31 MB) como arquivo. | Aceito (acima de 20 MB, graças ao servidor local); só o áudio é usado; nenhum arquivo sobra em `tmpfs` nem no volume do `telegram-bot-api` depois da transcrição (V12). |
| V6 | Retomada entre canais (US3) | Iniciar um registro pelo Telegram e parar na conferência; na Web, escolher o mesmo aluno. Repetir no sentido inverso e escolher "descartar". | Aviso "Você tem um relatório em aberto" com canal de origem e data e hora; "continuar" leva à conferência na versão vigente; "descartar" cancela e pede novo áudio. |
| V7 | Versão desatualizada (FR-033) | Com a conferência aberta na Web (v1), pedir uma alteração pelo Telegram (v2); aprovar na Web. | Web recusa com "Esta versão está desatualizada…" e mostra a v2. |
| V8 | Semestral (US4) | Com aulas aprovadas de Matteo no semestre corrente, gerar o semestral pela Web; para Olavo, pelo Telegram; deixar um relatório de aula em aberto antes de gerar. | Aviso "Há 1 relatório(s) de aula em aberto…" com "Gerar mesmo assim"/"Voltar"; conferência com "N relatório(s) de aula considerado(s)…", 10 seções e o aviso repetido; Informações Gerais preenchidas pelo sistema; gerar de novo após aprovar → "já foi aprovado". |
| V9 | Semestral sem aulas (FR-042) | Pedir semestral de um período sem aulas aprovadas. | "Nenhum relatório de aula aprovado neste período…"; nada criado; nenhuma chamada à IA (`chamada_ia` inalterada). |
| V10 | Histórico (US5) | Consultar o histórico de Matteo nos dois canais; abrir ou receber um PDF. | Listas idênticas (ordem, rótulos, datas); PDF oficial com "Aprovado por Clara em …". |
| V11 | Acesso cruzado (SC-008) | Com a sessão de Clara, chamar `GET /api/v1/alunos/{aluno de Bruno}/historico` e `GET /api/v1/relatorios-aula/{relatório de Bruno}/pdf-oficial`; no Telegram, digitar o nome do aluno de Bruno. | 403 `ALUNO_NAO_ASSOCIADO`; 404 `RECURSO_NAO_ENCONTRADO`; bot responde "Nenhum aluno encontrado…", sem revelar dados. |
| V12 | Não retenção de áudio (FR-018, SC-008) | Depois de V3 a V5, listar `/tmp/audios` no backend e o diretório do `telegram-bot-api`; consultar o banco. | Diretórios vazios; nenhuma coluna ou tabela com bytes de áudio; logs sem transcrição, nome de aluno nem CPF. |
| V13 | Áudio inválido | Enviar um arquivo de áudio silencioso e um `.txt` renomeado para `.ogg`. | "Não conseguimos entender este áudio…"; nada criado (nenhuma `aula` nova). |
| V14 | Aviso entre canais (FR-021) | Com o Telegram vinculado, registrar uma aula pela Web e fechar o navegador. | O bot envia "O relatório de {aluno} está pronto para conferência." com "Continuar no chat". |
| V15 | Encerrar acesso | "Encerrar sessão" na Web; "sair" no Telegram. | A sessão deixa de valer no servidor (requisição seguinte → 401); o bot pede o código de novo. |
| V16 | Acessibilidade e responsividade (SC-011) | Percorrer as telas 01 a 12 a 390 px e 1440 px nos dois temas. | Layout igual aos protótipos; sem rolagem horizontal; navegação completa por teclado. |

## 7. Implantação (VPS)

Resumo (roteiro completo em `infra/README.md`, entregue na implementação): configurar DNS →
`infra/.env` de produção na VPS → `infra/scripts/implantar.sh` (build e `up -d` com
`compose.yaml` + `compose.prod.yaml`) → conferir que só as portas 80 e 443 estão abertas
(`ss -tlnp`) → definir a foto do bot como o monograma FB pelo @BotFather (a API de bots não
permite definir a foto de perfil do bot).
