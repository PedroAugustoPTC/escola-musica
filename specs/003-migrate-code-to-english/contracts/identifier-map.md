# Contrato: Mapa de identificadores fora do OpenAPI e do JSON Schema

**Feature**: `003-migrate-code-to-english` | Complementa: [api-portal.openapi.yaml](./api-portal.openapi.yaml),
[telegram-channel-internal.openapi.yaml](./telegram-channel-internal.openapi.yaml),
[actions-v1.schema.json](./actions-v1.schema.json), [ai-lesson-report.schema.json](./ai-lesson-report.schema.json),
[ai-semester-report.schema.json](./ai-semester-report.schema.json) e [glossary.json](./glossary.json).

Aqui ficam os nomes que cruzam camadas e não aparecem nos contratos formais. Os textos em português
(mensagens, logs, PDFs, palavras digitadas pelo professor) **não mudam**. Cada tabela é o insumo da fatia
indicada e do verificador de renomeação (research.md R-02).

## 1. Códigos de erro e chaves do catálogo de mensagens (fatias 3 e 5)

O código de erro da API é a **mesma cadeia** que a chave do catálogo (`CodigoErro.chaveMensagem()`), por
isso renomeiam juntos. Regra para as demais chaves do catálogo (~230): palavra a palavra, pelo
[glossary.json](./glossary.json), em `SCREAMING_SNAKE_CASE`. Os valores (textos) ficam byte a byte iguais;
só os **marcadores** entre chaves também são identificadores e acompanham (ex.: `{minutos}` → `{minutes}`).

| Atual | Novo |
|---|---|
| `CODIGO_NECESSARIO` | `CODE_REQUIRED` |
| `CODIGO_INVALIDO` | `INVALID_CODE` |
| `CODIGO_EXPIRADO` | `EXPIRED_CODE` |
| `BLOQUEADO_TEMPORARIAMENTE` | `TEMPORARILY_BLOCKED` |
| `VINCULO_CRIADO` | `LINK_CREATED` |
| `TELEGRAM_JA_VINCULADO` | `TELEGRAM_ALREADY_LINKED` |
| `PROFESSOR_JA_VINCULADO` | `TEACHER_ALREADY_LINKED` |
| `SESSAO_EXPIRADA` | `SESSION_EXPIRED` |
| `NAO_AUTENTICADO` | `NOT_AUTHENTICATED` |
| `ALUNO_NAO_ASSOCIADO` | `STUDENT_NOT_ASSIGNED` |
| `RECURSO_NAO_ENCONTRADO` | `RESOURCE_NOT_FOUND` |
| `PROCESSAMENTO_EM_ANDAMENTO` | `PROCESSING_IN_PROGRESS` |
| `AUDIO_MUITO_GRANDE` | `AUDIO_TOO_LARGE` |
| `FORMATO_NAO_SUPORTADO` | `UNSUPPORTED_FORMAT` |
| `AUDIO_NAO_PROCESSAVEL` | `AUDIO_NOT_PROCESSABLE` |
| `RESPOSTA_VAZIA` | `EMPTY_ANSWER` |
| `ALTERACAO_VAZIA` | `EMPTY_CHANGE` |
| `VERSAO_DESATUALIZADA` | `STALE_VERSION` |
| `RELATORIO_JA_APROVADO` | `REPORT_ALREADY_APPROVED` |
| `RELATORIO_CANCELADO` / `RELATORIO_CANCELADO_ERRO` | `REPORT_CANCELLED` / `REPORT_CANCELLED_ERROR` |
| `ACAO_NAO_PERMITIDA_NA_ETAPA` | `ACTION_NOT_ALLOWED_AT_STEP` |
| `RELATORIO_EM_ABERTO` | `REPORT_ALREADY_OPEN` |
| `SEM_AULAS_APROVADAS_NO_PERIODO` | `NO_APPROVED_LESSONS_IN_PERIOD` |
| `HA_RELATORIOS_EM_ABERTO` | `OPEN_REPORTS_EXIST` |
| `SEMESTRAL_JA_APROVADO` | `SEMESTER_REPORT_ALREADY_APPROVED` |
| `EMAIL_INVALIDO` | `INVALID_EMAIL` |
| `EMAIL_DUPLICADO_NO_ARQUIVO` | `DUPLICATE_EMAIL_IN_FILE` |
| `NOME_OBRIGATORIO` | `NAME_REQUIRED` |
| `CPF_INVALIDO` | `INVALID_CPF` |
| `CPF_DUPLICADO_NO_ARQUIVO` | `DUPLICATE_CPF_IN_FILE` |
| `DATA_NASCIMENTO_INVALIDA` | `INVALID_BIRTH_DATE` |
| `RESPONSAVEL_OBRIGATORIO` | `GUARDIAN_REQUIRED` |
| `PROFESSOR_INEXISTENTE` | `TEACHER_NOT_FOUND` |
| `DADOS_INVALIDOS` | `INVALID_DATA` |
| `ERRO_INTERNO` | `INTERNAL_ERROR` |
| `SERVICO_INDISPONIVEL` | `SERVICE_UNAVAILABLE` |
| `GRAVACAO_INDISPONIVEL` | `RECORDING_UNAVAILABLE` |
| `PROCESSAMENTO_DEMORADO` | `PROCESSING_DELAYED` |
| `SEM_ALUNOS` | `NO_STUDENTS` |

