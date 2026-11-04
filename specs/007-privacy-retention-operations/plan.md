# Implementation Plan: Privacidade, retenção e operação (LGPD)

**Branch**: `feat/007-privacy-retention-operations` | **Date**: 2026-11-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-privacy-retention-operations/spec.md` (constituição **v2.2.0**;
`/speckit-clarify` de 2026-11-03 fechou D6, D7, D8, D9, backup — fora — e destino dos alertas)

## Summary

Transformar a política de dados em comportamento verificável, sem mudar o ciclo de vida dos relatórios nem os
provedores de IA: **trilha de auditoria** única, só de acréscimo e com detecção de alteração; **consentimento** do
responsável obrigatório no cadastro do aluno menor, com bloqueio de IA como garantia; **retenção** automática diária;
**eliminação a pedido** pelo administrador; **custo e tempo** por unidade sobre a medição que já existe; **alertas** num
painel da Web; e o **inventário de tratamento**. Administrador somente na Web; professor com paridade Web ≡ Telegram.

**Abordagem técnica** (decisões em [research.md](./research.md)):

1. **Retenção** (R-01): `RetentionTask` (`@Scheduled`, diária) só chama `RunRetentionUseCase` no núcleo; a regra
   (prazos, proteção do aprovado vigente, reavaliação por item, idempotência) está no domínio; os dados são lidos e
   alterados por métodos novos nas portas **existentes** (`LessonReportRepository`, `SemesterReportRepository`,
   `ReportVersionRepository`, `PdfDocumentRepository`, `ReportInteractionRepository`, `DocumentStoragePort.delete`).
2. **Trilha** (R-02, R-03): a `authorship_record` da 005/006 vira a trilha única; ganha operações e alvos novos, as
   leituras do professor, cadeia de resumos SHA-256 calculada no núcleo, guarda de 5 anos só por função do banco com
   piso fixo, e um papel de banco sem posse (`reports_app`) para o pool da aplicação.
3. **Consentimento** (R-04): `ConsentInput` nos comandos de cadastro e edição da 005 (mesmos casos de uso nos três
   pontos de entrada); `ConsentGuardService` em duas barreiras antes de qualquer porta de IA; carga `consent-load`.
4. **Eliminação** (R-05): registrar → prévia com token de 10 min → confirmar; uma transação para o banco, remoção dos
   arquivos depois, com repetição segura; lista `erased_student` para a restauração futura.
5. **Métricas** (R-06): `ai_call` ganha `channel`; preço com vigência e custo fixo em configuração; agregação mensal no
   banco por `CostReportQueryPort`.
6. **Alertas** (R-07, R-08): tabela `operational_alert` avaliada a cada 60 s no backend; painel e indicador na Web.
   **Nenhuma ferramenta de observabilidade nova**; só rotação de logs do Docker e `correlationId` no MDC.
7. **Inventário** (R-12): `privacy/processing-inventory.md` + item no modelo de PR e no `privacy-auditor`.

### Fatias de entrega (para o `/speckit-tasks`)

| Fatia | História | Depende de | Entrega |
|---|---|---|---|
| S0 | fundação | — | `V17` (papel `reports_app`, colunas novas de trilha), `db-roles.sh`, `RetentionPolicy`/`AlertThresholds`, `correlationId`, rotação de logs, glossário |
| S1 | US1 trilha (P1) | S0 | cadeia, eventos novos (inclusive leituras do professor), consulta A14, guarda |
| S2 | US2 consentimento (P1) | S1 | termos, consentimento no cadastro (3 entradas), guarda de IA, pendências, `consent-load` |
| S3 | US3 retenção (P2) | S1 | `RunRetentionUseCase`, `RetentionTask`, `DocumentStoragePort.delete` |
| S4 | US4 eliminação (P2) | S1, S3 | registro, prévia, confirmação, comprovante |
| S5 | US5 custo e alertas (P3) | S0 | `ai_call.channel`, preços, painel A17, alertas A18 e indicador |
| S6 | US6 inventário (P3) | — | `privacy/`, modelo de PR, `privacy-auditor` |

## Technical Context

**Language/Version**: Java 25 (backend, Spring Boot 4, multi-módulo `core`/`application`); TypeScript 5 + React 19
(frontend, Vite).

**Primary Dependencies**: as existentes — Spring Web/Security/Data JPA, Flyway, `logstash-logback-encoder`, ArchUnit;
React Router e o gerador de tipos OpenAPI do frontend. **Nenhuma dependência nova** (SHA-256 pelo `MessageDigest` do JDK;
agendamento pelo `@Scheduled` já usado).

**Storage**: PostgreSQL 18 (migração `V17__privacy_retention_operations.sql`, aditiva; `V1`–`V16` imutáveis) e o
armazenamento de PDFs atrás de `DocumentStoragePort` (volume do contêiner).

**Testing**: JUnit 5 + dublês no núcleo (≥ 90% linha e branch, JaCoCo); testes de integração em contêiner
(`sh infra/scripts/local.sh tests backend`); testes de contrato da API nova; paridade Web ≡ Telegram (`parity/`);
privacidade (`privacy/`, canário); ArchUnit; Vitest e Playwright + axe no frontend (`tests frontend`, `tests e2e`).

**Target Platform**: contêineres Linux (Docker Engine 27+, Compose v2) no ambiente local, no CI e numa VPS Linux
genérica; só o Caddy (HTTPS) exposto.

**Project Type**: aplicação web (API Java + SPA React) com canal Telegram via n8n (só transporte).

**Performance Goals**: alerta visível em ≤ 5 min (SC-006; desenho dá ~2 min); retenção diária concluída em minutos
para o volume da escola (milhares de relatórios); consulta da trilha paginada em < 1 s para 90 dias.

**Constraints**: nada de dado pessoal em log, métrica, alerta ou trilha (SC-001); operação + evento na mesma transação
(FR-044); nenhum contêiner novo; nenhuma porta publicada nova; segredos (`DB_APP_PASSWORD`) só por ambiente.

**Scale/Scope**: uma escola — dezenas de professores, centenas de alunos, milhares de relatórios por ano, dezenas de
eventos de trilha por minuto no pico; ~5 telas novas na área administrativa e passos novos no cadastro do professor.

## Constitution Check

*GATE: avaliado antes da Fase 0 e reavaliado depois da Fase 1. Base: constituição v2.2.0. Pedido do usuário: II, IV, V,
VII e as restrições de contêiner e de VPS genérica em destaque; os demais princípios também conferidos.*

| Princípio / restrição | Como o plano cumpre | Situação |
|---|---|---|
| **I. Domínio independente** | Toda regra nova no núcleo (`domain/privacy`, `usecase/privacy`, `usecase/audit`, `usecase/operations`): prazos, proteção do aprovado, consentimento, alcance da eliminação, limites de alerta e cadeia de resumos. `*Task` e controllers só chamam casos de uso. Funções do administrador só na Web (`teacherTelegram`/Telegram = `DENY`; ArchUnit proíbe referência a esses casos de uso em `inbound/telegram`). Consentimento do professor pelos mesmos casos de uso na Web e no Telegram. | ✅ |
| **II. Fonte única da verdade** | Só o backend grava: retenção, eliminação, consentimento, alertas e trilha. O n8n não muda (nenhum workflow novo; nenhum agendamento nele). O agendamento é do backend, não do host nem do n8n (R-01). Autorização na `PermissionMatrix`. | ✅ |
| **III. Humano no controle** | A retenção nunca toca a versão aprovada vigente (FR-011, reavaliação por item, teste SC-003); versões não aprovadas mantêm o texto (FR-012). A eliminação apaga conteúdo por direito do titular (D7, decisão do dono do produto) mas não cria nem altera versão nem aprovação. | ✅ |
| **IV. Privacidade desde o design** | É o objeto da feature. Trilha, métricas e alertas só com ids e códigos (CHECK no banco + teste canário SC-001). Nada novo vai a provedor de IA; o bloqueio impede chamada sem consentimento (SC-005). Segredo novo (`DB_APP_PASSWORD`) só em ambiente. Nenhum serviço novo exposto; painel atrás da sessão administrativa. Logs JSON com `correlationId`, sem dado pessoal. | ✅ |
| **V. Qualidade obrigatória** | Testes antes do código por fatia; núcleo ≥ 90%; contrato da API nova; ArchUnit para guarda de IA, porta de guarda da trilha e canais; integração para retenção, eliminação, adulteração da trilha e alertas; cada SC com teste nomeado (research R-13). | ✅ |
| **VI. Nomenclatura e idioma** | Identificadores em inglês (`consent`, `erasure`, `retention`, `audit`, `alert`, `operational`, `inventory`), entrando no `glossary.json` antes do uso; sufixos padronizados; textos, logs e documentos em português. Palavra de confirmação `ELIMINAR` é texto do usuário (exceção já prevista). | ✅ |
| **VII. Simplicidade e foco** | **Nenhuma ferramenta de observabilidade nova** (R-08, com justificativa); nenhuma dependência nova; agregação no banco em vez de tabela de agregados; comprovante como tela imprimível; reaproveita a tabela de autoria. Complexidade adicionada e justificada: cadeia de resumos e papel de banco separado (R-03) — exigidos por "alteração indevida DEVE ser detectável" e "proteção contra alteração". | ✅ |
| **VIII. Fluxo de trabalho** | Branch própria a partir da `main`; PR sem merge pelo autor; `code-review-graph build` depois do PR. | ✅ |
| **IX. Identidade e acessibilidade** | Telas novas com tokens do design system, mobile-first a partir de 390 px, WCAG 2.1 AA (axe no e2e), alerta com texto e ícone (não só cor). | ✅ |
| **Contêineres** | Nada fora de contêiner: a tarefa agendada roda no contêiner do backend; o papel de banco é criado por migração e o `LOGIN` por script que usa `docker compose exec` no contêiner do Postgres; testes em `compose.test.yaml`. Mesma imagem local e produção; só ambiente muda. | ✅ |
| **VPS genérica** | Só Docker Engine e Compose; rotação de logs pelo driver `json-file` (recurso do Engine); nenhum serviço, painel, snapshot ou DNS de provedor; nenhuma porta publicada nova (o painel é rota da API atrás do Caddy e da sessão). | ✅ |

**Reavaliação pós-Fase 1**: o modelo de dados, os contratos e o quickstart não trouxeram desvio. A única mudança
operacional (segunda credencial de banco) é por variável de ambiente e script versionado; está registrada no quickstart
e não exige recurso de provedor. Sem itens obrigatórios em Complexity Tracking; as duas escolhas de maior custo ficam
registradas abaixo por transparência.

## Project Structure

### Documentation (this feature)

```text
specs/007-privacy-retention-operations/
├── spec.md
├── plan.md              # este arquivo
├── research.md          # R-01 a R-13
├── data-model.md        # tabelas, entidades, estados, migração V17
├── quickstart.md        # roteiro de validação por história
├── contracts/
│   ├── api-privacy-operations.openapi.yaml   # /api/v1/admin/{audit,erasures,consents,...}
│   ├── audit-events.md                       # lista fechada de eventos (FR-041, SC-002)
│   ├── permission-matrix-additions.md        # operações novas (FR-080, FR-081)
│   ├── telegram-consent.md                   # passos do consentimento no cadastro guiado e bloqueio
│   ├── consent-load.md                       # comando de carga da implantação (FR-036)
│   ├── configuration.md                      # variáveis de ambiente, padrões e validação
│   └── portal-screens.md                     # telas A14–A18 e mudanças no "Meus alunos"
├── checklists/
└── tasks.md             # /speckit-tasks (não criado aqui)
```

### Source Code (repository root)

```text
backend/core/src/main/java/br/com/felipeblaft/reports/
├── domain/privacy/      # RetentionPolicy, RetentionDecision, ConsentTermVersion, GuardianConsent, ConsentInput,
│                        # ConsentPolicy, ErasureRequest, ErasureScope, ErasedStudent
├── domain/audit/        # AuditChain (forma canônica + SHA-256), AuditTrailFilter, AuditEventType
├── domain/operations/   # OperationalAlert, AlertType, AlertThresholds, PriceTable, MonthlyCostReport
├── usecase/privacy/     # RunRetentionUseCase, Record/Revoke/ListPending Consent, PublishConsentTerm,
│                        # RegisterErasureRequest, PreviewErasure, ConfirmErasure, GetErasureReceipt
├── usecase/audit/       # QueryAuditTrailUseCase, VerifyAuditChainUseCase
├── usecase/operations/  # EvaluateOperationalAlertsUseCase, ListOperationalAlerts, GetMonthlyCostReport
├── service/             # ConsentGuardService, OperationalAlertService, AuthorshipService (eventos novos)
└── port/                # novos: AuditTrailQueryPort, AuditTrailRetentionPort, ConsentRepository,
                         # ConsentTermRepository, ErasureRequestRepository, RetentionRunRepository,
                         # OperationalAlertRepository, OperationalSignalQueryPort, CostReportQueryPort;
                         # métodos novos nas portas existentes (DocumentStoragePort.delete, find*RetentionCandidates…)

