# Implementation Plan: Fluxo de envio e criação de relatórios pedagógicos (Web e Telegram)

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

**Branch**: `001-fluxo-relatorios` | **Date**: 2026-09-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-fluxo-relatorios/spec.md`

## Summary

> **Nota (feature 002, sem reescrever histórico):** o transporte do Telegram agora é feito via n8n
> (constituição v1.4.0); ver `specs/002-api-interna-canal-telegram/research.md` R-01. O resumo abaixo
> registra o plano original da 001.

O professor registra uma aula por áudio (gravação no navegador, arquivo, mensagem de voz ou vídeo
de até 50 MB) pela **Web ou pelo Telegram, com paridade funcional**. O sistema transcreve, estrutura
o relatório em 5 seções com IA e faz até 2 rodadas de perguntas, que podem ser respondidas por texto
ou áudio. Depois o professor confere o texto e o PDF, pede alterações por texto ou áudio (cada uma
gera nova versão com PDF próprio) e aprova explicitamente a versão vigente. Com a aprovação, o
relatório entra no histórico com um PDF oficial. O mesmo ciclo vale para o relatório semestral (10
seções), que consolida só as aulas aprovadas do próprio professor. Acesso por código (7 dias
renováveis pelo uso), bloqueio por tentativas, autorização por associação professor–aluno e carga
inicial dos cadastros completam a feature.

**Abordagem técnica** (detalhes em [research.md](./research.md)): backend Java 25 + Spring Boot 4.1
em arquitetura de portas e adaptadores, com o **núcleo em módulo Maven próprio sem framework**. A
Web e o Telegram são dois adaptadores de entrada que chamam os mesmos casos de uso. O Telegram entra
por webhook direto no backend, via **servidor local da API de bots** (arquivos de até 50 MB), sem
orquestrador. O processamento é assíncrono numa **fila persistida no PostgreSQL**, com etapas
consultáveis pelos dois canais. O FFmpeg normaliza o áudio num `tmpfs`, que é apagado depois da
transcrição. Provedores de transcrição e LLM compatíveis com a API da OpenAI (Groq por padrão)
ficam atrás de portas. O PDF é gerado de HTML/CSS com a identidade visual (OpenHTMLtoPDF). O
frontend é React 19 + Vite fiel aos protótipos. Tudo roda em contêineres (Caddy, backend, Postgres
18, telegram-bot-api), localmente e na VPS.

## Technical Context

**Language/Version**: Java 25 (LTS) no backend; TypeScript 5.x + React 19 no frontend

**Primary Dependencies**:
- Backend: Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway,
  OpenHTMLtoPDF + Thymeleaf (PDF), Jackson + networknt json-schema-validator (saída da IA), FFmpeg
  (binário na imagem), Micrometer, Logback JSON
- Frontend: Vite, React Router, TanStack Query, openapi-typescript (tipos a partir do contrato)
- Canal Telegram: servidor oficial `tdlib/telegram-bot-api` em modo `--local` (contêiner)
- Provedores (por configuração): transcrição e LLM compatíveis com a API da OpenAI; padrão Groq
  (`whisper-large-v3-turbo`, `llama-3.3-70b-versatile`)

**Storage**: PostgreSQL 18 (fonte única da verdade, inclusive para estado da conversa, fila de
trabalhos e tentativas de acesso); volume de arquivos `documentos` para os PDFs; `tmpfs` para o
áudio durante o processamento (nunca persistido)

**Testing**: JUnit 5 + AssertJ + dublês à mão (núcleo, JaCoCo ≥ 90% de linhas e branches); ArchUnit;
Spring Boot Test + WireMock (integração, IA e API de bots simuladas); swagger-request-validator
(contrato da API); testes de conversa do Telegram; roteiro de paridade Web × Telegram; Vitest +
Testing Library; Playwright + axe-core (e2e e WCAG AA). Tudo via `infra/compose.test.yaml`.

**Target Platform**: contêineres Linux (Docker Compose), local e em produção numa VPS;
portal em navegadores modernos de celular (a partir de 390 px) e de computador; Telegram em chat
privado

**Project Type**: aplicação web (backend API + SPA) com um segundo canal de entrada (bot do
Telegram) no mesmo backend

**Performance Goals**: ≤ 2 min do envio à conferência em 90% dos áudios de até 10 min (SC-001);
semestral ≤ 3 min para até 30 aulas (SC-010); alteração ou resposta por áudio de até 2 min ≤ 90 s
(SC-013); confirmações e erros do bot ≤ 5 s (SC-007); polling da Web a cada 2 s

**Constraints**: áudio nunca retido (FR-018); limite de 50 MB igual nos dois canais (FR-015); data
da aula no fuso America/Sao_Paulo; nenhum dado identificador do aluno enviado à IA (FR-023,
FR-044); só HTTPS exposto (Princípio IV); WCAG 2.1 AA em claro e escuro (FR-052); mensagens
idênticas nos dois canais (FR-003); código em português sem acentos (Princípio VI)

**Scale/Scope**: uma escola, dezenas de professores, centenas de alunos, alguns milhares de
relatórios por ano; 12 telas do portal (mais variações de estado), cerca de 20 estados de conversa
no bot; uma VPS

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição v1.3.0. Avaliação **antes** da pesquisa e **refeita depois** do design (Fase 1):

| Princípio / Restrição | Como o plano atende | Pré | Pós |
|---|---|---|---|
| **I. Domínio independente de canal e fornecedor** | Módulo `nucleo` (domínio → casos de uso → portas) sem Spring nem SDK, com dependência imposta pelo Maven; ArchUnit para camadas e para proibir nomes de fornecedor. Web (`*Controlador` REST) e Telegram (`TelegramWebhookControlador` + `InterpretadorConversaTelegram`) só traduzem para os **mesmos** casos de uso. Paridade: tabela da spec coberta por [api-portal.openapi.yaml](./contracts/api-portal.openapi.yaml) × [telegram-conversa.md](./contracts/telegram-conversa.md), verificada por roteiro de paridade. Provedores atrás de `TranscricaoPorta`, `LlmPorta`, `GeracaoPdfPorta`, `ArmazenamentoDocumentoPorta`, `NotificacaoProfessorPorta`. | ✅ | ✅ |
| **II. Fonte única da verdade** | Sem orquestrador *(nota da 002: o n8n passa a ser admitido, só como transporte do Telegram; ver 002 R-01)*: só o backend escreve no banco. Estado da conversa em `vinculo_telegram` (FR-036); reiniciar o contêiner não perde nada. O `from.id` do Telegram só identifica; a autorização é decidida nos casos de uso. | ✅ | ✅ |
| **III. Humano no controle da IA** | Aprovação exige `versaoConfirmada = versao_vigente` e confirmação no canal; `VersaoRelatorio` imutável; PDF por versão; o áudio de alteração **nunca** aprova nem cancela (`pedidoDeAcao` só gera mensagem); prompts "não inventar" + schema; Informações Gerais do semestral preenchidas pelo sistema. | ✅ | ✅ |
| **IV. Privacidade desde o design** | `EntradaTarefa` tipada impede enviar nome, CPF ou data de nascimento à IA; áudio só no `tmpfs` e apagado no `finally`; no Telegram, só o `file_id` fica guardado antes da confirmação; CPF com AES-256-GCM + HMAC; código de acesso e token de sessão guardados só como hash; segredos em `.env`; logs JSON sem dados sensíveis; autorização por associação em todo caso de uso; só o Caddy (HTTPS) exposto; `telegram-bot-api`, `postgres` e `backend` na rede interna. | ✅ | ✅ |
| **V. Qualidade obrigatória** | JaCoCo ≥ 90% de linha e branch no `nucleo`, bloqueando o build e o CI; testes de contrato da API e da conversa; dublês para os provedores; suíte de IA real fora da cobertura. | ✅ | ✅ |
| **VI. Nomenclatura e linguagem ubíqua** | Pacotes e tipos em português sem acento com sufixos (`RegistrarAulaPorAudioCasoDeUso`, `RelatorioAulaRepositorio`, `LlmPorta`, `LlmOpenAiCompativelAdaptador`, `RelatorioAulaMapeador`, `RelatorioAulaDto`); tabelas e rotas em português; ArchUnit verifica sufixos por camada. Exceções só as permitidas (anotações e APIs de framework, siglas). | ✅ | ✅ |
| **VII. Simplicidade e foco** | Sem broker (fila no Postgres), sem orquestrador, sem armazenamento de objetos, PDF em processo, polling em vez de WebSocket. Os acréscimos exigidos pela spec estão justificados em "Complexity Tracking". Itens de roadmap (secretaria, WhatsApp, envio às famílias) ficam fora. | ✅ | ✅ |
| **VIII. Fluxo de trabalho** | Branch dedicada `001-fluxo-relatorios`; commit e PR para a `main` ao concluir cada etapa, sem merge pelo autor nem auto-merge; CI (GitHub Actions) e definições de contêiner e implantação versionados em `.github/` e `infra/`. | ✅ | ✅ |
| **IX. Identidade visual e acessibilidade** | Tokens, tipografia e telas dos protótipos; PDF segundo o `Modelo PDF.dc.html`; bot com o monograma e a terminologia do portal; WCAG AA verificado por axe a 390 e 1440 px nos dois temas. | ✅ | ✅ |
| **Restrições: stack, provedores, contêineres, VPS genérica, MVP só leitura** | Stack registrada e justificada (R-01 a R-03); IA por configuração (R-08); 4 serviços em contêiner, um comando local, mesmas imagens, testes em contêiner, implantação na VPS (R-15); MVP usado só como referência. | ✅ | ✅ |
| **Quality gate: paridade** | Nenhuma funcionalidade fica restrita a um canal; nenhum desvio a justificar. | ✅ | ✅ |

**Resultado**: aprovado antes e depois do desenho, sem violações. A pendência registrada no Sync
Impact Report da constituição (spec restringindo semestral e histórico à Web) já foi resolvida na
revisão da spec de 2026-09-30.

## Project Structure

### Documentation (this feature)

```text
specs/001-fluxo-relatorios/
├── plan.md                  # Este arquivo
├── research.md              # Fase 0: decisões R-01 a R-18
├── data-model.md            # Fase 1: entidades, estados, índices
├── quickstart.md            # Fase 1: subir, testar e validar V1 a V16
├── contracts/
│   ├── api-portal.openapi.yaml   # API REST do portal (contrato testado)
│   ├── telegram-conversa.md      # Transporte, comandos, botões, estados da conversa
│   ├── mensagens.md              # Catálogo único de mensagens (os dois canais)
│   ├── portas-provedores.md      # Portas de saída + schemas de saída da IA + prompts
│   ├── carga-inicial.md          # Procedimento e formato da carga
│   └── rotas-portal.md           # Rotas ↔ telas 01–12 e regras de cliente
├── checklists/requirements.md
└── tasks.md                 # Fase 2 (/speckit-tasks; não criado por este comando)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                   # parent (Java 25, Spring Boot 4.1 BOM, JaCoCo, Spotless)
├── nucleo/                                   # Java puro — SEM Spring (regra de dependência pelo Maven)
│   ├── pom.xml
│   └── src/
│       ├── main/java/br/com/felipeblaft/relatorios/
│       │   ├── dominio/                      # Professor, Aluno, Aula, RelatorioAula, RelatorioSemestral,
│       │   │                                 # VersaoRelatorio, InteracaoRelatorio, ConversaTelegram,
│       │   │                                 # CodigoAcesso, PeriodoSemestral, BuscaAluno, erros de domínio
│       │   ├── casodeuso/                    # um *CasoDeUso por ação (acesso, inicio, aula, perguntas,
│       │   │                                 # conferencia, semestral, historico, documentos, carga)
│       │   ├── servico/                      # *Servico de apoio (ProcessadorTrabalhoServico, AutorizacaoServico)
│       │   └── porta/                        # *Porta e *Repositorio (interfaces)
│       └── test/java/…                       # unitários (≥ 90%) + ArchUnit
└── aplicacao/                                # Spring Boot: adaptadores + configuração
    ├── pom.xml
    ├── Dockerfile                            # Temurin 25 JRE + FFmpeg
    └── src/
        ├── main/java/br/com/felipeblaft/relatorios/
        │   ├── entrada/web/                  # *Controlador REST, *Dto/*Requisicao/*Resposta, *Mapeador,
        │   │                                 # segurança (sessão por cookie, CSRF), tratamento de erros
        │   ├── entrada/telegram/             # TelegramWebhookControlador, InterpretadorConversaTelegram,
        │   │                                 # ApresentadorTelegram (mensagens + teclados)
        │   ├── entrada/carga/                # comandos `carga` e `emitir-codigo`
        │   ├── saida/persistencia/           # entidades JPA, *RepositorioAdaptador, *Mapeador
        │   ├── saida/ia/                     # TranscricaoOpenAiCompativelAdaptador, LlmOpenAiCompativelAdaptador
        │   ├── saida/audio/                  # FfmpegNormalizacaoAudioAdaptador, ArquivoTelegramAdaptador
        │   ├── saida/pdf/                    # OpenHtmlToPdfGeracaoPdfAdaptador
        │   ├── saida/armazenamento/          # SistemaArquivosArmazenamentoDocumentoAdaptador
        │   ├── saida/telegram/               # MensageriaTelegramAdaptador, NotificacaoTelegramAdaptador
        │   ├── saida/fila/                   # FilaTrabalhoPostgresAdaptador + workers
        │   └── configuracao/                 # wiring dos casos de uso, propriedades, relógio
        ├── main/resources/
        │   ├── db/migration/                 # Flyway V1__…
        │   ├── prompts/                      # *.v1.txt versionados
        │   ├── pdf/                          # templates Thymeleaf + fontes + logos
        │   └── mensagens/mensagens_pt_BR.properties
        └── test/java/…                       # integração, contrato (OpenAPI), conversa Telegram, paridade