## 2. Valores de enumeração persistidos e expostos (fatia 3, com a V7)

Estes valores estão no banco (`varchar`), nos DTOs, nos contratos e nos `CHECK`s/índices parciais. Valores
já listados nos OpenAPI/JSON Schema acima não se repetem aqui, exceto os que só existem no banco.

| Coluna / tipo | Atual → novo |
|---|---|
| `report_type` (todas as tabelas) | `AULA` → `LESSON`; `SEMESTRAL` → `SEMESTER` |
| `pdf_document.type` | `CONFERENCIA` → `REVIEW`; `OFICIAL` → `OFFICIAL` |
| `status` do relatório | `EM_PROCESSAMENTO` → `PROCESSING`; `FALHA_PROCESSAMENTO` → `PROCESSING_FAILED`; `AGUARDANDO_RESPOSTAS` → `AWAITING_ANSWERS`; `AGUARDANDO_CONFERENCIA` → `AWAITING_REVIEW`; `APROVADO` → `APPROVED`; `CANCELADO` → `CANCELLED` |
| `status` do trabalho | `PENDENTE` → `PENDING`; `EM_EXECUCAO` → `RUNNING`; `CONCLUIDO` → `COMPLETED`; `FALHOU` → `FAILED`; `DESCARTADO` → `DISCARDED` |
| `type` do trabalho | `REGISTRO_AULA` → `LESSON_REGISTRATION`; `NOVA_TENTATIVA` → `RETRY`; `RESPOSTA_PERGUNTAS` → `QUESTION_ANSWER`; `ALTERACAO` → `CHANGE`; `CONSOLIDACAO_SEMESTRAL` → `SEMESTER_CONSOLIDATION` |
| `step` / `processing_step` | `ENVIANDO` → `UPLOADING`; `TRANSCREVENDO` → `TRANSCRIBING`; `ESTRUTURANDO` → `STRUCTURING`; `INCORPORANDO_RESPOSTAS` → `INCORPORATING_ANSWERS`; `TRANSCREVENDO_PEDIDO` → `TRANSCRIBING_REQUEST`; `APLICANDO_ALTERACAO` → `APPLYING_CHANGE`; `CONSOLIDANDO` → `CONSOLIDATING`; `GERANDO_PDF` → `GENERATING_PDF`; `PRONTO` → `READY` |
| `medium` | `TEXTO` → `TEXT`; `AUDIO` não muda |
| `channel`, `origin_channel`, `approval_channel` | `WEB`, `TELEGRAM` não mudam |
| `flow` (`telegram_link`) | `NENHUM` → `NONE`; `REGISTRO_AULA` → `LESSON_REGISTRATION`; `SEMESTRAL` → `SEMESTER`; `HISTORICO` → `HISTORY` |
| `flow_step` | `NENHUMA` → `NONE`; `ESCOLHENDO_ALUNO` → `CHOOSING_STUDENT`; `AGUARDANDO_AUDIO` → `AWAITING_AUDIO`; `CONFIRMANDO_ALUNO_DO_AUDIO` → `CONFIRMING_AUDIO_STUDENT`; `AVISO_RELATORIO_EM_ABERTO` → `OPEN_REPORT_NOTICE`; `ESCOLHENDO_SEMESTRE` → `CHOOSING_SEMESTER`; `CONFIRMANDO_GERACAO_SEMESTRAL` → `CONFIRMING_SEMESTER_GENERATION`; `ESCOLHENDO_ITEM_HISTORICO` → `CHOOSING_HISTORY_ITEM` |
| `channel_action.state` | `PENDENTE` → `PENDING`; `ENTREGUE` → `DELIVERED`; `EXECUTADA` → `EXECUTED`; `FALHOU` → `FAILED`; `FALHA_DEFINITIVA` → `PERMANENTLY_FAILED`; `DESCARTADA` → `DISCARDED` |
| `channel_action.type` | `MENSAGEM` → `MESSAGE`; `EDICAO` → `EDIT`; `DOCUMENTO` → `DOCUMENT`; `RESPOSTA_CALLBACK` → `CALLBACK_REPLY`; `MENSAGEM_ETAPA` → `STEP_MESSAGE` |
| `channel_action.origin` | `RESPOSTA_UPDATE` → `UPDATE_REPLY`; `PROATIVA` → `PROACTIVE`; `COMPENSACAO` → `COMPENSATION` |
| `processed_telegram_update.origin` | `INTERNO` → `INTERNAL` |
| `processing_job.result_code` | os 9 do `JobDto.resultCode` (OpenAPI) e mais `FALHA_RESPOSTA` → `ANSWER_FAILED`, `FALHA_CONSOLIDACAO` → `CONSOLIDATION_FAILED`, `DESCARTADO` → `DISCARDED` |
| `lesson_report.failure_reason`, `ai_call.error_code`, `channel_action.reason_code` | códigos de erro da seção 1 e, no `reason_code`, os códigos do orquestrador (`MENSAGEM_NAO_EDITAVEL` → `MESSAGE_NOT_EDITABLE`, etc.; ver `ReasonCode` em actions-v1) |
| `ai_call.task` | `TRANSCRICAO` → `TRANSCRIPTION`; `ESTRUTURACAO` → `STRUCTURING`; `INCORPORACAO_RESPOSTAS` → `ANSWER_INCORPORATION`; `REVISAO` → `REVISION`; `CONSOLIDACAO` → `CONSOLIDATION` |
| `report_version.origin` | `ESTRUTURACAO_INICIAL` → `INITIAL_STRUCTURING`; `RESPOSTA_PERGUNTAS` → `QUESTION_ANSWER`; `ALTERACAO` → `CHANGE`; `CONSOLIDACAO` → `CONSOLIDATION` |
| `report_interaction.type` | `RESPOSTA_PERGUNTAS` → `QUESTION_ANSWER`; `ALTERACAO` → `CHANGE` |
| `report_interaction.result` | `APLICADA` → `APPLIED`; `SEM_ALTERACAO_DE_CONTEUDO` → `NO_CONTENT_CHANGE`; `PEDIDO_DE_ACAO_IGNORADO` → `ACTION_REQUEST_IGNORED`; `FALHOU` → `FAILED`; `RECUSADA_VERSAO_DESATUALIZADA` → `REJECTED_STALE_VERSION` |
| `report_version.prompt_version` | nome do arquivo de prompt sem extensão: `estruturacao-aula.v1` → `lesson-structuring.v1`, `incorporacao-respostas.v1` → `answer-incorporation.v1`, `revisao-aula.v1` → `lesson-revision.v1`, `consolidacao-semestral.v1` → `semester-consolidation.v1`, `revisao-semestral.v1` → `semester-revision.v1`, `correcao-json.v1` → `json-correction.v1` |

