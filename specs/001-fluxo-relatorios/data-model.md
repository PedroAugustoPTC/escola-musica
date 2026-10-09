# Data Model: Fluxo de envio e criação de relatórios pedagógicos (Web e Telegram)

**Feature**: `001-fluxo-relatorios` | **Data**: 2026-09-30 | **Plano**: [plan.md](./plan.md) | **Pesquisa**: [research.md](./research.md)

Convenções:

- Identificadores são UUID (v7, ordenável). Carimbos de tempo são `timestamptz` em UTC. Datas de
  aula e de período são `date`, calculadas no fuso da escola (`ESCOLA_FUSO_HORARIO`, padrão
  `America/Sao_Paulo`).
- Nomes em português sem acento (Princípio VI). Tabelas em `snake_case`; tipos Java em
  `PascalCase`.
- As seções estruturadas dos relatórios ficam em `jsonb`. O conteúdo de cada versão é uma fotografia
  imutável.
- O domínio (`nucleo`) não conhece essas tabelas. O adaptador de persistência converte por
  `*Mapeador`.

## Visão geral

```text
Professor 1 ──── N ProfessorAluno N ──── 1 Aluno
    │ 1                                       │
    ├── 0..1 VinculoTelegram (+ ConversaTelegram embutida)
    ├── N SessaoWeb
    │
    ├── N Aula 1 ──── 1 RelatorioAula ──┐
    │                                    ├── N VersaoRelatorio 1 ──── 1..2 DocumentoPdf
    ├── N RelatorioSemestral ───────────┘            │
    │                                    N InteracaoRelatorio (respostas e alterações)
    └── N TrabalhoProcessamento ──── N ChamadaIa

TentativaAcesso (isolada; chave = canal + hash da origem)
UpdateTelegramProcessado (isolada; deduplicação de webhook)
```

---

## 1. Professor

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| nome | texto(255) | obrigatório |
| email | texto(255) | obrigatório, único (minúsculas), formato de e-mail |
| ativo | booleano | padrão `true`; inativo não acessa nenhum canal |
| codigo_acesso_hash | texto(64) | HMAC-SHA-256 (hex) do código; único quando presente |
| codigo_acesso_expira_em | timestamptz | nulo se não há código |
| codigo_acesso_renovado_em | timestamptz | último momento em que a validade foi renovada (limita a escrita a 1 vez por hora) |
| criado_em / atualizado_em | timestamptz | preenchidos pela aplicação |

**Regras de domínio**

