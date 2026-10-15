---

description: "Lista de tarefas da feature 002 - API interna de canal do Telegram"
---

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

# Tasks: API interna de canal do Telegram para orquestrador externo

**Input**: Documentos de projeto em `specs/002-api-interna-canal-telegram/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md) (R-01 a R-15), [data-model.md](./data-model.md), [contracts/](./contracts/) (`canal-telegram-interno.md`, `acoes-v1.schema.json`, `canal-telegram-interno.openapi.yaml`), [quickstart.md](./quickstart.md) (V1 a V20)

**Tests**: INCLUÍDOS. A spec os exige (FR-030, FR-031, FR-032, SC-001, SC-005 a SC-008) e o plano os detalha em "Estratégia de testes". Todo comando de teste roda por `docker compose -f infra/compose.test.yaml run --rm testes-backend` (única dependência da máquina é o Docker).

**Organization**: Tarefas agrupadas por história de usuário. A US2 (segurança) vem **antes** da US1 na ordem de execução porque a rota da US1 só é alcançável pela cadeia `/interno/**` criada na US2; ambas são P1 e juntas formam o MVP.

## Format: `[ID] [P?] [Story] Descrição com caminho`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: história da spec (US1 a US5); ausente em Setup, Foundational e Polish
- Código em português do Brasil **sem acentos** (Princípio VI): sufixos `*CasoDeUso`, `*Controlador`, `*Servico`, `*Repositorio`, `*Porta`, `*Adaptador`, `*Mapeador`, `*Dto`/`*Requisicao`/`*Resposta`; rótulos de protocolo (`X-N8N-Service-Token`, `Idempotency-Key`, `EXECUTADA`…) são exceção permitida.

## Path Conventions

Pacote raiz Java: `br.com.felipeblaft.relatorios`. Abreviações usadas nos caminhos abaixo (substitua literalmente):

| Abreviação | Caminho |
|---|---|
| `{NUC}` | `backend/nucleo/src/main/java/br/com/felipeblaft/relatorios` |
| `{NUT}` | `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios` |
| `{APP}` | `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios` |
| `{APT}` | `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios` |
| `{RES}` | `backend/aplicacao/src/main/resources` |

Regras fixas: `nucleo` é Java puro (sem Spring, sem JPA, sem citar `n8n`; JaCoCo ≥ 90% de linhas e branches); `aplicacao` só traz adaptadores; `entrada` não depende de `saida` (a entrada define a porta, a saída a implementa). Dublês do núcleo ficam em `{NUT}/dubles/` (à mão, sem Mockito). Nenhuma dependência nova de produção.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: configuração e esqueleto compartilhados por todas as histórias

- [X] T001 Em `{APP}/configuracao/PropriedadesAplicacao.java`, adicionar o `record CanalTelegram` (prefixo `relatorios.canal-telegram`) com os campos de [data-model.md §4](./data-model.md): `entregaProativa` (enum `DIRETA`|`ORQUESTRADOR`, padrão `DIRETA`), `tokenAtual`, `tokenProximo`, `orquestradorUrl`, `orquestradorToken`, `conexaoTimeoutMs` (3000), `respostaTimeoutMs` (10000), `retentativaInicialSegundos` (30), `retentativaTetoSegundos` (900), `prazoHoras` (24), `confirmacaoMaxSegundos` (60), `leaseSegundos` (60; deve ser maior que `conexaoTimeoutMs` + `respostaTimeoutMs`, validar na subida), `intervaloDespachoMs` (2000), `loteMaxPorCiclo` (20), `retencaoPayloadHoras` (24), `retencaoRegistroDias` (7); `toString()` mascarando `tokenAtual`, `tokenProximo` e `orquestradorToken` (mesmo padrão dos records `Seguranca` e `Telegram` já existentes); expor o record no `PropriedadesAplicacao` raiz
- [X] T002 [P] Em `{RES}/application.yaml`, adicionar o bloco `relatorios.canal-telegram` lendo as variáveis `CANAL_TELEGRAM_ENTREGA`, `CANAL_TELEGRAM_TOKEN_ATUAL`, `CANAL_TELEGRAM_TOKEN_PROXIMO`, `CANAL_TELEGRAM_ORQUESTRADOR_URL`, `CANAL_TELEGRAM_ORQUESTRADOR_TOKEN`, `CANAL_TELEGRAM_CONEXAO_TIMEOUT_MS`, `CANAL_TELEGRAM_RESPOSTA_TIMEOUT_MS`, `CANAL_TELEGRAM_RETENTATIVA_INICIAL_S`, `CANAL_TELEGRAM_RETENTATIVA_TETO_S`, `CANAL_TELEGRAM_PRAZO_H`, `CANAL_TELEGRAM_CONFIRMACAO_MAX_S`, `CANAL_TELEGRAM_LEASE_S`, `CANAL_TELEGRAM_INTERVALO_DESPACHO_MS`, `CANAL_TELEGRAM_LOTE_MAX`, `CANAL_TELEGRAM_RETENCAO_PAYLOAD_H`, `CANAL_TELEGRAM_RETENCAO_REGISTRO_D`, com os padrões de data-model §4 (tokens e URL vazios)
- [X] T003 [P] Em `infra/.env.example`, documentar (comentário + valor vazio/padrão) todas as variáveis `CANAL_TELEGRAM_*` de T002, avisando que `TOKEN_*` e `ORQUESTRADOR_TOKEN` são segredos; **não** alterar nenhum `infra/compose*.yaml` nem o Caddy (escopo da feature 004)
- [X] T004 [P] Em `backend/aplicacao/src/test/resources/application-teste.yaml`, adicionar `relatorios.canal-telegram` com `token-atual` e `token-proximo` fictícios e distintos, `entrega-proativa: DIRETA` e `orquestrador-url` vazio (os ITs de entrega sobrescrevem para o WireMock do orquestrador)
- [X] T005 [P] Em `{NUT}/arquitetura/RegrasArquiteturaTeste.java`, incluir `n8n` na lista `FORNECEDORES` proibidos no núcleo (R-13)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: migração, modelo de domínio da outbox, portas, persistência, extração da porta de entrada única e referência de documento. Nada de história começa antes disto.

**⚠️ CRITICAL**: nenhuma história pode começar até esta fase terminar.

### Migração

- [X] T006 Criar `{RES}/db/migration/V6__canal_telegram_interno.sql` exatamente como [data-model.md §1](./data-model.md): (a) `ALTER TABLE update_telegram_processado` + `total_acoes integer` e `origem varchar(10)` com `CHECK (total_acoes IS NULL OR total_acoes >= 0)` e `CHECK (origem IS NULL OR origem = 'INTERNO')`; (b) `CREATE TABLE acao_canal` (colunas, inclusive `versao integer NOT NULL DEFAULT 0` para controle otimista, `PRIMARY KEY (id)`, `seq bigint GENERATED ALWAYS AS IDENTITY UNIQUE`, CHECKs de `estado`/`tipo`/`origem`/`tentativas`/`RESPOSTA_UPDATE exige update_id`) e os índices parciais `ix_acao_canal_entrega`, `ix_acao_canal_chat_vivas`, `ix_acao_canal_update`, `ix_acao_canal_lote`, `ix_acao_canal_expurgo`; (c) `CREATE TABLE documento_canal` (`token_hash char(64)` PK, demais colunas e CHECKs) e `ix_documento_canal_expira`. Sem chaves estrangeiras; migração aditiva
- [X] T007 [P] Criar `{APT}/saida/persistencia/MigracaoV6IT.java` (V20): aplica as migrações até a V5 com linhas antigas em `update_telegram_processado`, aplica a V6 e confirma que as linhas antigas ficam com `total_acoes`/`origem` nulos, que as restrições rejeitam valores inválidos e que `registrarSeNovo` continua atômico para o webhook antigo (estender `suporte/IntegracaoBase`)

### Domínio e portas do núcleo (Java puro)

- [X] T008 [P] Criar em `{NUC}/dominio/canal/` os enums `EstadoAcaoCanal` (`PENDENTE`, `ENTREGUE`, `EXECUTADA`, `FALHOU`, `FALHA_DEFINITIVA`, `DESCARTADA`, com método `final()`), `OrigemAcaoCanal` (`RESPOSTA_UPDATE`, `PROATIVA`, `COMPENSACAO`) e `TipoAcaoCanal` (`MENSAGEM`, `EDICAO`, `DOCUMENTO`, `RESPOSTA_CALLBACK`, `MENSAGEM_ETAPA`)
- [X] T009 [P] Criar `{NUC}/dominio/canal/PoliticaRetentativa.java` (pura): intervalo inicial, teto, prazo e jitter injetável (função `double`→ zero nos testes); `proximaTentativa(int tentativas, Instant agora)` = `agora + min(teto, inicial × 2^(n−1)) × (1 ± jitter)`; e `Instant prazoDe(Instant criadoEm)`. Teste em `{NUT}/dominio/canal/PoliticaRetentativaTeste.java` cobrindo a sequência 30 s, 1, 2, 4, 8, 15, 15 min, o teto, o jitter e valores inválidos
- [X] T010 Criar `{NUC}/dominio/canal/AcaoCanal.java` conforme [data-model.md §1.2 e §2](./data-model.md): campos `id`, `loteId`, `canal`, `origem`, `updateId`, `chatId`, `professorId`, `trabalhoId`, `tipo`, `payload` (String opaca), `compensacao` (String opaca), `estado`, `tentativas`, `proximaTentativaEm`, `prazoEm`, `criadoEm`, `entregueEm`, `finalizadoEm`, `codigoMotivo`; métodos `registrarFalhaEntrega(agora, politica)`, `marcarEntregue(agora)`, `confirmarExecutada(agora)`, `confirmarFalha(codigo, agora)`, `descartar(agora)`, `vencida(agora)`, `temCompensacao()`, `zerarPayload()`; invariantes: estado final nunca volta, confirmação idêntica repetida é inócua, divergente é sinalizada (retorno booleano/enum) sem alterar; a ação carrega `versao` (incrementada a cada gravação) para o controle otimista do repositório. Teste `{NUT}/dominio/canal/AcaoCanalTeste.java` cobrindo todas as transições e invariantes (depende de T008, T009)
- [X] T011 [P] Criar `{NUC}/dominio/canal/DocumentoCanal.java` (`tokenHash`, `professorId`, `relatorioTipo` `AULA`|`SEMESTRAL`, `relatorioId`, `versao` nulo = oficial, `nomeArquivo`, `criadoEm`, `expiraEm`; `valido(Instant agora)`). Teste `{NUT}/dominio/canal/DocumentoCanalTeste.java`
- [X] T012 [P] Criar em `{NUC}/porta/`: `AcaoCanalRepositorio` (gravar lote idempotente por `id` sem sobrescrever; `buscarPorId`; `buscarPorUpdate(updateId)` ordenado por `seq`; `buscarPorLote`; `reservarProximoLoteDevido(agora, confirmacaoMax, leaseAte)` com `FOR UPDATE SKIP LOCKED` e portão de ordem por chat; `atualizarSeVersao(acao, versaoEsperada)` (atualização condicional que devolve `boolean`; `false` = outro ator gravou antes, ex.: a confirmação chegou durante o HTTP do despacho); `expurgar(payloadAte, registroAte)`; contagens para métricas), `DocumentoCanalRepositorio` (`emitir`, `buscarPorHash`, `expurgarVencidos(agora)`), `OrquestradorCanalPorta` (`ResultadoEntrega entregar(LoteEntrega lote)` → `ACEITO` ou `RECUSADO(codigoSituacao)`) e os records `LoteEntrega` (`loteId`, `origem`, `criadoEm`, `tentativa`, ações com `acaoId`/`tipo`/`payload`/`trabalhoId`) e `ResultadoEntrega`
- [X] T013 [P] Criar dublês em `{NUT}/dubles/`: `AcaoCanalRepositorioEmMemoria` (reproduz ordem por `seq`, idempotência por id, o portão de ordem por chat e a atualização condicional por `versao`), `DocumentoCanalRepositorioEmMemoria`, `OrquestradorCanalFalso` (programável: aceita, recusa com código, registra lotes recebidos); reaproveitar `RelogioFixo`
- [X] T014 Criar `{NUC}/casodeuso/canal/GravarAcoesCanalCasoDeUso.java`: recebe a lista de ações já mapeadas (id, tipo, payload, compensação, origem, chat, professor, trabalho), define `lote_id`, `estado` (`ENTREGUE` para `RESPOSTA_UPDATE`; `PENDENTE` para `PROATIVA`/`COMPENSACAO`), `prazo_em` pela política e grava de forma idempotente (mesmo id = sem efeito, FR-014). Teste `{NUT}/casodeuso/canal/GravarAcoesCanalCasoDeUsoTeste.java` (depende de T010, T012, T013)
- [X] T015 Criar `{NUC}/casodeuso/canal/EmitirDocumentoCanalCasoDeUso.java`: gera token de 256 bits (`SecureRandom`, Base64url sem preenchimento, 43 caracteres), grava só o hash SHA-256 hex em `DocumentoCanalRepositorio` com `expira_em = prazo de entrega + 1 h` e devolve o token. Teste `{NUT}/casodeuso/canal/EmitirDocumentoCanalCasoDeUsoTeste.java` (formato, unicidade, validade, hash gravado ≠ token) (depende de T011, T012, T013)

### Persistência (adaptadores)

- [X] T016 [P] Criar em `{APP}/saida/persistencia/` `AcaoCanalEntidade`, `AcaoCanalJpa` e `AcaoCanalRepositorioAdaptador` implementando `AcaoCanalRepositorio`: `INSERT … ON CONFLICT (id) DO NOTHING`; reserva do lote devido por SQL nativo com `FOR UPDATE SKIP LOCKED` e *lease* (`proxima_tentativa_em` empurrada), ignorando chats com ação anterior `PENDENTE` ou `ENTREGUE` há menos de `confirmacao-max-segundos`; `UPDATE … SET …, versao = versao + 1 WHERE id = ? AND versao = ?` para `atualizarSeVersao` (devolve se afetou linha) e lease de `leaseSegundos` na reserva; conversão por `*Mapeador` (o domínio não conhece a entidade); `jsonb` para `payload`/`compensacao` (reaproveitar `JsonPersistencia`)
- [X] T017 [P] Criar em `{APP}/saida/persistencia/` `DocumentoCanalEntidade`, `DocumentoCanalJpa` e `DocumentoCanalRepositorioAdaptador` implementando `DocumentoCanalRepositorio`
- [X] T018 Em `{APP}/entrada/telegram/RegistroUpdatesTelegram.java` (a interface vive na entrada, não no núcleo) e em `{APP}/saida/persistencia/UpdateTelegramProcessadoEntidade.java`, `UpdateTelegramProcessadoJpa.java` e `RegistroUpdatesTelegramAdaptador.java`: mapear as colunas `total_acoes`/`origem`; acrescentar `registrarSeNovo(updateId, origem)`, `concluir(updateId, totalAcoes)` e `situacao(updateId)` (existe? `totalAcoes` nulo? idade da linha? `origem`), mantendo o comportamento atual do webhook (não toca nas colunas novas)
- [X] T019 [P] Criar teste de integração `{APT}/saida/persistencia/AcaoCanalRepositorioAdaptadorIT.java`: idempotência por id, ordem por `seq`, reserva com `SKIP LOCKED` por dois consumidores concorrentes (uma só reserva por lote), portão de ordem por chat com e sem `confirmacao-max-segundos` vencido, expurgo (depende de T006, T016)

### Entrada única e referência de documento

- [X] T020 Em `{APP}/entrada/telegram/SaidaTelegram.java`, fazer `Documento` ganhar `ReferenciaDocumento` (`professorId`, tipo `AULA`|`SEMESTRAL`, `relatorioId`, `versao` ou nulo para o PDF oficial) **mantendo** o campo `conteudo` (bytes) para o envio direto antigo; criar `ReferenciaDocumento` ao lado. Em `ApresentacaoRelatorioAulaTelegram.java`, `ApresentacaoRelatorioSemestralTelegram.java` e `FluxoHistoricoTelegram.java`, preencher a referência onde criam `Documento`. Nenhum texto/botão muda
- [X] T021 Criar `{APP}/entrada/telegram/ResultadoUpdateTelegram.java` (`updateId`, `repetido`, `List<SaidaTelegram>`) e `{APP}/entrada/telegram/ProcessadorUpdateTelegramServico.java`: extrair de `TelegramWebhookControlador.java` o que não é HTTP — ler o JSON do update (mesmo `JsonMapper` tolerante), `registrarSeNovo`, chamar `InterpretadorConversaTelegram.interpretar` e produzir `ERRO_INTERNO` diante de `RuntimeException` (hoje `respostaDeErro`), preservando o `RESPOSTA_CALLBACK` primeiro em toque de botão (FR-008). Alterar `TelegramWebhookControlador` para usar o serviço e continuar enviando cada `SaidaTelegram` por `EnvioTelegram` **exatamente como hoje**. Registrar o serviço em `{APP}/configuracao/` se necessário (depende de T018, T020)
- [X] T022 Rodar `docker compose -f infra/compose.test.yaml run --rm testes-backend` e confirmar que **todos** os testes existentes e os novos de T007/T009–T015/T019 passam e que o gate JaCoCo do `nucleo` está verde (prova de que o webhook atual segue intacto, US5.5)

**Checkpoint**: V6 aplicada, outbox e portas prontas, entrada única extraída; histórias podem começar.

---

## Phase 3: User Story 2 - Acesso interno protegido por token de serviço (Priority: P1)

**Goal**: só o orquestrador, com token de serviço, usa `/interno/**`; token não abre o portal, cookie não abre o interno, sem token configurado tudo é recusado.

**Independent Test**: `SegurancaInternaIT` — matriz rota × credencial (sem, errada, correta, `proximo`, ambos, só cookie de portal, token no portal, nenhum token configurado): só "token correto na área interna" passa; o resto é `401` sem corpo.

- [X] T023 [P] [US2] Criar `{APP}/entrada/interno/seguranca/ComparadorSegredo.java`: compara `SHA-256(recebido)` com `SHA-256(configurado)` por `MessageDigest.isEqual` sobre digests de tamanho fixo; `contemAlgum(recebido, tokenAtual, tokenProximo)` avalia **sempre os dois** com `&` bit a bit (sem curto-circuito); token ausente/vazio vira digest-isca; nulos tratados. Teste `{APT}/entrada/interno/seguranca/ComparadorSegredoTeste.java` (V5: digests, tamanhos diferentes, nulos, isca, ambos avaliados — verificação estrutural, sem medir relógio)
- [X] T024 [US2] Criar `{APP}/entrada/interno/seguranca/TokenServicoFiltro.java` (`OncePerRequestFilter`): lê `X-N8N-Service-Token`, usa `ComparadorSegredo` com `canalTelegram.tokenAtual/tokenProximo`, autentica com a autoridade `SERVICO_CANAL`; nenhum token configurado = nunca autentica (fail-closed); nunca registra o token em log (depende de T001, T023)
- [X] T025 [US2] Criar `{APP}/entrada/interno/seguranca/SegurancaInternaConfiguracao.java`: `SecurityFilterChain` com `securityMatcher("/interno/**")` e `@Order(1)`, `STATELESS`, CSRF desligado **só nesta cadeia**, **sem** `SessaoCookieFiltro`, CORS/`httpBasic`/`formLogin` desligados, `authenticationEntryPoint` devolvendo `401` sem corpo, `anyRequest().hasAuthority("SERVICO_CANAL")`, filtro de T024 antes da autenticação; aviso `WARN` na subida se nenhum token estiver configurado (sem imprimir valores) (depende de T024)
- [X] T026 [US2] Em `{APP}/entrada/web/seguranca/SegurancaConfiguracao.java`, mudar **somente** para `@Order(2)`; regras do portal, CSRF e `denyAll()` final inalterados (FR-027) (depende de T025)
- [X] T027 [US2] Em `{APP}/entrada/telegram/TelegramWebhookControlador.java`, substituir a comparação `segredoValido` pelo `ComparadorSegredo` (o webhook antigo herda a comparação de tempo constante sobre digests); comportamento observável inalterado (depende de T023)
- [X] T028 [US2] Criar `{APT}/entrada/interno/seguranca/SegurancaInternaIT.java` (V4, SC-005): para uma rota interna que exista sem lógica (`GET /interno/v1/canal/telegram/acoes/inexistente`: autenticado ⇒ não-`401`, recusado ⇒ `401`), cobrir sem token, token errado, `atual`, `proximo`, ambos configurados, só cookie de sessão de portal válido, nenhum token configurado (propriedades sobrescritas) e `401` **sem corpo** em todos os recusados; e `GET /api/v1/...` com o token de serviço ⇒ `401`; cookie + token errado ⇒ `401`; CSRF do portal continua exigido nas rotas `/api/v1/**` mutáveis (depende de T026)

**Checkpoint**: área interna isolada nos dois sentidos; rotas de negócio entram na US1.

---

## Phase 4: User Story 1 - Conversa completa do professor pela nova porta, sem diferença visível (Priority: P1) 🎯 MVP

**Goal**: `POST /interno/v1/canal/telegram/atualizacao` recebe o update bruto, usa o **mesmo** interpretador do webhook e devolve a lista ordenada de ações v1; update repetido devolve só as não confirmadas; `POST /confirmacoes` e `GET /acoes/{id}` completam o ciclo.

**Independent Test**: reexecutar todos os `*ConversaIT` e `RoteiroParidade*IT` com `telegram.transporte=interno` sem alterar nenhuma expectativa (V1, SC-001), com todo corpo validado contra `acoes-v1.schema.json` (V2) e a matriz de idempotência C4–C6 (V6).

### DTOs e mapeamento

- [X] T029 [P] [US1] Criar em `{APP}/entrada/telegram/canal/` os DTOs imutáveis do formato v1, **seguindo literalmente** `specs/002-api-interna-canal-telegram/contracts/acoes-v1.schema.json`: `AcaoDto` (interface `sealed`, discriminador `tipo`, campos comuns `acaoId`, `chatId`) e subtipos `AcaoMensagemDto`, `AcaoEdicaoDto`, `AcaoDocumentoDto`, `AcaoRespostaCallbackDto`, `AcaoMensagemEtapaDto`, mais `TecladoDto`/`BotaoDto` (`rotulo`, `dados` ≤ 64). Serialização Jackson 3 (`tools.jackson`), sem `@JsonIgnore` de campos do contrato
- [X] T030 [P] [US1] Criar em `{APP}/entrada/telegram/canal/` `RespostaAtualizacao` (`versao:"v1"`, `updateId`, `loteId`, `acoes`), `RequisicaoConfirmacao` (lista de `{acaoId, situacao, codigoMotivo?, mensagemId?}`), `RespostaConfirmacao` (`aceitas`, `ignoradas`), `RespostaEstadoAcao` e `LoteEntregaDto`, conforme o schema e o OpenAPI
- [X] T031 [US1] Criar `{APP}/entrada/telegram/canal/AcaoCanalMapeador.java`: `List<SaidaTelegram>` → `List<AcaoDto>` (+ cada ação serializada para o `payload`); **divide textos acima de 4096 por `ApresentadorTelegram.dividir`**, uma `MENSAGEM` por parte com o teclado só na última; atribui `acaoId = u<update_id>-<posicao>` **depois** da expansão; `Documento` → `AcaoDocumentoDto` emitindo o identificador opaco por `EmitirDocumentoCanalCasoDeUso` (validade = prazo de entrega + 1 h) a partir da `ReferenciaDocumento`, levando `nomeArquivo`, `tipoConteudo=application/pdf` e a `legenda` (o envio atual manda `caption`; paridade); texto já escapado como HTML, botões como `{rotulo, dados}`; gera a `compensacao` (`EDICAO` → `MENSAGEM` com mesmo texto/teclado). Nenhuma regra de conversa (FR-005) (depende de T020, T029, T015)
- [X] T032 [P] [US1] Teste `{APT}/entrada/telegram/canal/AcaoCanalMapeadorTeste.java`: divisão de texto longo (limite 4096, teclado só na última), ids por posição **após** expansão, `Documento` com id opaco e sem bytes, compensação de `EDICAO`, `RESPOSTA_CALLBACK` primeiro em toque de botão, ausência de áudio/caminho/URL (depende de T031)

### Casos de uso do núcleo

- [X] T033 [P] [US1] Criar `{NUC}/casodeuso/canal/ObterAcoesNaoConfirmadasDoUpdateCasoDeUso.java`: dado `updateId`, devolve as ações `ENTREGUE` na ordem de `seq`, mesmos ids; lista vazia se todas confirmadas (FR-007). Teste `{NUT}/casodeuso/canal/ObterAcoesNaoConfirmadasDoUpdateCasoDeUsoTeste.java` (C4–C6)
- [X] T034 [P] [US1] Criar `{NUC}/casodeuso/canal/ConfirmarExecucaoAcoesCanalCasoDeUso.java` (versão base): para cada resultado, `EXECUTADA`/`FALHOU` com `codigoMotivo`; idempotente (confirmação idêntica repetida sem efeito); divergente, `acaoId` desconhecido, `DESCARTADA` ou `FALHA_DEFINITIVA` vão para `ignoradas`, sem erro; `FALHOU` sem `codigoMotivo` é inválido; `codigoMotivo` **desconhecido** (valor novo do orquestrador) é aceito e tratado como `ERRO_TELEGRAM`, e cada resultado é validado isoladamente (um item inválido vai para `ignoradas`, não derruba os demais). (`mensagemId` e compensação entram na US3, T055.) Teste `{NUT}/casodeuso/canal/ConfirmarExecucaoAcoesCanalCasoDeUsoTeste.java`
- [X] T035 [US1] Registrar os casos de uso de T014, T015, T033 e T034 como beans em `{APP}/configuracao/CasosDeUsoConfiguracao.java` (com o decorador transacional existente `TransacaoCasoDeUsoDecorador` onde aplicável) e fornecer `PoliticaRetentativa` a partir de `PropriedadesAplicacao.CanalTelegram`

### Controlador

- [X] T036 [US1] Criar `{APP}/entrada/telegram/canal/CanalTelegramControlador.java` (`/interno/v1/canal/telegram`) com:
  - `POST /atualizacao`: valida `X-Telegram-Bot-Api-Secret-Token` com `ComparadorSegredo` (ausente/errado ⇒ `401` sem corpo, FR-006); `registrarSeNovo(updateId, INTERNO)`; **novo**: `ProcessadorUpdateTelegramServico` → `AcaoCanalMapeador` → `GravarAcoesCanalCasoDeUso` + `concluir(updateId, total)` e devolve `RespostaAtualizacao`; **repetido**: polling de até 3 s (100 ms) enquanto `total_acoes` nulo, depois `ObterAcoesNaoConfirmadasDoUpdateCasoDeUso`; linha `INTERNO` com `total_acoes` nulo e mais de 30 s ⇒ `ERRO_INTERNO` com id `u<N>-e0`; linha de `origem` nula (webhook antigo) ⇒ `acoes: []`; corpo ilegível ou tipo não suportado ⇒ `200` com `acoes: []`; **nunca** envia nada ao Telegram (FR-010)
  - `POST /confirmacoes`: valida `versao`, chama `ConfirmarExecucaoAcoesCanalCasoDeUso`, `400` com `ErroResposta` para corpo inválido (`VERSAO_NAO_SUPORTADA` quando for o caso)
  - `GET /acoes/{acaoId}`: `RespostaEstadoAcao` ou `404` sem corpo
  Sem regra de conversa; respostas `401`/`404` sem corpo; logs só com `updateId`/`acaoId` (depende de T021, T025, T027, T030, T031, T035)

### Testes da história

- [X] T037 [US1] Em `{APT}/suporte/TelegramConversaBase.java`, introduzir o **executor de transporte** escolhido por `System.getProperty("telegram.transporte", "webhook")`: modo `webhook` = comportamento atual; modo `interno` = `POST /interno/v1/canal/telegram/atualizacao` com token e segredo, **valida o corpo contra `#/$defs/RespostaAtualizacao`** (schema, `networknt json-schema-validator`) e delega ao novo `{APT}/suporte/ExecutorAcoesSimuladoTeste.java`. Nenhuma asserção existente (`textosEnviados`, `ultimaMensagem`, `botoes`, `chamadas`) pode mudar (depende de T036)
- [X] T038 [US1] Criar `{APT}/suporte/ExecutorAcoesSimuladoTeste.java` (a especificação executável do workflow da 004): percorre `acoes[]` em ordem **sem parar na primeira falha** e traduz para o WireMock da API de bots conforme a tabela de [contracts/canal-telegram-interno.md §4](./contracts/canal-telegram-interno.md) (`MENSAGEM`→`sendMessage` com `parse_mode=HTML` e `reply_markup`, `EDICAO`→`editMessageText`, `DOCUMENTO`→`GET /documentos/{id}` + `sendDocument`, `RESPOSTA_CALLBACK`→`answerCallbackQuery`, `MENSAGEM_ETAPA`→send/edit) e envia as confirmações por `POST /confirmacoes`; tipo desconhecido ⇒ confirma `FALHOU/ACAO_NAO_SUPORTADA` e segue, ignorando campos desconhecidos; consulta `GET /acoes/{id}` antes de executar ação repetida (pula se `EXECUTADA` ou `FALHOU`); aplica a tabela "erro do Telegram → situação" de [contracts/canal-telegram-interno.md §4](./contracts/canal-telegram-interno.md) (ex.: "message is not modified" ⇒ `EXECUTADA`; mensagem inexistente/não editável ⇒ `FALHOU/MENSAGEM_NAO_EDITAVEL`); para `DOCUMENTO` faz **um único** `GET` e, em `404`/`5xx`, confirma `FALHOU/DOCUMENTO_INDISPONIVEL` sem repetir por conta própria. Teste próprio `{APT}/suporte/ExecutorAcoesSimuladoVerificacaoTeste.java` com ação de tipo desconhecido e campo extra (FR-003a) (depende de T037)
- [X] T039 [US1] Em `backend/aplicacao/pom.xml`, configurar o `maven-failsafe-plugin` para **duas execuções** dos ITs de conversa e de paridade (`entrada/telegram/*ConversaIT`, `paridade/RoteiroParidade*IT`): `telegram.transporte=webhook` e `telegram.transporte=interno`; as duas devem passar para o build passar. Conferir `.github/workflows/ci.yml` e `infra/compose.test.yaml` para garantir que a CI exige as duas execuções (R-13)
- [X] T040 [P] [US1] Criar `{APT}/entrada/telegram/canal/CanalTelegramContratoIT.java` (V2, C1–C3, C13–C14): valida toda resposta de `/atualizacao`, `/confirmacoes` e `/acoes/{id}` contra `acoes-v1.schema.json` e as rotas/códigos HTTP contra `canal-telegram-interno.openapi.yaml` (`swagger-request-validator`, como em `suporte/ContratoApiBase`); cenários: `DOCUMENTO` com `nomeArquivo`, `tipoConteudo` e `legenda` iguais ao que o envio direto usa; teste de latência `@Tag("latencia")` não bloqueante que mede p95 de `/atualizacao` (conferência com PDF simulado, 50 chamadas) contra o alvo de 2 s de SC-003 e imprime o resultado; não vinculado ⇒ `CODIGO_NECESSARIO` com id `u<N>-0`; toque em "Aprovar relatório" ⇒ `RESPOSTA_CALLBACK` antes de `CONFIRMAR_APROVACAO` com botões; relatório pronto ⇒ mensagens divididas + `DOCUMENTO` + botões na ordem de hoje; corpo ilegível/tipo não suportado ⇒ `acoes: []`; grupo ⇒ `SOMENTE_CONVERSA_PRIVADA`; versão desconhecida ⇒ `400`
- [X] T041 [P] [US1] Criar `{APT}/entrada/telegram/canal/AtualizacaoIdempotenciaIT.java` (V6, C4–C6, US1.5): mesmo `update_id` sem confirmar ⇒ mesmas ações e ids; após confirmar tudo ⇒ `acoes: []`; após confirmar parte ⇒ só o resto; duas chamadas paralelas (`CountDownLatch`) ⇒ um só processamento do interpretador; estado da conversa gravado antes da resposta; **o backend não envia nada à API de bots** ao responder (FR-010: contar no WireMock, antes de o executor começar, zero chamadas a `sendMessage`/`editMessageText`/`sendDocument`/`answerCallbackQuery`; `getFile` do áudio é exceção legítima, FR-028); linha perdida (`total_acoes` nulo, > 30 s) ⇒ `ERRO_INTERNO` `u<N>-e0`; update do webhook antigo repetido ⇒ `acoes: []`
- [X] T042 [US1] Acrescentar a `SegurancaInternaIT` (T028) os casos de `POST /atualizacao` com segredo do webhook ausente/errado ⇒ `401` sem corpo, com token de serviço correto (V3, US2.6, C8); e `GET /acoes/...` com `proximo` aceito (C7)
- [X] T043 [US1] Rodar `docker compose -f infra/compose.test.yaml run --rm testes-backend` e confirmar V1/SC-001: todos os `*ConversaIT` e `RoteiroParidade*IT` passam **nos dois modos** sem nenhuma expectativa alterada; registrar qualquer divergência como bug do mapeador/controlador, nunca como alteração de expectativa

**Checkpoint**: US2 + US1 = MVP funcional e testável: o n8n (simulado) opera a conversa completa pela nova porta.

---

## Phase 5: User Story 4 - Obter o PDF por identificador (Priority: P2)

**Goal**: `GET /interno/v1/canal/telegram/documentos/{documentoId}` devolve os bytes do PDF por identificador opaco, com a mesma checagem de dono, e `404` indistinguível para qualquer identificador inválido.

**Independent Test**: gerar relatório com PDF, ler o id da ação `DOCUMENTO`, buscar os bytes e comparar com o PDF do portal; testar inexistente, vencido, URL/caminho e repetição dentro do prazo (V14).

- [X] T044 [US4] Criar `{NUC}/casodeuso/canal/ObterDocumentoCanalCasoDeUso.java`: recebe o hash, busca em `DocumentoCanalRepositorio`, rejeita inexistente/vencido (`valido(agora)`) com resultado único "não encontrado", delega a `ObterDocumentoRelatorioCasoDeUso` (`conferencia`/`oficial`) com o `professor_id` gravado **repetindo** a checagem de dono e associação; documento que sumiu ⇒ "não encontrado"; falha real de leitura propaga exceção (nunca bytes parciais). Teste `{NUT}/casodeuso/canal/ObterDocumentoCanalCasoDeUsoTeste.java` (depende de T011, T013, T015)
- [X] T045 [US4] Em `{APP}/configuracao/CasosDeUsoConfiguracao.java`, registrar `ObterDocumentoCanalCasoDeUso`; em `{APP}/entrada/telegram/canal/CanalTelegramControlador.java`, adicionar `GET /documentos/{documentoId}`: recusar com `404` **antes do banco** qualquer valor fora de 43 caracteres Base64url (URL, caminho, tamanho ou alfabeto errado, FR-023); calcular SHA-256 do id e chamar o caso de uso; sucesso `200` `application/pdf` com `Content-Disposition: inline; filename="…"` (nome sem identificadores internos), `Cache-Control: no-store`; desconhecido, vencido, malformado ⇒ `404` sem corpo e indistinguíveis; falha de leitura ⇒ `5xx` (depende de T044, T036)
- [X] T046 [P] [US4] Criar `{APT}/entrada/telegram/canal/DocumentoCanalContratoIT.java` (V14, C12, SC-007): id válido ⇒ bytes **idênticos** ao PDF que `DocumentoControlador` entrega para o mesmo relatório (conferência e oficial), com tipo e nome corretos; inexistente, vencido (via `RelogioAjustavel`), malformado e `http://…`/`../` ⇒ `404` sem corpo e com respostas indistinguíveis; sem token ⇒ `401`; repetição do `GET` dentro do prazo continua válida (US4.4); nenhuma chamada de rede originada do id (depende de T045)

**Checkpoint**: PDF por id funcional (já exercitado de ponta a ponta pelo executor simulado da US1).

---

## Phase 6: User Story 3 - Entrega proativa ao orquestrador sem perder mensagens (Priority: P2)

**Goal**: ações sem update (resultado de áudio, etapas, demora, avisos entre canais, compensações) são gravadas na outbox e entregues ao n8n com retentativa, `Idempotency-Key`, ordem por chat e confirmação; mensagem de etapa regrava o `mensagemId`; falha de edição gera compensação; nada se perde por até 24 h nem se duplica.

**Independent Test**: com o orquestrador simulado fora do ar, concluir um processamento e ver a ação `PENDENTE` ser reentregue (mesmos ids) quando ele volta; com respostas lentas/duplicadas, executar uma única vez (V8–V11, V13).

### Porta de saída e etapa como `MensagemEtapa`

- [X] T047 [US3] Em `{NUC}/porta/FilaTrabalhoPorta.java`, adicionar `registrarMensagemEtapa(UUID trabalhoId, long mensagemId)`; implementar em `{APP}/saida/persistencia/FilaTrabalhoPostgresAdaptador.java` (hoje o método existe só na classe concreta usada por `MensageriaTelegramAdaptador`) e ajustar os dublês que implementam a porta (`{NUT}/dubles/DublesProcessamento.java`) (e consultar o id gravado para o despacho)
- [X] T048 [US3] `SaidaTelegram.MensagemEtapa` e `MensageriaTelegramAdaptador.enviarEtapa` **já existem** (edita no lugar e regrava o id da etapa); não alterá-los. Em `{APP}/saida/telegram/NotificacaoTelegramAdaptador.java`, trocar as `Edicao` de etapa e do "Pronto" (hoje em torno das linhas 97 e 254) por `MensagemEtapa(chatId, editarMensagemId, texto, trabalhoId)` e manter um teste que prove que o envio direto continua editando no lugar e regravando o id (comportamento atual inalterado) (depende de T047)
- [X] T049 [US3] Criar `{APP}/entrada/telegram/PublicacaoAcaoTelegramPorta.java` (porta de saída da notificação; assinatura `publicar(String eventoId, List<SaidaTelegram> saidas)`; a entrada a define, a saída implementa)
- [X] T050 [P] [US3] Criar `{APP}/saida/telegram/PublicacaoDiretaTelegramAdaptador.java` (`@ConditionalOnProperty(relatorios.canal-telegram.entrega-proativa=DIRETA, matchIfMissing=true)`): comportamento de hoje, delegando a `MensageriaTelegramAdaptador` (depende de T048, T049)
- [X] T051 [US3] Estender `{APP}/entrada/telegram/canal/AcaoCanalMapeador.java` e seu teste (T032): mapear `MensagemEtapa` para `AcaoMensagemEtapaDto` (com `trabalhoId`; `editarMensagemId` preenchido só no despacho) e gerar a compensação "mesma ação sem `editarMensagemId`"; ids proativos `p:resultado:<trabalhoId>:<pos>`, `p:etapa:<trabalhoId>:<ETAPA>:<pos>`, `p:demora:<trabalhoId>:<pos>` (data-model §1.2.1) (depende de T048)
- [X] T052 [US3] Criar `{APP}/saida/telegram/PublicacaoOutboxTelegramAdaptador.java` (`@ConditionalOnProperty(...=ORQUESTRADOR)`): mapeia com `AcaoCanalMapeador` e grava com `GravarAcoesCanalCasoDeUso` (origem `PROATIVA`, idempotente por evento, FR-014); `saida` já depende de tipos da `entrada` (ex.: `SaidaTelegram` em `MensageriaTelegramAdaptador`), então este adaptador usa `AcaoCanalMapeador` diretamente; a regra proibida é só `entrada` → `saida` (depende de T049, T051)
- [X] T053 [US3] Refatorar `{APP}/saida/telegram/NotificacaoTelegramAdaptador.java`: deixa de depender de `MensageriaTelegramAdaptador` e passa a depender de `PublicacaoAcaoTelegramPorta`; etapas e o "Pronto" emitem `MensagemEtapa(chatId, id, texto, trabalhoId)` em vez de `Edicao`; derivar o `eventoId` (`resultado:<trabalhoId>`, `etapa:<trabalhoId>:<ETAPA>`, `demora:<trabalhoId>`); mesmos textos, foco só no caso do Telegram, aviso entre canais com botão "Continuar no chat" sem mudar foco (US3.1, US3.2) (depende de T050, T052)

### Entrega, confirmação completa e retenção

- [X] T054 [US3] Criar `{NUC}/casodeuso/canal/EntregarAcoesPendentesCasoDeUso.java`: reserva o próximo lote devido (portão de ordem por chat, `confirmacao-max-segundos`), descarta (`DESCARTADA`, com registro) ações de professor sem vínculo (usar `VinculoTelegramRepositorio`), marca `FALHA_DEFINITIVA` se `agora ≥ prazo_em`, resolve `editarMensagemId` das etapas pelo id gravado no trabalho (`FilaTrabalhoPorta`), chama `OrquestradorCanalPorta.entregar` **fora de transação** com o lote inteiro, grava `ENTREGUE` no aceite ou `registrarFalhaEntrega` na recusa **sempre por `atualizarSeVersao`**: se devolver `false` (a confirmação chegou durante o HTTP), recarregar a ação e **não** sobrescrever o estado já final; devolve contagens para métricas. Teste `{NUT}/casodeuso/canal/EntregarAcoesPendentesCasoDeUsoTeste.java` (recusa → reagendamento, prazo, descarte, ordem por chat, chats independentes, confirmação que chega antes do 2xx, timeout do portão) (depende de T010, T012, T013, T047)
- [X] T055 [US3] Completar `{NUC}/casodeuso/canal/ConfirmarExecucaoAcoesCanalCasoDeUso.java` e seu teste (T034): `EXECUTADA` de `MENSAGEM_ETAPA` com `trabalhoId` exige `mensagemId` e o grava por `registrarMensagemEtapa` **antes** de marcar `EXECUTADA`; `FALHOU` com `MENSAGEM_NAO_EDITAVEL` e `compensacao` presente cria **uma** ação `<acaoId>-c` (origem `COMPENSACAO`, lote `c:<acaoId>`, `PENDENTE`) com a alternativa; demais códigos (`CHAT_INDISPONIVEL`, `ACAO_NAO_SUPORTADA`, `DOCUMENTO_INDISPONIVEL`, `LIMITE_DE_TAXA`, `ERRO_TELEGRAM` e qualquer código desconhecido, tratado como `ERRO_TELEGRAM`) só registram; uma `COMPENSACAO` (`-c`) que falha **não** gera nova compensação; falha de edição de etapa regrava o `mensagemId` pela compensação (depende de T034, T047)
- [X] T056 [US3] Criar `{NUC}/casodeuso/canal/LimparAcoesCanalCasoDeUso.java`: zera `payload`/`compensacao` de ações finais com mais de `retencao-payload-horas`, apaga linhas finais com mais de `retencao-registro-dias` e `documento_canal` vencido. Teste `{NUT}/casodeuso/canal/LimparAcoesCanalCasoDeUsoTeste.java`
- [X] T057 [US3] Criar `{APP}/saida/telegram/OrquestradorN8nAdaptador.java` (implementa `OrquestradorCanalPorta`; **o nome `n8n` só pode existir em `aplicacao`**): `java.net.http.HttpClient` com timeouts de conexão e resposta configuráveis, sem seguir redirecionamentos, `POST {orquestrador-url}` com `LoteEntregaDto` JSON, cabeçalhos `X-Canal-Entrega-Token` e `Idempotency-Key: <loteId>`; qualquer `2xx` = `ACEITO`, o resto (timeout, erro de conexão, `5xx`, `4xx`) = `RECUSADO(codigo)` sem encerrar tentativas; **nenhum** endereço vindo de payload; em log só `loteId`, status HTTP, código de situação e o tipo da exceção: **nunca** corpo, `getMessage()` de exceção, URL do orquestrador (webhooks do n8n podem trazer segredo no caminho) nem token; garantir que o log interno do `HttpClient` do JDK fique desligado (depende de T030, T001)
- [X] T058 [US3] Criar em `{APP}/saida/fila/` `EntregaAcoesCanalTarefa.java` (`@Scheduled`, `@Profile("!carga")`, threads virtuais, só ativa se houver `orquestrador-url`; intervalo `intervalo-despacho-ms`, até `lote-max-por-ciclo` lotes) chamando `EntregarAcoesPendentesCasoDeUso`, e as métricas Micrometer de R-15: `canal_acoes_pendentes`, `canal_acao_pendente_mais_antiga_segundos`, `canal_entregas_total{resultado}`, `canal_acoes_falha_definitiva_total` (+ log `WARN` por ação sem dado de aluno), `canal_acoes_sem_confirmacao_total`, `canal_confirmacoes_total{situacao,codigo}` (depende de T054, T057)
- [X] T059 [P] [US3] Criar `{APP}/saida/fila/LimpezaAcoesCanalTarefa.java` (diária, na janela de `VarreduraAcessoTarefa`) chamando `LimparAcoesCanalCasoDeUso` (depende de T056)
- [X] T060 [US3] Em `{APP}/configuracao/CasosDeUsoConfiguracao.java`, registrar `EntregarAcoesPendentesCasoDeUso` e `LimparAcoesCanalCasoDeUso`; em uma configuração de `{APP}/configuracao/` (p.ex. `CanalTelegramConfiguracao.java`), **falhar a subida** se `entrega-proativa=ORQUESTRADOR` e `orquestrador-url` estiver vazio (R-10), ou se `orquestrador-url` não for `https` e o host não for de rede interna (nome de serviço sem ponto, `localhost` ou IP privado), por causa do Princípio IV, e se `leaseSegundos` ≤ `conexaoTimeoutMs` + `respostaTimeoutMs`, e garantir que existe exatamente um bean de `PublicacaoAcaoTelegramPorta` (depende de T054, T056, T050, T052)

### Testes da história

- [X] T061 [P] [US3] Criar `{APT}/saida/telegram/EntregaAcoesCanalIT.java` (V8–V11, C9, C11, SC-004) com WireMock do orquestrador e `RelogioAjustavel`: indisponível ⇒ `PENDENTE`, reenvio com `acaoId`/`loteId`/`Idempotency-Key` idênticos em intervalos crescentes, entrega ao voltar, processamento do relatório intacto (FR-015); `5xx`/`4xx`/timeout continuam `PENDENTE` até o prazo, depois `FALHA_DEFINITIVA` (log sem dado de aluno, métrica incrementada); ordem por chat e portão de confirmação (lote 2 só após confirmação do lote 1 ou 60 s; chats distintos em paralelo); notificar o mesmo trabalho duas vezes ⇒ uma só ação; dois despachantes ⇒ uma só entrega por lote; vínculo desfeito ⇒ `DESCARTADA`; **corrida**: a confirmação `EXECUTADA` chega enquanto o HTTP do despacho ainda está em andamento (WireMock com atraso) ⇒ o estado final permanece `EXECUTADA`, nunca volta a `ENTREGUE`; **reinício**: lote reservado cujo despachante "morre" (lease vence com `RelogioAjustavel`) ⇒ reentregue com os mesmos ids; **idempotência do receptor (FR-013, SC-004)**: o mesmo lote entregue duas vezes ao `ExecutorAcoesSimuladoTeste` ⇒ o WireMock da API de bots recebe **uma só** chamada por ação; **métricas**: afirmar `canal_acoes_pendentes`, `canal_acao_pendente_mais_antiga_segundos`, `canal_entregas_total{resultado}`, `canal_acoes_falha_definitiva_total`, `canal_acoes_sem_confirmacao_total` e `canal_confirmacoes_total{situacao,codigo}`; todo corpo enviado ao n8n validado contra `LoteEntrega` do schema
- [X] T062 [P] [US3] Criar `{APT}/saida/telegram/ConfirmacaoCompensacaoIT.java` (V7, C10, FR-007b/FR-017): `FALHOU` + `MENSAGEM_NAO_EDITAVEL` numa `EDICAO` ⇒ nasce `<id>-c` com o mesmo conteúdo, entregue uma vez e sem repetir a edição; falha na edição de etapa ⇒ nova mensagem de etapa e `mensagemId` regravado no trabalho (a etapa seguinte edita a mensagem nova); `MENSAGEM_ETAPA` `EXECUTADA` sem `mensagemId` ⇒ `400`; "message is not modified" numa edição ⇒ executor confirma `EXECUTADA` e **nenhuma** compensação nasce (sem mensagem duplicada para o professor); `codigoMotivo` desconhecido ⇒ tratado como `ERRO_TELEGRAM` sem `400` e sem perder as confirmações irmãs do lote; compensação `-c` que falha não gera outra; confirmação repetida/divergente/desconhecida ⇒ `ignoradas`
- [X] T063 [US3] Estender `{APT}/saida/telegram/NotificacaoTelegramAdaptadorIT.java` com o modo `ORQUESTRADOR` (V13, US3.1, US3.2): resultado de áudio do Telegram (perguntas, conferência com PDF, falha) com mesmas mensagens e ordem de hoje e relatório em foco; aviso de trabalho da Web com vínculo ⇒ aviso curto com "Continuar no chat" sem mudar o foco; manter intactos os casos existentes do modo `DIRETA` (depende de T053, T060)

**Checkpoint**: entrega proativa com garantia, ordem, idempotência, compensação e retenção.

---

## Phase 7: User Story 5 - Áudio e mensagens antigas continuam como hoje (Priority: P3)

**Goal**: áudio nunca passa pelo orquestrador; webhook antigo intacto; exatamente um caminho proativo ativo por configuração.

**Independent Test**: voz dentro e acima do limite pelo transporte `interno` sem nenhum byte/caminho/URL de áudio em ações, outbox ou logs; alternar `DIRETA`/`ORQUESTRADOR` e ver as mensagens proativas saírem por um caminho só (V12, V15, V17, V18).

- [X] T064 [P] [US5] Em `{APT}/entrada/telegram/RegistroAulaConversaIT.java` e `{APT}/privacidade/NaoRetencaoAudioIT.java` (V15, C13, US5.1/US5.2), garantir no modo `interno`: voz dentro do limite ⇒ `AUDIO_RECEBIDO_CONFIRMAR_ALUNO` sem bytes, caminho de arquivo ou endereço de download na resposta, em `acao_canal` e nos logs; áudio acima do limite ⇒ `AUDIO_MUITO_GRANDE` sem baixar o arquivo; o executor simulado jamais chama `getFile` nem baixa áudio
- [X] T065 [US5] Estender `{APT}/saida/telegram/NotificacaoTelegramAdaptadorIT.java` (V12, US5.3, US5.4) com a exclusividade: `DIRETA` ⇒ mensagem sai só pela API de bots e **nada** entra em `acao_canal`; `ORQUESTRADOR` ⇒ entregue só ao orquestrador e **nenhuma** chamada à API de bots; subida com `ORQUESTRADOR` e URL vazia falha (depende de T063)
- [X] T066 [P] [US5] Em `{APT}/arquitetura/RegrasAdaptadoresTeste.java` (V18, FR-033), acrescentar regras ArchUnit: controladores de `entrada.telegram.canal` terminam em `Controlador`; records de borda terminam em `Dto`/`Requisicao`/`Resposta`; `entrada.interno` não depende de `saida`; `entrada` continua sem depender de `saida`; núcleo sem Spring nem `n8n` (esta última já coberta por T005)
- [X] T067 [US5] Confirmar V17/US5.5: todos os ITs de conversa e de paridade no modo `webhook` passam sem nenhuma alteração de arquivo de teste de expectativa (executar `testes-backend` e anexar o resultado ao PR)

**Checkpoint**: todas as histórias funcionais; webhook antigo e envio direto preservados.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: privacidade, documentação, cobertura e validação final

- [X] T068 [P] Estender `{APT}/privacidade/LogSemDadosSensiveisIT.java` (V16, FR-029, SC-006) para o fluxo interno e a entrega: varrer logs (e corpos `401/404/5xx`) após conversa interna, entrega, retentativa, falha definitiva, compensação, subida sem token (aviso) e requisição ilegível, sem CPF, nome de aluno, texto de relatório, transcrição nem payload; **semear valores conhecidos** (token de serviço com formato que o mascarador não reconhece, ex.: `tok-teste-123`, `token-proximo`, `orquestrador-token`, segredo do webhook do Telegram, `orquestrador-url` com segredo no caminho e um `documentoId` real) e afirmar que **nenhum** aparece em log, inclusive nos logs de 404 de `GET /documentos/{id}` e em exceções de parsing do update; criar `{APT}/saida/persistencia/LimpezaAcoesCanalIT.java` confirmando `payload`/`compensacao` nulos após a retenção, linha final removida após `retencao-registro-dias` e `documento_canal` vencido removido
- [X] T069 [P] Anotar os artefatos da 001 sem reescrever histórico (Complexity Tracking / R-01): em `specs/001-fluxo-relatorios/research.md` (R-04), `specs/001-fluxo-relatorios/plan.md` (Summary, linha II do Constitution Check, Structure Decision) e `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` §1, acrescentar nota "transporte do Telegram agora via n8n; ver `specs/002-api-interna-canal-telegram/research.md` R-01"
- [X] T070 Rodar `docker compose -f infra/compose.test.yaml run --rm testes-backend` (`verify`) e confirmar JaCoCo ≥ 90% de linhas **e** branches no `nucleo` (V19, FR-032, SC-008); se algum branch de `dominio.canal`/`casodeuso.canal` estiver descoberto, adicionar testes unitários em `{NUT}/dominio/canal/` ou `{NUT}/casodeuso/canal/` (não relaxar o gate)
- [X] T071 Executar a validação completa de [quickstart.md](./quickstart.md) (V1 a V20) e conferir que os contratos em `specs/002-api-interna-canal-telegram/contracts/` coincidem com o que `CanalTelegramContratoIT` e `DocumentoCanalContratoIT` validam; limpar com `docker compose -f infra/compose.test.yaml down -v`
- [X] T072 Fechamento (constituição, Princípio VIII): commitar na branch `002-api-interna-canal-telegram` e abrir PR para `main`, **sem mesclar e sem auto-merge**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup; **bloqueia** todas as histórias.
- **US2 (Phase 3)**: depende da Foundational (usa T001 e T021/T027 do webhook); precede a US1.
- **US1 (Phase 4)**: depende da Foundational **e da US2** (a rota só é alcançável pela cadeia `/interno/**`).
- **US4 (Phase 5)**: depende da US1 (mesmo controlador, T036) e da emissão de documento (T015).
- **US3 (Phase 6)**: depende da Foundational e da US1 (`AcaoCanalMapeador`, controlador de confirmações); integra com a US4 só pelo executor simulado.
- **US5 (Phase 7)**: depende da US1 (modo `interno`) e da US3 (as duas implementações da porta).
- **Polish (Phase 8)**: depende de todas as histórias desejadas.

### User Story Dependencies

- **US2 (P1)**: independente das demais (testável com rota inexistente, T028).
- **US1 (P1)**: precisa da US2; é o MVP junto com ela.
- **US4 (P2)**: precisa do controlador da US1; independente da US3.
- **US3 (P2)**: precisa do mapeador/confirmação da US1; independente da US4.
- **US5 (P3)**: validação/endurecimento sobre US1 e US3.

### Within Each Story

- Domínio/casos de uso do núcleo → adaptadores → controlador → testes de integração.
- Testes unitários do núcleo acompanham a classe (mesma tarefa) e devem passar antes dos ITs.
- Tarefas que editam o mesmo arquivo são sequenciais: `CanalTelegramControlador.java` (T036 → T045), `AcaoCanalMapeador.java` (T031 → T051), `ConfirmarExecucaoAcoesCanalCasoDeUso.java` (T034 → T055), `NotificacaoTelegramAdaptadorIT.java` (T063 → T065), `SegurancaInternaIT.java` (T028 → T042), `CasosDeUsoConfiguracao.java` (T035 → T045 → T060).

### Parallel Opportunities

- Setup: T002, T003, T004, T005 em paralelo após T001.
- Foundational: T008, T009, T011, T012 em paralelo; depois T013; T016, T017, T019 em paralelo (após as portas).
- US2: T023 em paralelo com a leitura de T001; T024→T025→T026 sequenciais.
- US1: T029, T030 em paralelo; T032, T033, T034 em paralelo após T031; T040 e T041 em paralelo após T039.
- US3: T050 e T059 em paralelo; T061 e T062 em paralelo ao final.
- US4 e US3 podem ser tocadas por pessoas diferentes depois da US1 (arquivos distintos, exceto `CasosDeUsoConfiguracao.java`).

---

## Parallel Example: User Story 1

```bash
# Após a US2 e a Foundational, DTOs em paralelo:
Task: "T029 DTOs de ação v1 em backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/canal/"
Task: "T030 RespostaAtualizacao/RequisicaoConfirmacao/RespostaConfirmacao/RespostaEstadoAcao/LoteEntregaDto"

# Depois de T031 (mapeador) e T035, testes e casos de uso em paralelo:
Task: "T032 AcaoCanalMapeadorTeste"
Task: "T033 ObterAcoesNaoConfirmadasDoUpdateCasoDeUso + teste"
Task: "T034 ConfirmarExecucaoAcoesCanalCasoDeUso (base) + teste"

# Depois de T039:
Task: "T040 CanalTelegramContratoIT"
Task: "T041 AtualizacaoIdempotenciaIT"
```

---

## Implementation Strategy

### MVP First (US2 + US1)

1. Phase 1: Setup → Phase 2: Foundational (incluindo T022, regressão do webhook).
2. Phase 3: US2 (segurança) → validar `SegurancaInternaIT`.
3. Phase 4: US1 (conversa pela nova porta) → **PARAR e validar**: V1/SC-001 nos dois modos (T043).
4. Esse recorte já prova a troca de transporte para respostas a update; a entrega proativa ainda sai pelo caminho `DIRETA` (padrão), sem regressão.

### Incremental Delivery

1. Setup + Foundational → base pronta.
2. + US2 → área interna isolada.
3. + US1 → conversa completa pela nova porta (**MVP**).
4. + US4 → PDF por identificador (já usado pelo executor simulado; aqui ganha o contrato próprio).
5. + US3 → entrega proativa com garantia (liga `ORQUESTRADOR` só na feature 004).
6. + US5 → endurecimento (áudio, exclusividade, ArchUnit) e Polish.

### Parallel Team Strategy

1. Todos fazem Setup + Foundational (a migração T006 e as portas T012 destravam os demais).
2. Pessoa A: US2 → US1. Pessoa B (após T012/T016): início de US3 no núcleo (T054, T056) e dublês.
3. Depois da US1: A faz US4; B conclui US3; juntos US5 e Polish.

---

## Notes

- [P] = arquivos diferentes e sem dependência incompleta.
- `[Story]` mapeia a tarefa à história da spec para rastreabilidade (US1 a US5).
- Nunca alterar expectativas de teste existentes para fazer o modo `interno` passar (SC-001): corrigir o mapeador/controlador.
- O `n8n`, workflows, `compose`, Caddy, troca do webhook registrado no Telegram e remoção do envio direto **não** são desta feature (feature 004).
- Decisões do dono do produto ainda em aberto (a implementação segue o padrão indicado até a resposta): (1) falha de `DOCUMENTO` (`DOCUMENTO_INDISPONIVEL`) hoje **não** gera compensação, então o professor pode ficar sem o PDF sem aviso, igual ao envio direto atual; criar um aviso exige texto novo em `mensagens.md` (research R-07, "Em aberto"); (2) lista final de `codigoMotivo`.
- Pendência de interpretação registrada (R-11): FR-029 × texto da ação na outbox; seguir a leitura do plano (nada em logs, payload zerado após a retenção) e levar a confirmação ao dono do produto.
- Commitar por tarefa ou grupo lógico; parar em cada checkpoint para validar a história isoladamente.

## Phase 9: Convergence

- [X] T073 [P] Escrever testes que falham para a retenção de ações nunca confirmadas: em `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/canal/AcaoCanalTeste.java` e `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/canal/LimparAcoesCanalCasoDeUsoTeste.java`, e em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/persistencia/LimpezaAcoesCanalIT.java`: uma ação `ENTREGUE` (resposta a update ou proativa aceita) cuja confirmação nunca chega e cujo `prazo_em` venceu passa a estado final com código `SEM_CONFIRMACAO`, tem `payload` e `compensacao` zerados depois de `retencao-payload-horas` e a linha removida depois de `retencao-registro-dias`; uma ação `ENTREGUE` ainda dentro do prazo continua reentregável pelo update repetido per FR-029 (partial)
- [X] T074 Finalizar as ações `ENTREGUE` sem confirmação depois do `prazo_em`: acrescentar a `AcaoCanalRepositorio` (`backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/porta/AcaoCanalRepositorio.java`) a operação de expirar entregues vencidas, implementá-la em `AcaoCanalRepositorioAdaptador` e `AcaoCanalJpa` (`backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/saida/persistencia/`), chamá-la em `LimparAcoesCanalCasoDeUso` antes do expurgo, atualizar o dublê `AcaoCanalRepositorioEmMemoria` e acrescentar o código `SEM_CONFIRMACAO` a `AcaoCanal`, sem contá-lo na métrica `canal_acoes_falha_definitiva_total` e sem alterar o estado do relatório per FR-029 (partial)
- [X] T075 [P] Escrever em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/canal/CanalTelegramContratoIT.java` os testes de erro inesperado na rota `POST /atualizacao`: interpretador que lança e mapeamento de ações que lança, para mensagem de texto e para toque em botão, devolvendo `ERRO_INTERNO` com o texto do catálogo, a resposta ao toque primeiro nos toques, ids estáveis `u<id>-e<pos>` e a mesma resposta na repetição do update; repetir pelo webhook antigo em `InicioBuscaConversaIT` per FR-008 (missing)
- [X] T076 [P] Estender `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/privacidade/LogCanalInternoSemDadosSensiveisIT.java` com um interpretador que lança exceção cuja mensagem e causa contêm nome de aluno, texto do relatório e CPF: nenhum desses valores pode aparecer nos logs (inclusive no stack trace) nem na resposta da rota interna per FR-029 (missing)
- [X] T077 Em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/ProcessadorUpdateTelegramServico.java`, trocar `LOG.error("telegram-erro-interpretacao updateId={}", ..., erro)` por registro só de `updateId` e do tipo da exceção, como os demais logs do canal, mantendo a depuração por métrica e código de situação per FR-029 (partial)
- [X] T078 Obter do dono do produto as decisões que seguem em aberto e registrá-las: (1) leitura de FR-029 diante do texto do aluno que a própria ação carrega (research R-11); (2) falha `DOCUMENTO_INDISPONIVEL` sem compensação, que pode deixar o professor sem o PDF e sem aviso (research R-07); (3) lista final de `codigoMotivo`. Emendar a spec com `/speckit-clarify` e ajustar o contrato `specs/002-api-interna-canal-telegram/contracts/canal-telegram-interno.md`, abrindo tarefa de implementação para o que mudar per FR-029 (partial)
- [X] T079 Limitar a próxima tentativa ao `prazo_em` em `AcaoCanal.registrarFalhaEntrega` e `PoliticaRetentativa` (`backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/dominio/canal/`), para que a falha definitiva seja registrada no prazo de 24 h e não até 15 min depois, com testes em `PoliticaRetentativaTeste`, `AcaoCanalTeste` e no caso de 24 h de `EntregaAcoesCanalIT` per FR-018 (partial)
- [X] T080 [P] Escrever testes que falham para a compensação de `DOCUMENTO_INDISPONIVEL`: em `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/telegram/ConfirmacaoCompensacaoIT.java`, `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/casodeuso/canal/ConfirmarExecucaoAcoesCanalCasoDeUsoTeste.java`, `backend/nucleo/src/test/java/br/com/felipeblaft/relatorios/dominio/canal/AcaoCanalTeste.java` e `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/entrada/telegram/canal/AcaoCanalMapeadorTeste.java`: a ação `DOCUMENTO` guarda uma compensação (mensagem curta com o botão para pedir o PDF de novo); `FALHOU` com `DOCUMENTO_INDISPONIVEL` cria uma só compensação `<acaoId>-c` entregue pelo mesmo caminho; a compensação que falha não gera outra; PDF de conferência e PDF oficial do histórico usam o botão certo; a compensação não leva `documentoId` nem texto do relatório per FR-007c (missing)
- [X] T081 Implementar a compensação de `DOCUMENTO_INDISPONIVEL`: acrescentar o texto novo e aditivo ao catálogo em `backend/aplicacao/src/main/resources/mensagens/mensagens_pt_BR.properties` e a `specs/001-fluxo-relatorios/contracts/mensagens.md` (sem alterar nenhum texto existente), gravar a compensação da ação `DOCUMENTO` em `backend/aplicacao/src/main/java/br/com/felipeblaft/relatorios/entrada/telegram/canal/AcaoCanalMapeador.java`, e estender `AcaoCanal.criarCompensacao` e `ConfirmarExecucaoAcoesCanalCasoDeUso` (`backend/nucleo/src/main/java/br/com/felipeblaft/relatorios/`) para criar a compensação também quando o código é `DOCUMENTO_INDISPONIVEL`, atualizando `specs/002-api-interna-canal-telegram/data-model.md` e `contracts/acoes-v1.schema.json` se o formato mudar per FR-007c (missing)


## Phase 10: Convergence

- [ ] T082 Fortalecer `backend/aplicacao/src/test/java/br/com/felipeblaft/relatorios/saida/persistencia/LimpezaAcoesCanalIT.java` no caso de ação `ENTREGUE` nunca confirmada: afirmar que `canal_acoes_falha_definitiva_total` continua em zero depois da expiração por `SEM_CONFIRMACAO`, que o estado do relatório não muda e que o update repetido pela rota `POST /atualizacao` ainda devolve a ação dentro do prazo e deixa de devolvê-la depois da expiração (hoje o teste confere só o estado, o código e a retenção, e usa `buscarPorUpdate`) per FR-029 (partial)