Limite de tamanho: cada valor novo DEVE caber no `varchar(n)` da coluna (o maior caso é
`PERMANENTLY_FAILED`, 18 caracteres em `varchar(18)`); o teste de migração confere isto para todos os valores.

## 3. Chaves dentro dos documentos JSON gravados (`jsonb`) (fatia 3, com a V7)

As chaves seguem os nomes dos contratos acima. Só chaves mudam; valores de texto (conteúdo escrito pelo
professor ou gerado pela IA, mensagens) **nunca** são tocados, exceto os valores de enumeração listados.

| Coluna | Chaves (atual → novo) |
|---|---|
| `lesson_report.content`, `report_version.content` (aula) | `conteudosTrabalhados` → `workedTopics`; `evolucao` → `progress`; `dificuldades` → `difficulties`; `atividadesPropostas` → `proposedActivities`; `observacoes` → `observations` |
| `semester_report.content`, `report_version.content` (semestral) | seções e subchaves de [ai-semester-report.schema.json](./ai-semester-report.schema.json); `itens` → `items` |
| `semester_report.general_information` | `nome` → `name`; `idade` → `age`; `instrumento` → `instrument`; `professor` → `teacher`; `periodoChave` → `periodKey`; `periodoRotulo` → `periodLabel`; `periodoInicio` → `periodStart`; `periodoFim` → `periodEnd`; `quantidadeAulasConsideradas` → `consideredLessonCount` |
| `processing_job.parameters` | `periodoChave` → `periodKey` e as demais chaves dos parâmetros, extraídas de `FilaTrabalhoPostgresAdaptador` e dos casos de uso (fatia 3) |
| `telegram_link.flow_data` | chaves do mapa da conversa, extraídas de `ConversaTelegram`/fluxos (fatia 3); valores que são ações de botão seguem a seção 4 |
| `channel_action.payload`, `.compensation` | chaves de [actions-v1.schema.json](./actions-v1.schema.json) (`acaoId` → `actionId`, `tipo` → `type`, `teclado` → `keyboard`, `linhas` → `rows`, `rotulo` → `label`, `dados` → `data`, …); valor de `type` e o texto de `data` (ação do botão) mudam; `text` e `caption` não |

