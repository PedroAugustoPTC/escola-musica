# Restrição do tipo de ação do canal (T131) — verificação 1 do plano

Pergunta: qual é o nome real da `CHECK` que limita `channel_action.type` e o `ChannelActionRepository` aceita o tipo
novo `DELETE_MESSAGE`?

## Rastro da restrição nas migrações

| Migração | O que faz com a `CHECK` do tipo | Nome resultante |
|---|---|---|
| `V6__canal_telegram_interno.sql` (linha 39) | cria `CONSTRAINT ck_acao_canal_tipo CHECK (tipo IN ('MENSAGEM','EDICAO','DOCUMENTO','RESPOSTA_CALLBACK','MENSAGEM_ETAPA'))` na tabela `acao_canal` | `ck_acao_canal_tipo` |
| `V7__rename_to_english.sql` (linha 216) | `ALTER TABLE channel_action RENAME CONSTRAINT ck_acao_canal_tipo TO ck_channel_action_type` (a tabela e a coluna já foram renomeadas para `channel_action.type`) | `ck_channel_action_type` |
| `V8__rename_enum_values.sql` (linhas 17 e 108) | `DROP CONSTRAINT ck_channel_action_type` e recria com o **mesmo nome**: `CHECK (type IN ('MESSAGE','EDIT','DOCUMENT','CALLBACK_REPLY','STEP_MESSAGE'))` | **`ck_channel_action_type`** |

**Nome real em vigor: `ck_channel_action_type`** (tabela `channel_action`, coluna `type`). A `V13` troca essa restrição
(`DROP CONSTRAINT` + `ADD CONSTRAINT`, mesmo nome) acrescentando `'DELETE_MESSAGE'` ao conjunto; os cinco valores atuais
continuam aceitos e qualquer outro continua barrado. A `V13` é aditiva: não toca nas linhas existentes.

## Largura das colunas

| Coluna | Largura | Maior valor novo | Cabe? |
|---|---|---|---|
| `channel_action.type` | `varchar(20)` | `DELETE_MESSAGE` (14) | sim |
| `channel_action.reason_code` | `varchar(40)` | `MESSAGE_NOT_DELETABLE` (21) | sim |

## `ChannelActionRepository` aceita o tipo novo?

`ChannelActionRepositoryAdapter` grava `action.type().name()` e lê com `ChannelActionType.valueOf(e.type)`; não há
lista de tipos nem `CHECK` replicada no código. O enum `ChannelActionType` já tem `DELETE_MESSAGE` (acrescentado pela
Fundação, T011). Portanto, **o único impedimento ao tipo novo é a `CHECK` do banco**: antes da `V13` a gravação falha
com violação de `ck_channel_action_type`; depois, passa. `ChannelActionDeleteMessageIT` cobre as duas pontas (antes e
depois da migração) e a leitura de volta pelo repositório.

## Outros pontos que o tipo novo toca

- `ConfirmChannelActionsExecutionUseCase.KNOWN_CODES`: precisa listar `MESSAGE_NOT_DELETABLE`; sem isso a falha seria
  normalizada para `TELEGRAM_ERROR` e a compensação nunca seria gerada.
- `ChannelAction.compensatesBy`: hoje devolve `MESSAGE_NOT_EDITABLE` para tudo que não é `DOCUMENT`; passa a olhar o tipo
  (`DELETE_MESSAGE` compensa por `MESSAGE_NOT_DELETABLE`, e só por ele).
- O payload de `DELETE_MESSAGE` leva apenas `type`, `chatId` e `messageId`: nenhum texto do professor, nenhum CPF.
