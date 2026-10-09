# Data Model: API interna de canal do Telegram

**Feature**: `002-api-interna-canal-telegram` | **Data**: 2026-10-02 | **Plano**: [plan.md](./plan.md) | **Pesquisa**: [research.md](./research.md)

Convenções herdadas de `specs/001-fluxo-relatorios/data-model.md`: nomes em português sem acento
(Princípio VI), `snake_case` nas tabelas, `timestamptz` em UTC, `jsonb` para estruturas, e o
domínio (`nucleo`) não conhece as tabelas (o adaptador converte por `*Mapeador`). Nenhuma tabela
existente perde coluna ou dado; a migração é aditiva e compatível com o webhook antigo em execução.

## Visão geral

```text
update_telegram_processado 1 ──── N acao_canal   (respostas a update: update_id)
trabalho_processamento     1 ──── N acao_canal   (etapas e resultados: trabalho_id, sem FK)
acao_canal                 N ──── 0..1 documento_canal (via payload DOCUMENTO; sem FK)
acao_canal                 1 ──── 0..1 acao_canal (compensação: id = <id>-c)
```

Não há chaves estrangeiras para `trabalho_processamento`, `professor` nem `update_telegram_processado`
em `acao_canal`: a retenção da outbox (7 dias) e a de `update_telegram_processado` (7 dias) são
independentes, e o descarte de uma ação por vínculo desfeito não pode depender de a linha pai existir.

## 1. Migração `V6__canal_telegram_interno.sql`

Única migração da feature (a outbox exige tabela, R-14).

### 1.1 Alteração de `update_telegram_processado` (reaproveitada, V2)

| Coluna nova | Tipo | Nulo | Significado |
|---|---|---|---|
| `total_acoes` | integer | sim | quantidade de ações gravadas para o update; nulo = ainda processando (ou perdido, R-04 item 5) |
| `origem` | varchar(10) | sim | `INTERNO` quando o update entrou pela rota interna; nulo nas linhas do webhook antigo |

`CHECK (total_acoes IS NULL OR total_acoes >= 0)` e `CHECK (origem IS NULL OR origem = 'INTERNO')`.
A chave (`update_id`) e o índice por `recebido_em` não mudam. `registrarSeNovo` continua atômico
(`ON CONFLICT DO NOTHING`); o webhook antigo não toca nas colunas novas.

### 1.2 Tabela `acao_canal` (a outbox)

Uma linha por **ação** (R-05). Respostas a update, proativas e compensações compartilham a tabela.

| Coluna | Tipo | Nulo | Descrição |
|---|---|---|---|
| `id` | varchar(120) | não | **PK**, o `acaoId` estável (ver 1.2.1) |
| `seq` | bigint | não | `GENERATED ALWAYS AS IDENTITY`, único; ordem global de criação (base da ordem por chat) |
| `lote_id` | varchar(120) | não | evento que originou a ação (`u<update_id>`, `p:<evento>`, `c:<acaoId>`); a entrega é por lote |
| `canal` | varchar(12) | não | `TELEGRAM` (outros canais futuros) |
| `origem` | varchar(16) | não | `RESPOSTA_UPDATE`, `PROATIVA` ou `COMPENSACAO` |
| `update_id` | bigint | sim | preenchido em `RESPOSTA_UPDATE` |
| `chat_id` | bigint | não | destino; define a fila de ordem. Em `RESPOSTA_CALLBACK`, o chat do update |
| `professor_id` | uuid | sim | preenchido nas proativas e compensações; usado para descartar se o vínculo foi desfeito |
| `trabalho_id` | uuid | sim | preenchido em `MENSAGEM_ETAPA`; liga a confirmação ao id da mensagem de etapa |
| `tipo` | varchar(20) | não | `MENSAGEM`, `EDICAO`, `DOCUMENTO`, `RESPOSTA_CALLBACK`, `MENSAGEM_ETAPA` |
| `payload` | jsonb | sim | a ação v1 serializada (sem `acaoId`); **zerado** após a retenção (R-11) |
| `compensacao` | jsonb | sim | a ação v1 alternativa (R-07): para `EDICAO`/`MENSAGEM_ETAPA`, a mesma mensagem como nova se a edição falhar (`MENSAGEM_NAO_EDITAVEL`); para `DOCUMENTO`, só o aviso curto com o botão para pedir o PDF de novo, sem `documentoId` nem texto do relatório (`DOCUMENTO_INDISPONIVEL`, FR-007c); zerada com o `payload` |
| `estado` | varchar(18) | não | ver 1.2.2 |
| `tentativas` | integer | não | tentativas de entrega ao orquestrador (0 nas respostas a update) |
| `proxima_tentativa_em` | timestamptz | sim | próxima tentativa; durante a reserva é empurrada para o futuro (lease) |
| `prazo_em` | timestamptz | não | `criado_em` + prazo total (24 h por padrão); depois dele, `FALHA_DEFINITIVA` |
| `versao` | integer | não | controle otimista (padrão 0); toda gravação faz `WHERE versao = ?` e incrementa, para que a confirmação e o despachante nunca se sobrescrevam |
| `criado_em` | timestamptz | não | |
| `entregue_em` | timestamptz | sim | aceite HTTP do orquestrador, ou criação nas respostas a update |
| `finalizado_em` | timestamptz | sim | momento do estado final (base da retenção) |
| `codigo_motivo` | varchar(40) | sim | motivo em `FALHOU`; código de situação em `FALHA_DEFINITIVA`/`DESCARTADA` |

