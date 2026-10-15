# Quickstart: validar a API interna de canal do Telegram

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

**Feature**: `002-api-interna-canal-telegram` | **Plano**: [plan.md](./plan.md)

Guia para provar, de ponta a ponta, que a nova porta funciona **sem mudar nada do que o professor vê**.
Detalhes de rotas, formato e regras estão em [contracts/](./contracts/) e [data-model.md](./data-model.md);
este guia só os referencia. Como esta feature não inclui workflows do n8n (feature `004`), a prova é
feita por testes automatizados cujo "executor" simula o n8n.

## 1. Pré-requisitos

- Docker Engine 27+ com Docker Compose v2 (única dependência da máquina, constituição "Containerização
  e implantação").
- Nenhuma credencial real: o ambiente de testes usa o provedor de IA simulado e o servidor de API de
  bots simulado em WireMock (`infra/compose.test.yaml`). Nenhum bot nem n8n reais são necessários.

## 2. Configuração usada nos testes

Os ITs sobem a aplicação com o perfil `teste` (`backend/aplicacao/src/test/resources/application-teste.yaml`),
acrescido dos valores do canal (fixos no código de teste, sem segredo real):

| Propriedade | Valor no teste | Para quê |
|---|---|---|
| `relatorios.canal-telegram.token-atual` / `token-proximo` | valores fictícios distintos | autenticação e rotação |
| `relatorios.canal-telegram.entrega-proativa` | `ORQUESTRADOR` nos ITs de entrega; `DIRETA` nos demais | exatamente um caminho |
| `relatorios.canal-telegram.orquestrador-url` | URL do WireMock do orquestrador | despacho |
| `relatorios.canal-telegram.retentativa-*`, `prazo-horas` | padrões, com `RelogioAjustavel` | retentativa sem dormir |

Para ambientes reais (feature `004`), as variáveis estão em [data-model.md §4](./data-model.md); nenhuma
é obrigatória nesta feature (o padrão `DIRETA` mantém o comportamento atual).

## 3. Rodar a suíte

```bash
docker compose -f infra/compose.test.yaml run --rm testes-backend
```

Esse comando executa unitários (núcleo, com gate JaCoCo ≥ 90% de linhas e ramos), ArchUnit e todos os
ITs. Os ITs de conversa e de paridade rodam **duas vezes** (R-13): uma com o transporte `webhook`
(comportamento de hoje) e outra com o transporte `interno` (executor simulado do n8n). O build falha se
qualquer uma falhar.

Para limpar: `docker compose -f infra/compose.test.yaml down -v`.

## 4. Cenários de validação

Cada cenário aponta o requisito que prova e a classe de teste responsável (a criar na implementação).

| # | Cenário | Prova | Esperado | Teste |
|---|---|---|---|---|
| V1 | **Paridade pela nova porta**: reexecutar conferência, registro de aula, retomada, semestral, histórico, vinculação, início/busca e os cinco roteiros de paridade com `telegram.transporte=interno` | FR-005, FR-030, SC-001, SC-002 | todos passam **sem alterar nenhuma expectativa** de texto, botão ou ordem | `*ConversaIT`, `RoteiroParidade*IT` (modo `interno`) |
| V2 | Contrato das ações: toda resposta de `/atualizacao` validada em `#/$defs/RespostaAtualizacao`; ids `u<N>-<pos>`; textos longos já divididos; teclado só na última parte | FR-002, FR-003, FR-031 | schema válido em todos os cenários de V1 | `CanalTelegramContratoIT` |
| V3 | Segredo do webhook ausente/errado em `/atualizacao` | FR-006, US2.6 | `401` sem corpo | `SegurancaInternaIT` |
| V4 | Matriz de credenciais: sem token, token errado, correto, `proximo`, ambos, só cookie de portal, token de serviço no portal, nenhum token configurado | FR-024 a FR-027, SC-005 | só "token correto na área interna" é aceito; tudo o mais `401` sem corpo | `SegurancaInternaIT`, `ComparadorSegredoTeste` |
| V5 | Tempo constante: `ComparadorSegredo` compara digests de mesmo tamanho, ambos os tokens sempre avaliados | FR-026a, US2.2 | teste unitário sobre a estrutura (sem medir relógio) | `ComparadorSegredoTeste` |
| V6 | Update repetido: sem confirmar → mesmas ações e ids; após confirmar tudo → `acoes: []`; após confirmar parte → só o resto; em paralelo → um processamento | FR-007, FR-007a, US1.5 | conforme C4 a C6 | `AtualizacaoIdempotenciaIT` |
| V7 | Falha parcial: confirmação `FALHOU` + `MENSAGEM_NAO_EDITAVEL` numa edição; "message is not modified"; `codigoMotivo` desconhecido | FR-007b, FR-017, SC-002 | nasce `<id>-c` com o mesmo conteúdo, entregue uma vez; falha da edição de etapa regrava o `mensagemId`; "not modified" = `EXECUTADA` sem compensação; código desconhecido = `ERRO_TELEGRAM` sem `400` | `ConfirmacaoCompensacaoIT` |
| V8 | Entrega proativa com orquestrador fora do ar, depois no ar; reinício com lease vencido; confirmação durante o HTTP; receptor recebendo o mesmo lote duas vezes | FR-011 a FR-016, FR-013, SC-004 | ação fica `PENDENTE`, reenvia com ids iguais em intervalos crescentes, entrega ao voltar, processamento do relatório intacto; estado final nunca regride; uma só execução no Telegram por ação | `EntregaAcoesCanalIT` |
| V9 | Recusas do orquestrador (`5xx`, `4xx`, timeout) e prazo esgotado | FR-012, FR-018, US3.6 | `PENDENTE` até o prazo; depois `FALHA_DEFINITIVA`, log sem dado de aluno, métrica incrementada | `EntregaAcoesCanalIT` |
| V10 | Ordem por chat e portão de confirmação (inclui *timeout*) | FR-016 | lote 2 só sai depois da confirmação do lote 1 ou de 60 s; chats distintos em paralelo | `EntregaAcoesCanalIT` + testes unitários do núcleo |
| V11 | Idempotência da criação: notificar o mesmo trabalho duas vezes | FR-014 | uma só ação por evento | `EntregaAcoesCanalIT` |
| V12 | Exatamente um caminho proativo: `DIRETA` não enfileira nada; `ORQUESTRADOR` não chama a API de bots | FR-019, US5.3, US5.4 | conforme a configuração, sem envio duplicado | `NotificacaoTelegramAdaptadorIT` (nos dois modos) |
| V13 | Aviso de trabalho da Web com vínculo; resultado de áudio do Telegram | US3.1, US3.2 | mesmas mensagens e ordem de hoje, foco só no caso do Telegram | `NotificacaoTelegramAdaptadorIT` (modo `ORQUESTRADOR`) |
| V14 | PDF por identificador: id válido, vencido, inexistente, URL/caminho, repetição dentro do prazo | FR-020 a FR-023, SC-007 | bytes idênticos aos do portal; os demais `404` sem corpo e indistinguíveis | `DocumentoCanalContratoIT` |
| V15 | Áudio: voz dentro e acima do limite pelo transporte `interno` | FR-028, US5.1, US5.2 | `AUDIO_RECEBIDO_CONFIRMAR_ALUNO` / `AUDIO_MUITO_GRANDE`; nenhum byte, caminho ou URL de áudio em ações, outbox ou logs | `RegistroAulaConversaIT` (modo `interno`) + `NaoRetencaoAudioIT` |
| V16 | Privacidade: varrer logs do fluxo interno e da entrega com segredos semeados (tokens, segredo do webhook, URL do orquestrador, `documentoId`); `payload` zerado após a retenção | FR-029, SC-006 | zero CPF, nome de aluno, texto de relatório, transcrição, token, URL do orquestrador ou `documentoId` | `LogSemDadosSensiveisIT` (estendido), `LimpezaAcoesCanalIT` |
| V17 | Webhook antigo intacto durante e depois da feature | US5.5, SC-008 | os ITs do modo `webhook` passam sem alteração | `*ConversaIT` (modo `webhook`) |
| V18 | Arquitetura | FR-033, Princípios I e VI | núcleo sem Spring nem `n8n`; `entrada` não depende de `saida`; sufixos `Controlador`/`Dto`/`Requisicao`/`Resposta`/`Adaptador` | `RegrasArquiteturaTeste`, `RegrasAdaptadoresTeste` |
| V19 | Cobertura do núcleo | FR-032, SC-008 | JaCoCo ≥ 90% de linhas e ramos em `nucleo` (inclui `dominio.canal` e `casodeuso.canal`) | build (`verify`) |
| V20 | Migração V6 sobre um banco já na V5, com linhas antigas em `update_telegram_processado` | R-14 | aplica sem erro; linhas antigas ficam com `total_acoes`/`origem` nulos e um update repetido do webhook antigo continua devolvendo `200` | `MigracaoV6IT` |

## 5. Verificação manual opcional (depois da `004`)

Fora do escopo desta feature, mas já descrita para não se perder: com o n8n da `004` apontado para estas
rotas, repetir V1 em um bot **de testes** e conferir (a) que o professor vê as mesmas mensagens, (b) que
derrubar o contêiner do n8n e enviar uma mensagem gera reentrega ao voltar, e (c) que
`CANAL_TELEGRAM_ENTREGA=ORQUESTRADOR` é ligado **junto** com a troca do webhook registrado no Telegram.

## 6. Critério de pronto

A feature está pronta quando V1 a V20 passam na CI (`testes-backend`), o gate de cobertura do núcleo
está verde e os contratos em `specs/002-api-interna-canal-telegram/contracts/` coincidem com o que os
testes de contrato validam (schema e OpenAPI).