Índice por expressão JSON afetado: `uk_trabalho_consolidacao_em_curso` usa `parametros ->> 'periodoChave'`;
a V7 o recria com `parameters ->> 'periodKey'`.

## 4. Ações dos botões do Telegram (`callback_data`, fatia 3)

Formato `v1:<action>:<argument>` (máximo 64 bytes). A versão `v1` **não muda** (o formato é o mesmo); muda
só o vocabulário da ação. Botão no formato antigo cai no ramo de ação desconhecida (FR-025).

| Atual | Novo |
|---|---|
| `aprovar:<versão>` | `approve:<version>` |
| `cancelar` | `cancel` |
| `descartar` | `discard` |
| `opcoes` | `options` |
| `sair` | `sign-out` |
| demais ações (`aluno`, `semestre`, `continuar`, `tentar`, `sim`, …) | palavra a palavra pelo glossário; lista completa extraída na fatia 3 e registrada em `telegram-conversa.md` |

As **palavras digitadas** pelo professor (`aprovar`, `cancelar`, `inicio`, `semestral`, …) não mudam
(FR-011); o enum interno `Comando` renomeia (`INICIO` → `HOME`, `REGISTRAR_AULA` → `REGISTER_LESSON`,
`APROVAR` → `APPROVE`, …).

## 5. Rotas da SPA (fatia 5)

| Atual | Nova |
|---|---|
| `/entrar` | `/sign-in` |
| `/` | `/` |
| `/alunos?busca=` | `/students?search=` |
| `/alunos/:alunoId/relatorio-em-aberto` | `/students/:studentId/open-report` |
| `/alunos/:alunoId/nova-aula` | `/students/:studentId/new-lesson` |
| `/trabalhos/:trabalhoId` | `/jobs/:jobId` |
| `/relatorios-aula/:id/perguntas` | `/lesson-reports/:reportId/questions` |
| `/relatorios-aula/:id` | `/lesson-reports/:reportId` |
| `/relatorios-aula/:id/aprovado` | `/lesson-reports/:reportId/approved` |
| `/alunos/:alunoId/semestral` | `/students/:studentId/semester` |
| `/relatorios-semestrais/:id` | `/semester-reports/:reportId` |
| `/relatorios-semestrais/:id/aprovado` | `/semester-reports/:reportId/approved` |
| `/alunos/:alunoId/historico` | `/students/:studentId/history` |

Parâmetros de consulta de `/sign-in`: `destino` → `destination`, `motivo` → `reason`. Cookie de sessão:
`sessao` → `session`. Chave de `localStorage` do tema: `relatorios.tema` → `reports.theme`; valores
`claro`/`escuro` → `light`/`dark` (o valor guardado em navegadores antigos deixa de ser reconhecido e o
tema volta ao padrão do sistema, sem erro).

## 6. Variáveis de ambiente e propriedades (fatias 3 e 6)