**Restrições**: `PRIMARY KEY (id)`; `UNIQUE (seq)`; `CHECK (estado IN (...))`;
`CHECK (tipo IN (...))`; `CHECK (origem IN (...))`; `CHECK (tentativas >= 0)`;
`CHECK (origem <> 'RESPOSTA_UPDATE' OR update_id IS NOT NULL)`.

**Índices** (os parciais cobrem só as linhas "vivas", mantendo-os pequenos):

| Índice | Definição | Uso |
|---|---|---|
| `ix_acao_canal_entrega` | `(proxima_tentativa_em) WHERE estado = 'PENDENTE'` | achar o próximo lote devido |
| `ix_acao_canal_chat_vivas` | `(chat_id, seq) WHERE estado IN ('PENDENTE','ENTREGUE')` | portão de ordem por chat |
| `ix_acao_canal_update` | `(update_id, seq) WHERE update_id IS NOT NULL` | reentrega de update repetido |
| `ix_acao_canal_lote` | `(lote_id, seq)` | carregar o lote |
| `ix_acao_canal_expurgo` | `(finalizado_em) WHERE finalizado_em IS NOT NULL` | limpeza |

#### 1.2.1 Formato do `id`

| Origem | Formato | Exemplo | Estabilidade |
|---|---|---|---|
| resposta a update | `u<update_id>-<posicao>` | `u90211-0` | igual em toda entrega do mesmo update (FR-007a) |
| proativa de resultado | `p:resultado:<trabalhoId>:<posicao>` | `p:resultado:7f3c…:2` | igual em toda tentativa (FR-013) e por evento (FR-014) |
| proativa de etapa | `p:etapa:<trabalhoId>:<ETAPA>:<posicao>` | `p:etapa:7f3c…:TRANSCREVENDO:0` | idem |
| proativa de demora | `p:demora:<trabalhoId>:<posicao>` | `p:demora:7f3c…:0` | idem |
| compensação | `<id da ação que falhou>-c` | `u90211-1-c` | no máximo uma por ação |
| erro de processamento perdido | `u<update_id>-e0` | `u90211-e0` | R-04 item 5 |

`posicao` é o índice 0-based na lista **já expandida** (partes de texto longo contam como ações).
Os ids não contêm CPF, nome nem texto (FR-029).

#### 1.2.2 Estados e transições (`AcaoCanal`, núcleo)

```text
                       2xx do orquestrador
      ┌──────────────────────────────────────────┐
      │                                          ▼
 PENDENTE ───(falha de entrega, agora < prazo)──▶ PENDENTE (tentativas+1, próxima tentativa)
      │                                          
      ├──(agora ≥ prazo)────────────────────────▶ FALHA_DEFINITIVA
      ├──(vínculo desfeito)─────────────────────▶ DESCARTADA
      └──(confirmação chegou antes do 2xx)──────▶ EXECUTADA | FALHOU

 ENTREGUE ──(confirmação EXECUTADA)──▶ EXECUTADA
 ENTREGUE ──(confirmação FALHOU)─────▶ FALHOU   (+ compensação se MENSAGEM_NAO_EDITAVEL ou DOCUMENTO_INDISPONIVEL)
 ENTREGUE ──(agora ≥ prazo, sem confirmação)──▶ FALHA_DEFINITIVA com SEM_CONFIRMACAO
```