- `CodigoAcesso`: objeto de valor com 8 caracteres do alfabeto `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. A
  entrada é normalizada (trim e maiúsculas) antes do HMAC.
- `codigoValidoEm(t)` ⇔ hash presente ∧ `t < codigo_acesso_expira_em` ∧ `ativo`.
- `renovarValidade(t)`: `expira_em = t + 7 dias` (configurável). Vale para acesso bem-sucedido e
  para uso com sessão ou vínculo ativos, em qualquer canal (FR-005). Se `t − renovado_em < 1 h`, não
  grava. O vencimento do código não encerra sessões nem vínculos.
- Emitir ou reemitir código (carga inicial) gera um novo código, substitui o hash e reinicia a
  validade.

## 2. Aluno

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| nome | texto(255) | obrigatório |
| data_nascimento | date | obrigatório, não futura |
| cpf_cifrado | bytea | AES-256-GCM, IV de 12 bytes prefixado; CPF normalizado (11 dígitos, com dígitos verificadores válidos) |
| cpf_hash | texto(64) | HMAC-SHA-256 do CPF normalizado; único |
| nome_responsavel | texto(255) | obrigatório se menor de 18 anos na data da carga |
| instrumento | texto(80) | opcional (ex.: "Piano") |
| ativo | booleano | padrão `true` |
| criado_em / atualizado_em | timestamptz | `criado_em` participa do cálculo dos semestres disponíveis |

**Regras**: o CPF nunca é lido de volta para exibição nem vai para a IA (FR-011, FR-023). A idade
exibida no semestral é calculada a partir de `data_nascimento` na data de fim do período (ou hoje,
se o semestre estiver em andamento).

## 3. ProfessorAluno

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| professor_id | uuid | FK Professor |
| aluno_id | uuid | FK Aluno |
| criado_em | timestamptz | |

Único: `(professor_id, aluno_id)`. É a base de toda autorização (FR-009): toda operação sobre um
aluno exige a associação; sem ela, o erro é `ALUNO_NAO_ASSOCIADO`. Toda operação sobre um relatório
exige `relatorio.professor_id = professor da sessão ou vínculo`; senão, `RECURSO_NAO_ENCONTRADO`.

## 4. SessaoWeb

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| professor_id | uuid | FK Professor |
| token_hash | texto(64) | SHA-256 do token opaco (256 bits); único |
| criada_em | timestamptz | |
| ultimo_uso_em | timestamptz | atualizado a cada requisição (no máximo 1 escrita por minuto) |
| expira_em | timestamptz | `ultimo_uso_em + 12 h` (configurável) |

"Encerrar sessão" apaga o registro. Uma sessão expirada é recusada com `SESSAO_EXPIRADA` (401), e a
Web leva o professor para a tela "Entrar". Sessões vencidas são apagadas por varredura diária.

## 5. VinculoTelegram e ConversaTelegram

`VinculoTelegram` identifica o professor no canal. `ConversaTelegram` é um objeto de valor embutido
na mesma linha com o estado da conversa (FR-036). O adaptador não guarda nenhum estado em memória.

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| professor_id | uuid | FK, **único** (FR-006) |
| telegram_usuario_id | bigint | **único** (FR-006) |
| chat_id | bigint | chat privado para envio das mensagens |
| vinculado_em | timestamptz | |
| aluno_selecionado_id | uuid | nulo; limpo após aprovar ou cancelar (FR-038) |
| fluxo | enum | `NENHUM`, `REGISTRO_AULA`, `SEMESTRAL`, `HISTORICO` |
| etapa_fluxo | enum | ver tabela de estados da conversa em [contracts/telegram-conversa.md](./contracts/telegram-conversa.md) |
| dados_fluxo | jsonb | dados curtos da etapa (ex.: semestre escolhido, opções listadas, confirmação pendente) |
| audio_pendente_file_id | texto(255) | nulo; **somente referência** ao arquivo no Telegram (R-05) |
| audio_pendente_file_unique_id | texto(64) | nulo |
| audio_pendente_mime | texto(100) | nulo |
| audio_pendente_tamanho | bigint | nulo; recusado na chegada se > limite |
| audio_pendente_recebido_em | timestamptz | nulo; expira após 30 min (configurável) |
| relatorio_foco_tipo | enum | `AULA`, `SEMESTRAL` ou nulo |
| relatorio_foco_id | uuid | relatório apresentado no chat (perguntas ou conferência) |
| versao_apresentada | int | última versão enviada ao chat; usada na aprovação (FR-028) |
| atualizado_em | timestamptz | |
| bloqueio_otimista | bigint | versão para concorrência |

**Regras**

- Vincular: código válido ∧ conta sem vínculo ∧ professor sem vínculo. Os conflitos são
  `TELEGRAM_JA_VINCULADO` e `PROFESSOR_JA_VINCULADO`. O código não é consumido.
- "sair" apaga o vínculo e todo o estado da conversa.
- Um novo áudio substitui `audio_pendente_*`. O áudio pendente é descartado ao expirar, ao cancelar
  ou ao iniciar outra ação (FR-040).
- Com `relatorio_foco_*` em `AGUARDANDO_RESPOSTAS` ou `AGUARDANDO_CONFERENCIA`, o áudio vai direto
  para o relatório em foco (FR-027b). Escolher "Registrar aula" limpa o foco, sem cancelar o
  relatório.

## 6. TentativaAcesso

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| canal | enum | `WEB`, `TELEGRAM` |
| origem_hash | texto(64) | SHA-256 com segredo do IP (Web) ou do `telegram_usuario_id` (Telegram); nunca em claro |
| tentativas_incorretas | int | ≥ 0 |
| bloqueado_ate | timestamptz | nulo |
| atualizado_em | timestamptz | |

Único: `(canal, origem_hash)`. Com 5 falhas consecutivas (configurável), `bloqueado_ate = agora +
15 min` (configurável). O bloqueio é verificado **antes** de validar o código. Um sucesso zera o
contador, e o fim do bloqueio também. A gravação é feita numa transação independente (FR-007).

## 7. Aula

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| professor_id, aluno_id | uuid | FKs; associação obrigatória |
| data_aula | date | hoje no fuso da escola (FR-016); não editável |
| criado_em | timestamptz | |

Sem unicidade: várias aulas no mesmo dia são permitidas. É criada **junto com** o RelatorioAula,
somente depois de a transcrição dar certo (FR-017).

## 8. RelatorioAula

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| aula_id | uuid | FK, único |
| professor_id, aluno_id | uuid | FKs (desnormalizados) |
| transcricao | text | obrigatória; nunca registrada em log |
| conteudo | jsonb | `ConteudoRelatorioAula` corrente (5 seções) |
| perguntas_pendentes | jsonb | lista de textos; vazia fora de `AGUARDANDO_RESPOSTAS` |
| rodadas_perguntas | int | 0..2 (FR-025) |
| status | enum | ver máquina de estados 8.1 |
| etapa_processamento | enum | nulo quando não há trabalho em curso; senão `ESTRUTURANDO`, `INCORPORANDO_RESPOSTAS`, `TRANSCREVENDO_PEDIDO`, `APLICANDO_ALTERACAO`, `GERANDO_PDF` |
| versao_vigente | int | 0 até a primeira conferência; depois ≥ 1 |
| canal_origem | enum | `WEB`, `TELEGRAM` |
| motivo_falha | enum | nulo; ex.: `FALHA_ESTRUTURACAO` (código estável, sem detalhe interno) |
| criado_em / atualizado_em | timestamptz | `atualizado_em` é a "última alteração" mostrada na retomada |
| aprovado_em | timestamptz | nulo |
| versao_aprovada | int | nulo |
| canal_aprovacao | enum | nulo |
| cancelado_em | timestamptz | nulo |
| tempo_processamento_ms | bigint | soma das durações dos trabalhos (FR-056) |
| bloqueio_otimista | bigint | concorrência |

**ConteudoRelatorioAula** (objeto de valor): `conteudosTrabalhados: string[]`, `evolucao: string`,
`dificuldades: string[]`, `atividadesPropostas: string[]`, `observacoes: string`. Um campo vazio é
string ou lista vazia, nunca inventado (FR-020).

**Índice único parcial** (FR-034): `(professor_id, aluno_id)` com `status IN ('EM_PROCESSAMENTO',
'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')`.

### 8.1 Máquina de estados do RelatorioAula

```text
 [trabalho REGISTRO_AULA: áudio transcrito com sucesso]
                     │ cria Aula + RelatorioAula
                     ▼
            ┌──────────────────┐  estruturação falhou   ┌──────────────────────┐
            │ EM_PROCESSAMENTO │───────────────────────►│ FALHA_PROCESSAMENTO  │
            └──┬────────────┬──┘◄───── tentar de novo ──└──────────┬───────────┘
   perguntas   │            │ sem perguntas (ou 2 rodadas feitas)  │ cancelar
   (rodada<2)  ▼            ▼                                      ▼
 ┌──────────────────────┐  ┌────────────────────────┐        ┌───────────┐
 │ AGUARDANDO_RESPOSTAS │─►│ AGUARDANDO_CONFERENCIA │──────► │ CANCELADO │
 └───┬──────────────────┘  └───┬───────────┬────────┘cancelar└───────────┘
     │ resposta incorporada;   │ alteração │ aprovar (versão vigente)
     │ novas perguntas e       │ v+1       ▼
     │ rodada<2 → volta        └──►(mesmo) ┌──────────┐
     │ cancelar ─────────────────────────► │ APROVADO │ (terminal)
     ▼                                     └──────────┘