frontend/
├── package.json
├── Dockerfile                                # build Node → estático servido pelo Caddy (imagem `web`)
├── src/
│   ├── app/                                  # rotas, guarda de sessão, tema
│   ├── paginas/                              # TelaEntrar, TelaPainel, TelaAlunos, TelaRelatorioEmAberto,
│   │                                         # TelaGravacao, TelaProcessando, TelaPerguntas, TelaConferencia,
│   │                                         # TelaAprovado, TelaSemestral, TelaConferenciaSemestral, TelaHistorico
│   ├── componentes/                          # GravadorAudio, EtapasProcessamento, SecoesRelatorio,
│   │                                         # VisualizadorPdf, DialogoConfirmacao, CartaoAluno, Cabecalho…
│   ├── servicos/                             # cliente da API (tipos gerados), polling, PDF autenticado
│   ├── estilos/                              # tokens (claro/escuro), tipografia, fontes locais
│   └── mensagens/                            # mensagens.json gerado do catálogo
└── tests/                                    # Vitest + Playwright/axe (e2e)

infra/
├── compose.yaml                              # web, backend, postgres, telegram-bot-api
├── compose.dev.yaml                          # portas em loopback, certificado local
├── compose.test.yaml                         # testes-backend, testes-frontend, testes-e2e, testes-ia-real
├── compose.prod.yaml                         # VPS: só 80/443 no `web`
├── caddy/Caddyfile
├── carga/exemplo.json                        # fictício (demais arquivos de carga no .gitignore)
├── scripts/implantar.sh
├── .env.example
└── README.md