| Estado | Final? | Quem o produz | Observação |
|---|---|---|---|
| `PENDENTE` | não | criação proativa/compensação; falha de entrega | elegível quando `proxima_tentativa_em <= agora` e o portão de ordem libera |
| `ENTREGUE` | não | aceite HTTP; **criação das respostas a update** | aguarda confirmação; conta no portão de ordem por `confirmacao-max-segundos` |
| `EXECUTADA` | sim | confirmação `EXECUTADA` | para `MENSAGEM_ETAPA`, só depois de gravar o `mensagemId` no trabalho |
| `FALHOU` | sim | confirmação `FALHOU` | com `codigo_motivo`; compensação criada pelo núcleo se aplicável |
| `FALHA_DEFINITIVA` | sim | prazo vencido sem aceite (`PRAZO_VENCIDO`) ou entregue nunca confirmada (`SEM_CONFIRMACAO`) | `PRAZO_VENCIDO`: log + métrica `canal_acoes_falha_definitiva_total` sem dado de aluno (FR-018). `SEM_CONFIRMACAO`: expiração da retenção (FR-029), produzida pela limpeza diária, fora dessa métrica e sem alterar o estado do relatório |
| `DESCARTADA` | sim | vínculo desfeito antes da entrega | só `PROATIVA`/`COMPENSACAO` com `professor_id` |

**Invariantes**: (1) estado final nunca volta; (2) confirmação repetida e idêntica é inócua,
divergente é ignorada e contada; (3) `PENDENTE` só existe para `PROATIVA`/`COMPENSACAO`;
(4) um lote é entregue inteiro ou não é entregue; (5) para um mesmo chat, nenhum lote é reservado
enquanto houver ação anterior `PENDENTE` ou `ENTREGUE` há menos de `confirmacao-max-segundos`;
(6) toda mudança de estado é condicional à `versao` lida: se o despachante terminar o HTTP e a ação já tiver
sido confirmada (`EXECUTADA`/`FALHOU`) por outro ator, a gravação do aceite é descartada e o estado final
prevalece; (7) a reserva (lease) dura `lease-segundos` e deve exceder conexão + resposta; um despachante
que morre deixa o lote reentregável quando o lease vence, com os mesmos ids.

**Política de retentativa** (`PoliticaRetentativa`, pura): após a *n*-ésima falha,
`proxima = agora + min(teto, inicial × 2^(n−1)) × (1 ± jitter)`; com `inicial=30 s`, `teto=15 min`,
`prazo=24 h` (padrões), a sequência é 30 s, 1 min, 2 min, 4 min, 8 min, 15 min, 15 min… (cerca de 100
tentativas em 24 h). A próxima tentativa nunca passa de `prazo_em`; `agora ≥ prazo_em` encerra em
`FALHA_DEFINITIVA` (`PRAZO_VENCIDO`) na visita seguinte, sem esperar o teto de 15 min.

### 1.3 Tabela `documento_canal`

Identificador opaco de PDF para o orquestrador (R-08).

| Coluna | Tipo | Nulo | Descrição |
|---|---|---|---|
| `token_hash` | char(64) | não | **PK**; SHA-256 (hex) do token de 256 bits entregue ao orquestrador |
| `professor_id` | uuid | não | dono; a leitura repete a checagem de dono e associação |
| `relatorio_tipo` | varchar(12) | não | `AULA` ou `SEMESTRAL` |
| `relatorio_id` | uuid | não | |
| `versao` | integer | sim | número da versão (conferência); nulo = PDF oficial |
| `nome_arquivo` | varchar(120) | não | nome sugerido (sem identificadores internos, como no portal) |
| `criado_em` | timestamptz | não | |
| `expira_em` | timestamptz | não | `prazo de entrega + 1 h` por padrão; depois, `404` |

`CHECK (relatorio_tipo IN ('AULA','SEMESTRAL'))`; `CHECK (versao IS NULL OR versao > 0)`;
índice `ix_documento_canal_expira (expira_em)` para o expurgo. Sem chave estrangeira (o relatório
pode ser apagado/anulado; nesse caso a leitura devolve `404`).

## 2. Modelo do núcleo (sem tabelas, sem Spring)