```

| Transição | De | Para | Condições e efeitos |
|---|---|---|---|
| criar | — | EM_PROCESSAMENTO | transcrição não vazia; aluno associado; nenhum relatório em aberto do par |
| estruturar com perguntas | EM_PROCESSAMENTO | AGUARDANDO_RESPOSTAS | `rodadas_perguntas += 1`; sem PDF |
| estruturar sem perguntas | EM_PROCESSAMENTO | AGUARDANDO_CONFERENCIA | cria versão 1 (origem `ESTRUTURACAO_INICIAL`) + PDF de conferência |
| falhar estruturação | EM_PROCESSAMENTO | FALHA_PROCESSAMENTO | transcrição preservada |
| tentar de novo | FALHA_PROCESSAMENTO | EM_PROCESSAMENTO | novo trabalho `NOVA_TENTATIVA`, sem áudio |
| responder | AGUARDANDO_RESPOSTAS | AGUARDANDO_RESPOSTAS ou AGUARDANDO_CONFERENCIA | resposta não vazia; incorporada pela IA (FR-025); se sobrarem perguntas e `rodadas < 2`, nova rodada; senão cria versão 1 (origem `RESPOSTA_PERGUNTAS`) |
| alterar | AGUARDANDO_CONFERENCIA | AGUARDANDO_CONFERENCIA | pedido não vazio ∧ `versaoApresentada = versao_vigente`; cria versão N+1 (origem `ALTERACAO`) + PDF; se a IA indicar `semAlteracaoDeConteudo`, nenhuma versão é criada |
| aprovar | AGUARDANDO_CONFERENCIA | APROVADO | `versaoConfirmada = versao_vigente` ∧ sem trabalho em curso; grava `aprovado_em`, `versao_aprovada` e `canal_aprovacao`; gera o PDF oficial |
| cancelar | EM_PROCESSAMENTO (só por descarte na retomada), FALHA_PROCESSAMENTO, AGUARDANDO_RESPOSTAS, AGUARDANDO_CONFERENCIA | CANCELADO | confirmação explícita no canal; resultado de trabalho em curso é descartado |

**Proibições** (código de erro entre parênteses): aprovar fora de `AGUARDANDO_CONFERENCIA`
(`ACAO_NAO_PERMITIDA_NA_ETAPA`); aprovar ou alterar versão desatualizada (`VERSAO_DESATUALIZADA`,
que devolve a versão vigente); qualquer ação sobre `APROVADO` (`RELATORIO_JA_APROVADO`) ou
`CANCELADO` (`RELATORIO_CANCELADO`); aprovar, alterar ou responder com `etapa_processamento ≠ nulo`
(`PROCESSAMENTO_EM_ANDAMENTO`).

## 9. RelatorioSemestral

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| professor_id, aluno_id | uuid | FKs; associação obrigatória |
| periodo_chave | texto(6) | `AAAA-S` (S ∈ {1,2}) |
| periodo_inicio / periodo_fim | date | 1 = 01/01–30/06; 2 = 01/07–31/12 |
| quantidade_aulas_consideradas | int | ≥ 1 (FR-042) |
| quantidade_relatorios_em_aberto | int | ≥ 0, no momento da geração; repetido como aviso na conferência |
| informacoes_gerais | jsonb | preenchido **pelo sistema** (FR-044): nome, idade, instrumento, professor, período, quantidade de aulas |
| conteudo | jsonb | `ConteudoRelatorioSemestral` corrente (seções 2 a 10) |
| status | enum | `AGUARDANDO_CONFERENCIA`, `APROVADO`, `CANCELADO` |
| etapa_processamento | enum | nulo, `TRANSCREVENDO_PEDIDO`, `APLICANDO_ALTERACAO` ou `GERANDO_PDF` |
| versao_vigente | int | ≥ 1 |
| canal_origem | enum | |
| criado_em / atualizado_em / aprovado_em | timestamptz | |
| versao_aprovada, canal_aprovacao, cancelado_em | | como no RelatorioAula |
| tempo_processamento_ms | bigint | |
| bloqueio_otimista | bigint | |

**ConteudoRelatorioSemestral**: `frequenciaEEstudo {frequenciaAulas, regularidadeEstudoEmCasa,
engajamento, comentarioGeral}`, `tecnica {conteudosTrabalhados[], evolucaoTecnica,
pontosADesenvolver[], estudosRealizados[]}`, `musicalidade {repertorioEstudado[], som, ritmo,
fraseado, articulacao, estilo, comentarioMusical}`, `leituraEMemorizacao {avaliacao, comentario}`,
`pontosDeAtencao {itens[]}`, `estrategiasPedagogicas {itens[]}`, `acompanhamentoFamiliar
{nivelDePresenca, observacaoDoProfessor}`, `planejamentoProximoSemestre {metasTecnicas[],
metasMusicais[], habitosDeEstudo[], repertorioProposto[], estudosTecnicos[], leituraETeoria[],
projetoDoSemestre}`, `parecerFinal: string`. As **Informações Gerais** (seção 1) não fazem parte do
que a IA gera nem do que ela pode alterar.

**Índices únicos parciais**: `(professor_id, aluno_id, periodo_chave) WHERE status = 'APROVADO'`
(FR-046) e `(professor_id, aluno_id, periodo_chave) WHERE status = 'AGUARDANDO_CONFERENCIA'`
(FR-034).

**Máquina de estados**: nasce em `AGUARDANDO_CONFERENCIA`, versão 1, com PDF, **somente depois de a
consolidação dar certo** (FR-045). Até lá existe só o `TrabalhoProcessamento` do tipo
`CONSOLIDACAO_SEMESTRAL`. Se a consolidação falhar, nada é criado e o professor pode gerar de novo.
Alterar, aprovar e cancelar seguem as transições e proibições do RelatorioAula.

**Semestres disponíveis** (FR-041): do semestre de `min(data da primeira aula do aluno com o
professor, aluno.criado_em no fuso da escola)` até o semestre corrente, em ordem decrescente, com o
mais recente pré-selecionado e o corrente marcado como "em andamento".

**Pré-condições de geração** (FR-042, FR-046, FR-034), nesta ordem: (1) aluno associado; (2) nenhum
semestral aprovado para o trio, senão `SEMESTRAL_JA_APROVADO`; (3) nenhum semestral em aberto nem
consolidação em curso para o trio, senão `RELATORIO_EM_ABERTO`, com opção de continuar ou descartar;
(4) contagem de aulas aprovadas no período > 0, senão `SEM_AULAS_APROVADAS_NO_PERIODO`, sem nada
criado e sem chamada à IA; (5) se houver relatórios de aula em aberto no período, é preciso
`confirmarMesmoComEmAberto = true`, senão `HA_RELATORIOS_EM_ABERTO` com a quantidade.

## 10. VersaoRelatorio

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| relatorio_tipo | enum | `AULA`, `SEMESTRAL` |
| relatorio_id | uuid | FK lógica para o relatório do tipo |
| numero | int | ≥ 1; único por `(relatorio_tipo, relatorio_id, numero)` |
| conteudo | jsonb | fotografia imutável (para o semestral, inclui `informacoes_gerais`) |
| origem | enum | `ESTRUTURACAO_INICIAL`, `RESPOSTA_PERGUNTAS`, `ALTERACAO`, `CONSOLIDACAO` |
| interacao_id | uuid | FK InteracaoRelatorio; nulo para `ESTRUTURACAO_INICIAL` e `CONSOLIDACAO` |
| canal | enum | canal em que a mudança foi pedida |
| prompt_versao | texto(40) | ex.: `revisao-aula.v1` |
| criada_em | timestamptz | |

Imutável depois de criada: nunca é atualizada nem apagada (Princípio III, FR-027).

## 11. InteracaoRelatorio

Registra cada resposta às perguntas e cada pedido de alteração, por texto ou por áudio (FR-056).

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| relatorio_tipo, relatorio_id | | |
| tipo | enum | `RESPOSTA_PERGUNTAS`, `ALTERACAO` |
| canal | enum | `WEB`, `TELEGRAM` |
| meio | enum | `TEXTO`, `AUDIO` |
| texto | text | digitado ou transcrito; é o "texto entendido" mostrado ao professor (FR-024a, FR-027a) |
| versao_base | int | versão vigente quando o pedido foi feito |
| rodada | int | para respostas: 1 ou 2 |
| resultado | enum | `APLICADA`, `SEM_ALTERACAO_DE_CONTEUDO`, `PEDIDO_DE_ACAO_IGNORADO`, `FALHOU`, `RECUSADA_VERSAO_DESATUALIZADA` |
| versao_resultante | int | nula se nada foi criado |
| criado_em | timestamptz | |

Uma transcrição que falha ou vem vazia **não** gera interação: o relatório fica como está e a rodada
não é consumida.

## 12. DocumentoPdf

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| versao_id | uuid | FK VersaoRelatorio |
| tipo | enum | `CONFERENCIA` (toda versão) ou `OFICIAL` (só a versão aprovada) |
| chave_armazenamento | texto(255) | interna; nunca exposta ao cliente (R-11) |
| tamanho_bytes | bigint | |
| sha256 | texto(64) | integridade |
| gerado_em | timestamptz | |

Único: `(versao_id, tipo)`. A entrega exige autorização pelo relatório (FR-048). O histórico entrega
sempre o `OFICIAL`.

## 13. TrabalhoProcessamento

Fila persistida (R-06) e fonte das etapas mostradas nos dois canais.

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| tipo | enum | `REGISTRO_AULA`, `NOVA_TENTATIVA`, `RESPOSTA_PERGUNTAS`, `ALTERACAO`, `CONSOLIDACAO_SEMESTRAL` |
| professor_id, aluno_id | uuid | |
| relatorio_tipo, relatorio_id | | nulos em `REGISTRO_AULA` (antes da transcrição) e em `CONSOLIDACAO_SEMESTRAL` |
| canal_solicitacao | enum | canal que recebe o resultado (FR-021) |
| meio | enum | `TEXTO` ou `AUDIO` |
| parametros | jsonb | ex.: `periodoChave`, `confirmarMesmoComEmAberto`, `versaoBase`, texto digitado, duração do áudio |
| arquivo_temporario | texto(255) | caminho no `tmpfs`; apagado depois da transcrição |
| status | enum | `PENDENTE`, `EM_EXECUCAO`, `CONCLUIDO`, `FALHOU`, `DESCARTADO` |
| etapa | enum | `ENVIANDO`, `TRANSCREVENDO`, `ESTRUTURANDO`, `TRANSCREVENDO_PEDIDO`, `APLICANDO_ALTERACAO`, `INCORPORANDO_RESPOSTAS`, `CONSOLIDANDO`, `GERANDO_PDF`, `PRONTO` |
| resultado_codigo | texto(60) | código estável (ex.: `PRONTO_PARA_CONFERENCIA`, `PERGUNTAS_PENDENTES`, `AUDIO_NAO_PROCESSAVEL`) |
| relatorio_resultante_id | uuid | preenchido quando o trabalho cria o relatório |
| tentativas | int | ≤ 2 para erros transitórios |
| aviso_demora_enviado | booleano | aviso aos 3 minutos (caso de borda) |
| criado_em / iniciado_em / concluido_em | timestamptz | |

**Índice único parcial**: `(professor_id, aluno_id) WHERE tipo = 'REGISTRO_AULA' AND status IN
('PENDENTE','EM_EXECUCAO')`. Com ele, um áudio em transcrição conta como "relatório em aberto" e não
se inicia um segundo registro para o mesmo aluno.

## 14. ChamadaIa

| Campo | Tipo | Regras |
|---|---|---|
| id | uuid | PK |
| trabalho_id | uuid | FK |
| relatorio_tipo, relatorio_id | | nulos se a chamada falhou antes da criação |
| tarefa | enum | `TRANSCRICAO`, `ESTRUTURACAO`, `INCORPORACAO_RESPOSTAS`, `REVISAO`, `CONSOLIDACAO` (a chamada de correção do JSON não tem linha própria: seus tokens e custo somam-se à chamada que a exigiu, inclusive quando a saída continua inválida ou o provedor cai na correção) |
| provedor, modelo | texto | valores de configuração (nunca no núcleo) |
| segundos_audio | numeric | transcrição |
| tokens_entrada, tokens_saida | int | LLM |
| duracao_ms | bigint | |
| custo_estimado_usd | numeric(12,6) | pela tabela de preços configurada |
| sucesso | booleano | |
| codigo_erro | texto(60) | nulo |
| criado_em | timestamptz | |

Não guarda prompt, resposta nem transcrição (FR-055).

## 15. UpdateTelegramProcessado

| Campo | Tipo | Regras |
|---|---|---|
| update_id | bigint | PK |
| recebido_em | timestamptz | apagado após 7 dias |

Idempotência do webhook: um update repetido é confirmado sem reprocessar.

---

## Relatório em aberto (visão derivada)

Os relatórios em aberto de um professor aparecem no início dos dois canais (FR-012, FR-035). São a
união de:

1. `RelatorioAula` com status em `{EM_PROCESSAMENTO, FALHA_PROCESSAMENTO, AGUARDANDO_RESPOSTAS,
   AGUARDANDO_CONFERENCIA}`;
2. `TrabalhoProcessamento` `REGISTRO_AULA` ou `CONSOLIDACAO_SEMESTRAL` com status `PENDENTE` ou
   `EM_EXECUCAO` (exibido como "Em processamento");
3. `RelatorioSemestral` em `AGUARDANDO_CONFERENCIA`.

Cada item traz aluno, tipo, status ou etapa, canal de origem e `atualizado_em`.

## Histórico (visão derivada)

`RelatorioAula` e `RelatorioSemestral` com `status = APROVADO`, `professor_id` = professor
autenticado e `aluno_id` = aluno associado, ordenados do mais recente ao mais antigo (aula por
`data_aula`, semestral por `periodo_inicio`; empate desfeito por `aprovado_em`). Rótulos: "Aula de
dd/mm/aaaa" e "Semestral (dd/mm/aaaa a dd/mm/aaaa)". Data de aprovação: "Aprovado em dd/mm/aaaa às
hh:mm", no fuso da escola (FR-047).
