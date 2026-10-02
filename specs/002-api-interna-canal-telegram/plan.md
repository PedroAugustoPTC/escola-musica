# Implementation Plan: API interna de canal do Telegram para orquestrador externo

**Branch**: `002-api-interna-canal-telegram` | **Date**: 2026-10-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-api-interna-canal-telegram/spec.md`

## Summary

O n8n passa a ser o único transporte do Telegram (constituição v1.4.0, Princípio II), sem alterar
nada do que o professor vê. Esta feature entrega **só a API interna do backend**: uma nova porta de
entrada "canal Telegram", independente de transporte, que recebe o update bruto, o interpreta com o
**mesmo** `InterpretadorConversaTelegram` do webhook atual e devolve a lista ordenada de ações a
executar no chat, em DTOs v1 versionados. O workflow do n8n, a composição de contêineres, o proxy e a
troca do webhook registrado no Telegram ficam para a feature `003`.

**Abordagem técnica** (decisões em [research.md](./research.md); a reversão de R-04 da 001 é R-01):

1. **Porta de entrada única**: um `ProcessadorUpdateTelegramServico` (dedup + interpretação + resposta
   `ERRO_INTERNO`) é chamado pelo `TelegramWebhookControlador` (envia direto, como hoje) e pelo novo
   `CanalTelegramControlador` (`/interno/v1/canal/telegram/atualizacao`). Ambos produzem
   `List<SaidaTelegram>`; a serialização v1 vive só na camada de entrada (R-02, R-03).
2. **Garantia de entrega por outbox Postgres**: tabela `acao_canal` (migração **V6**, necessária)
   guarda respostas a update, ações proativas e compensações, com ids estáveis, ordem por chat e
   retentativa (30 s → 15 min, 24 h). O despacho HTTP ao webhook do n8n usa o cliente JDK, com
   timeouts, retry e `Idempotency-Key`; a reserva reaproveita `FOR UPDATE SKIP LOCKED` da fila Postgres
   existente e `update_telegram_processado` continua sendo a chave de idempotência do update
   (R-04 a R-06).
3. **Regras no núcleo**: estado e retentativa da ação, confirmação, compensação e despacho são
   `dominio.canal` + `casodeuso.canal` em Java puro, sob o gate de 90% (R-12). O `aplicacao` traz só
   adaptadores: controladores, DTOs, mapeador, persistência, cliente HTTP, tarefas agendadas.
4. **Segurança serviço a serviço**: segunda `SecurityFilterChain` só para `/interno/**`, com filtro de
   token inspirado no `N8nServiceTokenFilter` do MVP, mas com comparação em tempo constante sobre
   digests, até dois tokens simultâneos e *fail-closed* (R-09). O portal não muda.
5. **`NotificacaoTelegramAdaptador` publica numa porta de saída** (`PublicacaoAcaoTelegramPorta`) com
   duas implementações exclusivas por configuração: envio direto (padrão, comportamento atual) ou
   outbox para o n8n (R-10).
6. **PDF por identificador opaco** (`documento_canal`, hash do token, validade) servido em
   `GET /interno/v1/canal/telegram/documentos/{id}` (R-08); áudio nunca passa pelo n8n (FR-028).
7. **Testes**: trocar o "executor" dos ITs de conversa por um que lê as ações JSON e as traduz para as
   mesmas chamadas ao WireMock, de modo que **nenhuma expectativa existente muda**; contrato por JSON
   Schema; ArchUnit mantém as camadas (R-13).

## Technical Context

**Language/Version**: Java 25 (LTS), Maven multimódulo (`nucleo` + `aplicacao`), herdado da 001

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Security, Data JPA, Actuator), Flyway, Jackson 3
(`tools.jackson`), `java.net.http.HttpClient` (já usado nos adaptadores de saída), Micrometer.
**Nenhuma dependência nova de produção.** Testes: `networknt json-schema-validator`,
`swagger-request-validator`, WireMock e ArchUnit (todos já presentes em `aplicacao/pom.xml`)

**Storage**: PostgreSQL 18, migração **V6** (`acao_canal`, `documento_canal` e duas colunas em
`update_telegram_processado`); PDFs continuam no volume `documentos` (nada é copiado)

**Testing**: JUnit 5 + AssertJ + dublês à mão (núcleo, JaCoCo ≥ 90% de linhas e branches); ITs
Spring Boot contra o `postgres` de `infra/compose.test.yaml`; WireMock simulando a API de bots **e o
webhook do n8n**; JSON Schema + OpenAPI para contrato; ArchUnit. Conversa/paridade executadas por dois
transportes (`webhook`, `interno`). Tudo via `docker compose -f infra/compose.test.yaml run --rm
testes-backend`

**Target Platform**: contêineres Linux (Docker Compose), local e VPS Hostinger; esta feature não
altera `infra/compose*.yaml` nem o Caddy (feature `003`)

**Project Type**: web-service (backend) com um terceiro tipo de adaptador de entrada (API interna de
serviço), no mesmo processo

**Performance Goals**: 95% dos updates (exceto áudio assíncrono) com as ações de resposta em ≤ 2 s,
mantendo a confirmação ao professor em ≤ 5 s (SC-003); entrega proativa até 24 h de indisponibilidade
do orquestrador sem perda (SC-004)

**Constraints**: nenhuma mudança visível ao professor (SC-002); webhook atual intacto (US5.5);
`/interno/**` só com token, nunca com cookie; token fora do código e comparado em tempo constante;
nenhum dado de aluno em logs; áudio fora do n8n (FR-028); código em português sem acentos
(Princípio VI); nenhum envio duplicado nem perdido entre os dois caminhos proativos (FR-019)

**Scale/Scope**: uma escola, dezenas de professores; dezenas a poucas centenas de ações por dia na
outbox; 4 rotas internas; 5 tipos de ação v1

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v1.4.0**. Avaliação **antes** da pesquisa e **refeita depois** do design (Fase 1):

| Princípio / Restrição | Como o plano atende | Pré | Pós |
|---|---|---|---|
| **I. Domínio independente de canal e fornecedor** | A nova entrada é só um adaptador: chama o mesmo interpretador e não tem regra de conversa (FR-005). A regra de entrega (estado, retentativa, compensação) fica em `dominio.canal`/`casodeuso.canal` no `nucleo`, sem Spring e sem citar o n8n; portas `AcaoCanalRepositorio`, `DocumentoCanalRepositorio`, `OrquestradorCanalPorta`. ArchUnit estendido (`n8n` entra na lista de fornecedores proibidos no núcleo). Paridade Web × Telegram inalterada (mesmos casos de uso). | ✅ | ✅ |
| **II. Fonte única da verdade** | O n8n só transporta: recebe o update, o repassa, executa as ações e confirma. Não decide, não autoriza, não valida, não chama IA e não grava dado de domínio; **a idempotência dele é sem estado** (consulta `GET /acoes/{id}`). Estado da conversa continua só no backend; reiniciar o n8n não perde nada (a outbox reentrega). | ✅ | ✅ |
| **III. Humano no controle da IA** | Nenhuma mudança no ciclo de vida do relatório nem na aprovação; as ações carregam exatamente o que o interpretador produz, inclusive o botão de aprovar da versão vigente. | ✅ | ✅ |
| **IV. Privacidade desde o design** | Token de serviço do ambiente, comparado em tempo constante, até dois (rotação), fail-closed (R-09). `/interno/**` não é publicado pelo proxy (só `/api/*`). Áudio nunca vai ao n8n (FR-028). PDF só por id opaco, com checagem de dono repetida na leitura (R-08). Logs só com ids e códigos; payload da outbox zerado após a retenção (R-11). Comunicação backend → n8n é interna à rede de serviços (HTTP, sem exposição pública); qualquer exposição pública usaria HTTPS (decisão da `003`). **Ponto a confirmar com o dono do produto**: leitura de FR-029 (R-11). | ✅ | ✅ (com ponto registrado) |
| **V. Qualidade obrigatória** | Regras no `nucleo` com ≥ 90% de linhas e branches (R-12); contrato por JSON Schema + OpenAPI; testes de segurança por matriz de credenciais; ITs de conversa/paridade nos dois transportes; dublês para o orquestrador (WireMock). | ✅ | ✅ |
| **VI. Nomenclatura e linguagem ubíqua** | Pacotes e tipos em português sem acento com os sufixos da constituição (`*CasoDeUso`, `*Controlador`, `*Servico`, `*Repositorio`, `*Porta`, `*Adaptador`, `*Mapeador`, `*Dto`/`*Requisicao`/`*Resposta`). Exceções permitidas: rótulos de protocolo (`X-N8N-Service-Token`, `Idempotency-Key`, `EXECUTADA`…). Regras ArchUnit novas (R-13). | ✅ | ✅ |
| **VII. Simplicidade e foco** | Sem broker, sem biblioteca de retry, sem dependência nova; reaproveita fila Postgres, `update_telegram_processado`, cliente HTTP JDK e as bibliotecas de teste. Acréscimos que a spec exige estão justificados em "Complexity Tracking". Nada de roadmap (workflows, compose, troca de webhook ficam na `003`). | ✅ | ✅ (acréscimos justificados) |
| **VIII. Fluxo de trabalho** | Branch dedicada `002-api-interna-canal-telegram`; commit e PR para a `main` ao concluir, sem merge pelo autor nem auto-merge. | ✅ | ✅ |
| **IX. Identidade visual e acessibilidade** | Sem alteração de UI, PDF ou texto do bot: as ações carregam os textos do catálogo atual. | ✅ | ✅ |
| **Restrições: n8n só como transporte; workflows como código; segredos fora dos JSON; contêiner com versão fixada** | Esta feature não cria workflows nem serviço n8n (escopo da `003`); entrega o contrato que eles seguirão, com obrigações explícitas ([contracts/canal-telegram-interno.md](./contracts/canal-telegram-interno.md) §4). | ✅ | ✅ |
| **Quality gate: paridade** | Nenhuma funcionalidade fica restrita a um canal; nenhum desvio. | ✅ | ✅ |
| **Quality gate: separação de responsabilidades em orquestração** | O contrato proíbe regra no orquestrador (§4) e dá ao backend a decisão de compensação (R-07). | ✅ | ✅ |

**Resultado**: aprovado antes e depois do desenho, **sem violação da constituição**. Há uma pendência
de interpretação (FR-029 × texto da ação na outbox, R-11) e uma nota de dependência (artefatos da 001
que assumem "sem n8n", abaixo). Nenhuma exige emenda.

**Artefatos da 001 a anotar na implementação** (tarefa de documentação, sem reescrever histórico):
`research.md` R-04 (apontar para R-01 desta feature), `plan.md` (Summary, linha II do Constitution
Check, Structure Decision) e `contracts/telegram-conversa.md` §1 (transporte). Os arquivos de `infra/`
que assumem "sem n8n" ficam para a `003` (conforme o Sync Impact Report da v1.4.0).

## Project Structure

### Documentation (this feature)

```text
specs/002-api-interna-canal-telegram/
├── plan.md                                  # Este arquivo
├── research.md                              # Fase 0: decisões R-01 a R-15 (R-01 reverte R-04 da 001)
├── data-model.md                            # Fase 1: V6, estados da ação, política de retentativa, configuração
├── quickstart.md                            # Fase 1: validação V1 a V20
├── contracts/
│   ├── canal-telegram-interno.md            # regras, rotas, entrega proativa, obrigações do orquestrador
│   ├── acoes-v1.schema.json                 # formato normativo dos corpos (JSON Schema 2020-12)
│   └── canal-telegram-interno.openapi.yaml  # rotas, cabeçalhos e códigos HTTP
├── checklists/requirements.md
└── tasks.md                                 # Fase 2 (/speckit-tasks; não criado por este comando)
```

### Source Code (repository root)

Somente `backend/` muda (mais testes e `application.yaml`). Marcação: **(N)** novo, **(M)** modificado.

```text
backend/
├── nucleo/                                          # Java puro — SEM Spring
│   ├── src/main/java/br/com/felipeblaft/relatorios/
│   │   ├── dominio/canal/                           # (N) AcaoCanal, EstadoAcaoCanal, OrigemAcaoCanal,
│   │   │                                            #     TipoAcaoCanal, PoliticaRetentativa, DocumentoCanal
│   │   ├── casodeuso/canal/                         # (N) GravarAcoesCanalCasoDeUso,
│   │   │                                            #     ObterAcoesNaoConfirmadasDoUpdateCasoDeUso,
│   │   │                                            #     ConfirmarExecucaoAcoesCanalCasoDeUso,
│   │   │                                            #     EntregarAcoesPendentesCasoDeUso,
│   │   │                                            #     EmitirDocumentoCanalCasoDeUso,
│   │   │                                            #     ObterDocumentoCanalCasoDeUso,
│   │   │                                            #     LimparAcoesCanalCasoDeUso
│   │   └── porta/                                   # (N) AcaoCanalRepositorio, DocumentoCanalRepositorio,
│   │                                                #     OrquestradorCanalPorta
│   │                                                # (M) FilaTrabalhoPorta.registrarMensagemEtapa
│   └── src/test/java/…                              # (N) testes unitários dos itens acima + dublês em memória
│                                                    # (M) RegrasArquiteturaTeste (n8n entre os fornecedores)
└── aplicacao/
    ├── src/main/java/br/com/felipeblaft/relatorios/
    │   ├── entrada/telegram/
    │   │   ├── ProcessadorUpdateTelegramServico.java   # (N) dedup + interpretação + ERRO_INTERNO
    │   │   ├── ResultadoUpdateTelegram.java            # (N)
    │   │   ├── PublicacaoAcaoTelegramPorta.java        # (N) porta de saída da notificação (R-10)
    │   │   ├── TelegramWebhookControlador.java         # (M) passa a usar o serviço; comportamento igual
    │   │   ├── RegistroUpdatesTelegram.java            # (M) + concluir(updateId, total) e situação
    │   │   ├── SaidaTelegram.java                      # (M) Documento ganha ReferenciaDocumento
    │   │   ├── Apresentacao*Telegram.java, FluxoHistoricoTelegram.java  # (M) preenchem a referência
    │   │   └── canal/                                  # (N) adaptador de entrada do canal interno
    │   │       ├── CanalTelegramControlador.java       #     4 rotas /interno/v1/canal/telegram/**
    │   │       ├── AcaoDto.java (sealed) e subtipos    #     AcaoMensagemDto, AcaoEdicaoDto, …
    │   │       ├── RespostaAtualizacao.java, RequisicaoConfirmacao.java, RespostaConfirmacao.java,
    │   │       │   RespostaEstadoAcao.java, LoteEntregaDto.java
    │   │       └── AcaoCanalMapeador.java              #     SaidaTelegram → AcaoDto (divisão, ids, compensação)
    │   ├── entrada/interno/seguranca/                  # (N) SegurancaInternaConfiguracao (cadeia /interno/**),
    │   │                                               #     TokenServicoFiltro, ComparadorSegredo
    │   ├── entrada/web/seguranca/SegurancaConfiguracao.java  # (M) só @Order(2); regras do portal inalteradas
    │   ├── saida/persistencia/                         # (N) AcaoCanalEntidade/Jpa/RepositorioAdaptador,
    │   │                                               #     DocumentoCanalEntidade/Jpa/RepositorioAdaptador
    │   │                                               # (M) RegistroUpdatesTelegramAdaptador, UpdateTelegramProcessado*
    │   │                                               # (M) FilaTrabalhoPostgresAdaptador implementa registrarMensagemEtapa
    │   ├── saida/telegram/                             # (N) OrquestradorN8nAdaptador (HTTP),
    │   │                                               #     PublicacaoDiretaTelegramAdaptador,
    │   │                                               #     PublicacaoOutboxTelegramAdaptador
    │   │                                               # (M) NotificacaoTelegramAdaptador usa a porta;
    │   │                                               #     etapas viram MensagemEtapa
    │   ├── saida/fila/                                 # (N) EntregaAcoesCanalTarefa, LimpezaAcoesCanalTarefa
    │   └── configuracao/                               # (M) PropriedadesAplicacao (+CanalTelegram),
    │                                                   #     CasosDeUsoConfiguracao (+casos de uso do canal)
    ├── src/main/resources/
    │   ├── application.yaml                            # (M) bloco relatorios.canal-telegram
    │   └── db/migration/V6__canal_telegram_interno.sql # (N)
    └── src/test/java/…                                 # ver "Estratégia de testes" abaixo
infra/.env.example                                      # (M) só documentação das variáveis CANAL_TELEGRAM_*
```

**Estratégia de testes** (R-13):

| Camada | Classes (novas ou alteradas) |
|---|---|
| Unitário (`nucleo`) | `AcaoCanalTeste`, `PoliticaRetentativaTeste`, `GravarAcoesCanalCasoDeUsoTeste`, `ConfirmarExecucaoAcoesCanalCasoDeUsoTeste`, `EntregarAcoesPendentesCasoDeUsoTeste`, `EmitirDocumentoCanalCasoDeUsoTeste`, `ObterDocumentoCanalCasoDeUsoTeste`, `LimparAcoesCanalCasoDeUsoTeste` |
| Unitário (`aplicacao`) | `ComparadorSegredoTeste`, `AcaoCanalMapeadorTeste` (divisão, ids, compensação, documento) |
| Contrato | `CanalTelegramContratoIT` (schema em toda resposta), `DocumentoCanalContratoIT` |
| Segurança | `SegurancaInternaIT` (matriz de credenciais, rotação, fail-closed, cookie, CSRF) |
| Conversa/paridade | `TelegramConversaBase` ganha o **executor** (`webhook`/`interno`) e `ExecutorAcoesSimuladoTeste`; os `*ConversaIT` e `RoteiroParidade*IT` **não mudam de expectativa** e rodam nos dois modos |
| Entrega | `EntregaAcoesCanalIT`, `AtualizacaoIdempotenciaIT`, `ConfirmacaoCompensacaoIT`, `NotificacaoTelegramAdaptadorIT` (nos dois caminhos) |
| Persistência/privacidade | `MigracaoV6IT`, `LimpezaAcoesCanalIT`, `LogSemDadosSensiveisIT` (estendido) |
| Arquitetura | `RegrasAdaptadoresTeste` (regras novas), `RegrasArquiteturaTeste` (`n8n` proibido no núcleo) |

**Structure Decision**: mantém o desenho da 001 (dois módulos Maven; canais como adaptadores de
entrada dentro de `aplicacao`). O canal interno é um **adaptador de entrada irmão** do webhook
(`entrada.telegram.canal`), o filtro de serviço vive em `entrada.interno.seguranca` (separado da
segurança do portal, que não é tocada além de `@Order`), e a regra de entrega vai ao `nucleo` para
ficar sob o gate de cobertura. Nenhum serviço de infraestrutura novo nesta feature.

## Complexity Tracking

> Não há violação da constituição. A tabela registra os acréscimos de complexidade que o Princípio VII
> manda justificar, **atualizando** o registro da 001 (cuja linha "sem orquestrador" deixa de valer, R-01).

| Acréscimo | Por que é necessário | Alternativa mais simples rejeitada porque |
|---|---|---|
| **Reversão de R-04**: n8n volta como transporte do Telegram (substitui "sem orquestrador" da 001) | decisão do dono do produto, já na constituição v1.4.0; esta feature entrega o lado do backend | manter o webhook direto contraria a decisão; n8n com lógica própria é proibido pelo Princípio II |
| Tabela `acao_canal` (outbox) e migração V6 | FR-007a, FR-012 a FR-016: ids estáveis, reentrega de update repetido, retentativa que sobrevive a reinício, ordem por chat | `update_telegram_processado` sozinho não guarda ações; entrega em memória perde ações num reinício; `trabalho_processamento` mistura dois ciclos de vida (R-05) |
| Despachante agendado (`EntregaAcoesCanalTarefa`) + `OrquestradorN8nAdaptador` | FR-011/FR-012: entrega proativa com timeout, retry e idempotência | chamar o n8n dentro do processamento do trabalho acopla o relatório à disponibilidade do orquestrador (viola FR-015) |
| Segunda `SecurityFilterChain` (`/interno/**`) e `TokenServicoFiltro` | FR-024/FR-025/FR-027: isolamento nos dois sentidos sem tocar CSRF do portal | ramo por caminho na cadeia do portal mistura credenciais e arrisca regressão |
| Comparação por digests, **dois** tokens, ambos sempre avaliados | FR-026a: rotação sem indisponibilidade e tempo constante | `equals` (como no MVP) vaza o prefixo; token único exige janela de recusa na troca |
| Tabela `documento_canal` (hash de token, validade) | FR-020 a FR-023: PDF por id opaco, imprevisível, expirável, sem trafegar bytes na lista | token assinado sem estado expõe ids ou exige cifragem e não revoga; copiar o PDF duplica dado de menor |
| Duas implementações exclusivas de `PublicacaoAcaoTelegramPorta` (direta × outbox) | FR-019: caminho atual como padrão e exatamente um ativo | remover o envio direto agora é escopo da `003`; um `if` em runtime deixa os dois vivos |
| Regras de entrega no `nucleo` (`dominio.canal`, `casodeuso.canal`) | FR-032/Princípio V: gate de 90% só existe no núcleo | deixar no adaptador esconde a parte mais arriscada da cobertura |
| `GET /acoes/{acaoId}` (não pedida literalmente) | FR-013 exige idempotência do receptor, e o n8n **não** pode guardar estado (Princípio II) | pedir ao n8n um cache próprio viola o Princípio II e some num reinício |
| `MensagemEtapa` também para edição de etapa (em vez de `Edicao`) | borda "mensagem de etapa apagada": a compensação precisa regravar o `mensagemId` para não gerar uma mensagem nova a cada etapa (R-07) | manter `Edicao` acumularia mensagens no chat |
| Portão de ordem por confirmação, com *timeout* | FR-016 sem corrida entre o envio e a primeira edição de etapa (o id vem na confirmação, com latência do n8n) | ordenar só por `seq` não garante a ordem de **execução** no Telegram nem o `mensagemId` conhecido |
| `SaidaTelegram.Documento` mantém `conteudo` e ganha `ReferenciaDocumento` | o envio direto antigo precisa dos bytes; o v1 precisa da referência | remover os bytes quebraria o caminho antigo, que deve continuar intacto (US5.5) |