.github/workflows/ci.yml                      # testes em contêiner + cobertura como gate
```

**Structure Decision**: aplicação web com dois projetos (`backend/`, `frontend/`) e a
infraestrutura em `infra/`. O backend fica dividido em `nucleo`, sem framework, e `aplicacao`
(Spring Boot com todos os adaptadores). O canal Telegram é um adaptador de entrada **dentro** de
`aplicacao`, e não um serviço separado. Assim, os dois canais chamam os mesmos casos de uso no mesmo
processo (Princípio I) e nenhum outro componente escreve no banco (Princípio II).

> **Nota (feature 002):** o transporte do Telegram agora é feito via n8n; ver
> `specs/002-api-interna-canal-telegram/research.md` R-01. O adaptador de entrada continua dentro de
> `aplicacao`; o n8n só transporta.

## Fases seguintes

- **Fase 2** (`/speckit-tasks`): gerar `tasks.md` a partir deste plano, nesta ordem sugerida de
  entrega: (1) fundação (repositório, contêineres, CI, núcleo e ArchUnit, migrações, carga inicial);
  (2) US2 acesso e início nos dois canais; (3) US1 registro → conferência → aprovação nos dois
  canais, com o roteiro de paridade; (4) US3 retomada; (5) US4 semestral; (6) US5 histórico e PDFs
  oficiais; (7) acessibilidade, desempenho (SC-001, SC-010, SC-013) e implantação.
- Os PDFs e as telas devem ser revisados lado a lado com os protótipos antes do PR de cada história
  (Princípio IX).

## Complexity Tracking

Não há violação da constituição. A tabela registra os acréscimos de complexidade que o Princípio VII
manda justificar no plano:

| Acréscimo | Por que é necessário | Alternativa mais simples rejeitada porque |
|---|---|---|
| Contêiner `telegram-bot-api` (servidor local da API de bots) | FR-015 e a clarificação da spec: limite de 50 MB igual nos dois canais; a API da nuvem só entrega arquivos de até 20 MB | limite de 20 MB no Telegram quebraria a paridade e foi recusado pelo dono do produto |
| Fila de trabalhos persistida (`trabalho_processamento`) | FR-021, etapas consultáveis nos dois canais; o processamento continua se a conexão cair; aviso aos 3 minutos; resultado no canal de origem | processar dentro da requisição (MVP) sofre timeouts e perde o trabalho se a conexão cair; `@Async` em memória perde o trabalho num reinício |
| FFmpeg na imagem do backend | vídeos `.mp4` do cliente (até 31 MB) e o limite de 25 MB dos provedores de transcrição; normalização resolve formatos (`oga`) | enviar o arquivo original falha com vídeos grandes e contêineres incomuns |
| Dois módulos Maven no backend | impor a regra de dependência (Princípio I, bloqueante) no compilador | módulo único deixaria a regra só em teste de arquitetura |
| Catálogo de mensagens compartilhado (backend → JSON do frontend) | FR-003, mesmo conteúdo nos dois canais | textos duplicados por canal divergem |
| Massa de demonstração do ambiente local (`entrada/carga/MassaDemonstracaoComando`, `infra/carga/demonstracao.json`, `local.sh subir --massa=true`) | não consta das tarefas originais, mas é o que permite conferir as telas e os PDFs lado a lado com os protótipos (Princípio IX) e exercitar histórico, semestral e relatórios em aberto sem gravar áudio; usa os mesmos objetos de domínio e o mesmo gerador de PDF do sistema, sem caminho paralelo de regra. Só tem dados fictícios (nomes, CPFs e responsáveis inventados), roda apenas no perfil `carga`, só com o banco vazio, e nada na implantação (`compose.prod.yaml`, `implantar.sh`, CI) a invoca. Decidido em 08/10/2026 (T202) | cadastrar tudo à mão a cada ambiente zerado atrasa a revisão visual e as validações manuais, e a carga inicial (FR-005) não cria relatórios nem PDFs |