backend/application/src/main/java/br/com/felipeblaft/reports/
├── inbound/web/admin/   # AdminAuditController, AdminErasureController, AdminConsentController,
│                        # AdminOperationsController (custos e alertas)
├── inbound/web/         # MyStudentController: ConsentInput no cadastro/edição; mensagem CONSENT_REQUIRED
├── inbound/telegram/    # TelegramStudentRegistryFlow: passos do consentimento; bloqueio
├── inbound/seed/        # ConsentLoadCommand (perfil seed)
├── outbound/persistence/# adapters novos + RetentionTask, OperationalAlertTask
├── outbound/storage/    # delete(key)
├── outbound/ai/         # preço vigente por data (PriceTable)
└── config/              # propriedades reports.retention/alerts/costs/privacy; correlationId no MDC
backend/application/src/main/resources/
├── db/migration/V17__privacy_retention_operations.sql
├── messages/messages_pt_BR.properties  # textos novos
└── logback-spring.xml                   # correlationId

frontend/src/
├── pages/admin/         # AuditTrailScreen, ErasureScreen, ErasureReceiptScreen, ConsentTermsScreen,
│                        # PendingConsentsScreen, CostsScreen, AlertsScreen; ConsentSection no aluno
├── pages/               # MyStudentFormScreen: seção de consentimento; aviso de bloqueio
├── components/          # AlertIndicator (cabeçalho administrativo)
└── services/            # tipos gerados de api-privacy-operations