Regra: inglês palavra a palavra; os prefixos `CANAL_TELEGRAM_` → `TELEGRAM_CHANNEL_`. As propriedades do
`application.yaml` seguem a variável (`relatorios.escola.fuso-horario` → `reports.school.timezone`).
Sem alias nem período de compatibilidade (FR-024); quem tem `infra/.env` antigo recebe orientação de
regerá-lo a partir de `infra/.env.example` (FR-025 do `local.sh`).

| Atual | Nova |
|---|---|
| `BANCO_URL` / `BANCO_USUARIO` / `BANCO_SENHA` / `BANCO_POOL` | `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` / `DATABASE_POOL` |
| `CPF_CHAVE_CIFRAGEM` | `CPF_ENCRYPTION_KEY` |
| `SEGREDO_HMAC` | `HMAC_SECRET` |
| `SESSAO_WEB_HORAS` / `SESSAO_COOKIE_SEGURO` | `WEB_SESSION_HOURS` / `SESSION_COOKIE_SECURE` |
| `ACESSO_MAX_TENTATIVAS` / `ACESSO_BLOQUEIO_MINUTOS` | `ACCESS_MAX_ATTEMPTS` / `ACCESS_LOCK_MINUTES` |
| `CODIGO_VALIDADE_DIAS` | `CODE_VALIDITY_DAYS` |
| `DOMINIO` / `CERTIFICADOS_LOCAIS` | `DOMAIN` / `LOCAL_CERTIFICATES` |
| `TELEGRAM_HABILITADO` / `TELEGRAM_SEGREDO_WEBHOOK` | `TELEGRAM_ENABLED` / `TELEGRAM_WEBHOOK_SECRET` |
| `TELEGRAM_ARQUIVOS_DIR` | `TELEGRAM_FILES_DIR` |
| `AUDIO_PENDENTE_MINUTOS` / `AUDIO_LIMITE_MB` / `AUDIOS_TEMP_DIR` / `MULTIPART_LIMITE_MB` | `PENDING_AUDIO_MINUTES` / `AUDIO_LIMIT_MB` / `AUDIO_TEMP_DIR` / `MULTIPART_LIMIT_MB` |
| `DOCUMENTOS_DIR` | `DOCUMENTS_DIR` |
| `PROVEDORES_IA` (`simulado`\|`real`) | `AI_PROVIDERS` (`simulated`\|`real`) |
| `TRANSCRICAO_URL` / `_MODELO` / `_CHAVE` | `TRANSCRIPTION_URL` / `TRANSCRIPTION_MODEL` / `TRANSCRIPTION_API_KEY` |
| `LLM_MODELO` / `LLM_CHAVE` | `LLM_MODEL` / `LLM_API_KEY` |
| `TRANSCRICAO_PRECO_USD_POR_HORA` | `TRANSCRIPTION_PRICE_USD_PER_HOUR` |
| `LLM_PRECO_USD_POR_MILHAO_ENTRADA` / `_SAIDA` | `LLM_PRICE_USD_PER_MILLION_INPUT` / `_OUTPUT` |
| `ESCOLA_FUSO_HORARIO` | `SCHOOL_TIMEZONE` |
| `TRABALHOS_WORKERS` / `TRABALHOS_INTERVALO_MS` | `JOBS_WORKERS` / `JOBS_INTERVAL_MS` |
| `BANCO_PORTA_LOCAL` | `LOCAL_DATABASE_PORT` |
| `CANAL_TELEGRAM_ENTREGA` | `TELEGRAM_CHANNEL_DELIVERY` (valores `DIRETA`\|`ORQUESTRADOR` → `DIRECT`\|`ORCHESTRATOR`) |
| `CANAL_TELEGRAM_TOKEN_ATUAL` / `_TOKEN_PROXIMO` | `TELEGRAM_CHANNEL_CURRENT_TOKEN` / `TELEGRAM_CHANNEL_NEXT_TOKEN` |
| `CANAL_TELEGRAM_ORQUESTRADOR_URL` / `_TOKEN` | `TELEGRAM_CHANNEL_ORCHESTRATOR_URL` / `TELEGRAM_CHANNEL_ORCHESTRATOR_TOKEN` |
| `CANAL_TELEGRAM_CONEXAO_TIMEOUT_MS` / `_RESPOSTA_TIMEOUT_MS` | `TELEGRAM_CHANNEL_CONNECTION_TIMEOUT_MS` / `TELEGRAM_CHANNEL_RESPONSE_TIMEOUT_MS` |
| `CANAL_TELEGRAM_RETENTATIVA_INICIAL_S` / `_RETENTATIVA_TETO_S` / `_PRAZO_H` | `TELEGRAM_CHANNEL_RETRY_INITIAL_S` / `TELEGRAM_CHANNEL_RETRY_CEILING_S` / `TELEGRAM_CHANNEL_DEADLINE_H` |
| `CANAL_TELEGRAM_CONFIRMACAO_MAX_S` / `_LEASE_S` | `TELEGRAM_CHANNEL_CONFIRMATION_MAX_S` / `TELEGRAM_CHANNEL_LEASE_S` |
| `CANAL_TELEGRAM_INTERVALO_DESPACHO_MS` / `_LOTE_MAX` | `TELEGRAM_CHANNEL_DISPATCH_INTERVAL_MS` / `TELEGRAM_CHANNEL_BATCH_MAX` |
| `CANAL_TELEGRAM_RETENCAO_PAYLOAD_H` / `_RETENCAO_REGISTRO_D` | `TELEGRAM_CHANNEL_PAYLOAD_RETENTION_H` / `TELEGRAM_CHANNEL_RECORD_RETENTION_D` |
| `CONTRATO_API` (testes) | `CONTRACT_API` |
| `E2E_CPF_CHAVE` / `E2E_SEGREDO_HMAC` | `E2E_CPF_KEY` / `E2E_HMAC_SECRET` |
| inalteradas | `POSTGRES_PASSWORD`, `ACME_EMAIL`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `TELEGRAM_API_URL`, `TELEGRAM_WEBHOOK_URL`, `TRANSCRIPTION`/`LLM` `_URL`, `ASSETS_DIR`, `E2E_URL`, `E2E_BANCO_URL` → `E2E_DATABASE_URL` |