| Tipo | Pacote | Papel |
|---|---|---|
| `AcaoCanal` | `dominio.canal` | entidade da ação: `id`, `loteId`, `origem`, `chatId`, `tipo`, `payload`, `compensacao`, `estado`, `tentativas`, `proximaTentativaEm`, `prazoEm`…; métodos `registrarFalhaEntrega(agora, politica)`, `marcarEntregue(agora)`, `confirmarExecutada(agora)`, `confirmarFalha(codigo, agora)`, `descartar(agora)`, `expirarSemConfirmacao(agora)`, `vencida(agora)`, `compensaPor(codigo)`, `criarCompensacao()` |
| `EstadoAcaoCanal`, `OrigemAcaoCanal`, `TipoAcaoCanal` | `dominio.canal` | enums |
| `PoliticaRetentativa` | `dominio.canal` | intervalo inicial, teto, prazo, jitter; `proximaTentativa(tentativas, agora)` |
| `DocumentoCanal` | `dominio.canal` | referência + validade; `valido(agora)` |
| `AcaoCanalRepositorio` | `porta` | gravar lote (idempotente), reservar próximo lote devido, buscar por id/update, atualizar, expurgar |
| `DocumentoCanalRepositorio` | `porta` | emitir, buscar por hash, expurgar |
| `OrquestradorCanalPorta` | `porta` | `entregar(LoteEntrega)` → `ACEITO` ou `RECUSADO(codigo)` |
| `FilaTrabalhoPorta.registrarMensagemEtapa` | `porta` | grava o id da mensagem de etapa no trabalho |

O `payload` e a `compensacao` são `String` opacas para o núcleo; o formato v1 é conhecimento do
adaptador de entrada (Princípio I).

## 3. Entidades da spec ↔ onde vivem

| Entidade da spec | Representação |
|---|---|
| Update de entrada | `update_telegram_processado` (+ `total_acoes`, `origem`) |
| Ação de canal | `acao_canal` (`origem = RESPOSTA_UPDATE`) e DTO v1 no corpo da resposta |
| Ação proativa pendente | `acao_canal` (`origem = PROATIVA`, `estado = PENDENTE`) |
| Documento disponível para o orquestrador | `documento_canal` |
| Credencial de serviço | configuração (`token-atual`, `token-proximo`); nunca persistida |
| Estado da conversa | inalterado (`vinculo_telegram`) |

## 4. Configuração (`relatorios.canal-telegram`)

Lida do ambiente por `PropriedadesAplicacao.CanalTelegram` (novo `record`), padrões entre colchetes:

| Propriedade | Variável | Padrão | Uso |
|---|---|---|---|
| `entrega-proativa` | `CANAL_TELEGRAM_ENTREGA` | `DIRETA` | `DIRETA` ou `ORQUESTRADOR` (R-10) |
| `token-atual`, `token-proximo` | `CANAL_TELEGRAM_TOKEN_ATUAL`, `CANAL_TELEGRAM_TOKEN_PROXIMO` | vazio | tokens de serviço (R-09); vazio = área interna recusa tudo |
| `orquestrador-url` | `CANAL_TELEGRAM_ORQUESTRADOR_URL` | vazio | webhook do n8n |
| `orquestrador-token` | `CANAL_TELEGRAM_ORQUESTRADOR_TOKEN` | vazio | enviado em `X-Canal-Entrega-Token` |
| `conexao-timeout-ms`, `resposta-timeout-ms` | `CANAL_TELEGRAM_CONEXAO_TIMEOUT_MS`, `CANAL_TELEGRAM_RESPOSTA_TIMEOUT_MS` | 3000, 10000 | cliente HTTP |
| `retentativa-inicial-segundos`, `retentativa-teto-segundos`, `prazo-horas` | `CANAL_TELEGRAM_RETENTATIVA_INICIAL_S`, `…_TETO_S`, `CANAL_TELEGRAM_PRAZO_H` | 30, 900, 24 | FR-018 |
| `confirmacao-max-segundos` | `CANAL_TELEGRAM_CONFIRMACAO_MAX_S` | 60 | portão de ordem |
| `lease-segundos` | `CANAL_TELEGRAM_LEASE_S` | 60 | reserva do lote; deve ser maior que `conexao-timeout-ms` + `resposta-timeout-ms` (validado na subida) |
| `intervalo-despacho-ms`, `lote-max-por-ciclo` | `CANAL_TELEGRAM_INTERVALO_DESPACHO_MS`, `CANAL_TELEGRAM_LOTE_MAX` | 2000, 20 | despachante |
| `retencao-payload-horas`, `retencao-registro-dias` | `CANAL_TELEGRAM_RETENCAO_PAYLOAD_H`, `CANAL_TELEGRAM_RETENCAO_REGISTRO_D` | 24, 7 | R-11 |

`toString()` dos `record`s com segredo é mascarado (como `Seguranca` e `Telegram`), e os valores
`token-*` e `orquestrador-token` nunca entram em log.