infra/
├── compose.yaml, compose.prod.yaml, compose.test.yaml   # DB_APP_PASSWORD, flyway user, rotação de logs
└── scripts/lib/db-roles.sh                              # LOGIN/senha do reports_app (idempotente)

privacy/                 # NOVO (com CLAUDE.md): processing-inventory.md
.github/pull_request_template.md                          # NOVO: item do inventário (FR-071)
```

**Structure Decision**: aplicação web existente (`backend/` multi-módulo + `frontend/`), sem módulo novo. Uma pasta
nova de primeiro nível, `privacy/`, nasce com o seu `CLAUDE.md` e uma linha na tabela do `CLAUDE.md` da raiz (regra do
repositório). Também se atualizam `backend/core/CLAUDE.md` e `backend/application/CLAUDE.md` (pacotes e `V17`),
`infra/CLAUDE.md` (papel de banco, rotação), e a linha da 007 em `specs/CLAUDE.md` (backup saiu do escopo).

## Complexity Tracking

> Nenhuma violação da constituição. Registro, por transparência, das duas escolhas que acrescentam peças.

| Escolha | Por que é necessária | Alternativa mais simples rejeitada porque |
|---|---|---|
| Papel de banco separado (`reports_app`) + script `db-roles.sh` | Os gatilhos da V10 não protegem contra o próprio dono da tabela (que pode desligá-los); o pedido exige "proteção contra alteração" | Só gatilhos: qualquer injeção ou bug com o usuário dono desliga a proteção |
| Cadeia de resumos na trilha | FR-043: "alteração indevida DEVE ser detectável", inclusive por quem tem a senha de dono | `REVOKE` apenas: impede, mas não detecta o que foi feito com privilégio de dono |