## 7. Infraestrutura, scripts e CI (fatia 6)

| Atual | Novo |
|---|---|
| `infra/scripts/implantar.sh` | `infra/scripts/deploy.sh` |
| `.github/workflows/implantar.yml` | `.github/workflows/deploy.yml` (o `ci.yml` não muda) |
| `infra/compose.banco.yaml` | `infra/compose.database.yaml` |
| `infra/carga/` (`exemplo.json`, `demonstracao.json`) | `infra/seed/` (`example.json`, `demo.json`) |
| `local.sh`: `subir` · `carga` · `codigo` · `status` · `logs` · `banco` · `banco-porta` · `banco-fechar` · `testes` · `parar` · `limpar` | `up` · `seed` · `code` · `status` · `logs` · `database` · `database-port` · `database-close` · `tests` · `stop` · `clean` |
| `local.sh testes backend\|frontend\|e2e\|ia-real\|todos` | `local.sh tests backend\|frontend\|e2e\|real-ai\|all` |
| `local.sh subir --massa=true\|false` | `local.sh up --demo-data=true\|false` |
| CLI do backend: `carga`, `massa`, `emitir-codigo`; `--arquivo`; perfil Spring `carga` | `seed`, `demo-data`, `issue-code`; `--file`; perfil `seed` |
| serviços de teste do compose: `testes-backend`, `testes-frontend`, `testes-e2e`, `testes-ia-real`, `postgres-teste` | `tests-backend`, `tests-frontend`, `tests-e2e`, `tests-real-ai`, `postgres-test` |
| projeto do compose `relatorios` / `relatorios-testes`; volumes `relatorios_pgdata`, `relatorios_documentos`; banco e usuário `relatorios` | `reports` / `reports-tests`; `reports_pgdata`, `reports_documents`; banco e usuário `reports` |
| `backend/nucleo` · `backend/aplicacao` · `aplicacao.jar` · pacote `br.com.felipeblaft.relatorios` · `artifactId` `relatorios` | `backend/core` · `backend/application` · `application.jar` · `br.com.felipeblaft.reports` · `reports` |

A decisão sobre `local.sh testes …` (a constituição e o pedido citam o comando atual) está no `plan.md`,
seção *Pontos de decisão*.

## 8. Arquivos de recurso (fatia 3 e 5)

| Atual | Novo |
|---|---|
| `prompts/estruturacao-aula.v1.txt` etc. | `prompts/lesson-structuring.v1.txt` etc. (lista na seção 2, `prompt_version`) |
| `ia/schemas/relatorio-aula.schema.json`, `relatorio-semestral.schema.json` | `ai/schemas/lesson-report.schema.json`, `semester-report.schema.json` |
| `ia/simulado/estruturacao-aula.json`, `consolidacao-semestral.json`, `transcricao.txt` | `ai/simulated/lesson-structuring.json`, `semester-consolidation.json`, `transcription.txt` |
| `mensagens/mensagens_pt_BR.properties` | `messages/messages_pt_BR.properties` (o sufixo `pt_BR` é a localidade, não um nome) |
| `pdf/relatorio-aula.html`, `relatorio-semestral.html`, `estilos-pdf.css`, `fontes/`, `imagens/` | `pdf/lesson-report.html`, `semester-report.html`, `pdf-styles.css`, `fonts/`, `images/` |
| `frontend/public/marca/fb-*-marfim.png` (`ouro`, `ouroclaro`, `preto`, `metal`, `monograma`, `filete`, `wordmark`) | `frontend/public/brand/fb-*-ivory.png` (`gold`, `lightgold`, `black`, `metal`, `monogram`, `divider`, `wordmark`) |
| `frontend/src/{app,paginas,componentes,servicos,estilos,mensagens}` | `frontend/src/{app,pages,components,services,styles,messages}` |
| `frontend/tests/{componentes,paginas,e2e}` | `frontend/tests/{components,pages,e2e}` |
| `Tela*.tsx` (`TelaPainel`, `TelaAlunos`, …) | `*Screen.tsx` (`DashboardScreen`, `StudentsScreen`, …), um por fluxo |
| `scripts/gerar-mensagens.mjs`; `npm run gerar`, `gerar:api`, `gerar:mensagens`, `formatar` | `scripts/generate-messages.mjs`; `npm run generate`, `generate:api`, `generate:messages`, `format` |

CSS: classes e variáveis seguem a BEM atual com o vocabulário trocado (`.pagina--estreita` →
`.page--narrow`, `.menu-professor__item` → `.teacher-menu__item`, `.girador` → `.spinner`). As variáveis de
`tokens.css` com siglas (`--gd`, `--gs`) não mudam. O inventário das classes sai do verificador de
renomeação (R-02), não de lista manual.

## 9. Termos polissêmicos (resolvidos por contexto em `glossary.json`, seção `contextual`)

| Termo | Contexto → inglês |
|---|---|
| `inicio` | tela inicial (rota/API `Home`, `Comando.INICIO`→`HOME`, pacote `usecase.inicio`→`usecase.home`, `services/inicio.ts`→`services/home.ts`, ação de botão `v1:inicio`→`v1:home`) → `home`; início de período (`periodoInicio`) → `start` |
| `registro` | ato de registrar uma aula (`REGISTRO_AULA`→`LESSON_REGISTRATION`, `FluxoRegistroAula…`) → `registration`; linha/log de dados (`RegistroCustoPorta`, `registroUpdates`, `RETENCAO_REGISTRO_D`→`RECORD_RETENTION_D`) → `record` |
| `gravar` | persistir (`GravarAcoesCanal…`, `gravarLote`) → `save`; gravar áudio usa `gravador`→`recorder`, `gravando`→`recording`, `gravacao`→`recording` |
| `filete` | ativo de marca → `divider` (`regra` continua `rule`) |

Homônimos aceitos, sempre dentro de compostos que os distinguem: `request` (pedido do usuário × sufixo HTTP),
`home` (tela × `homeStudyRegularity`), `port`, `support`, `page`/`screen` (ver `notes` do glossário).

## 10. Nomes de PDF (FR-026)

| O quê | Antes → depois |
|---|---|
| Nome mostrado ao usuário (download no Portal e documento no Telegram) | **não muda**: `relatorio-aula-<data>[-v<n>].pdf`, `relatorio-semestral-<período>[-v<n>].pdf` (exceção `download-file-name`) |
| Nome interno de armazenamento (`storage_key`) de documentos **novos** | `oficial.pdf` → `official.pdf`; `v<n>.pdf` não muda; pasta por tipo de relatório `AULA`/`SEMESTRAL` → `LESSON`/`SEMESTER` (vem da enumeração, fatia 4) |
| `storage_key` de documentos **já gravados** | não muda (dado opaco, lido do próprio registro); nenhum arquivo é movido |
