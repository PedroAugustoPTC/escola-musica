# HANDOFF — Sistema de Relatórios Pedagógicos por Voz (MVP → Definitivo)

> **Cliente:** Instituto Musical Felipe Blaft (escola de música)
> **Origem:** MVP `mvp-relatorios` (versão de referência: commit `72fbc1f`, 30/09/2026)
> **Destino:** sistema definitivo, construído do zero com **Spec Kit / SDD** (Spec-Driven Development)
> **Público deste documento:** agente/desenvolvedor que vai escrever a constituição, as specs, os planos e as tarefas do definitivo.
> **Resolução de caminhos:** todo caminho de arquivo citado neste documento (Apêndice B, `assets/`, etc.) é relativo à raiz do repositório do MVP, disponívelsomente para leitura em `E:\Projetos\mvp-relatorios`
---

## 0. Como usar este documento

1. Este documento descreve **o que o MVP faz e por quê** (regras de negócio, fluxos, contratos, textos, decisões e aprendizados). Ele **não** impõe stack: onde uma tecnologia é citada, é apenas para registrar como o MVP resolveu o problema. O definitivo pode (e provavelmente vai) trocar de stack.
2. **Linguagem normativa:**
   - **RN-xx** = Regra de Negócio vigente no MVP que deve ser preservada no definitivo (salvo decisão explícita em `/speckit.clarify`).
   - **[MVP]** = comportamento/implementação do MVP citado como referência, não obrigatório.
   - **[RECOMENDAÇÃO]** = melhoria sugerida para o definitivo (seção 17). Não é comportamento do MVP.
   - **[DECISÃO PENDENTE]** = ponto que precisa ser decidido com o cliente antes/durante o `/speckit.clarify`.
3. **Decisões já tomadas pelo dono do produto para o definitivo:**
   - O **design será totalmente novo**, baseado na logomarca do professor (seção 15). Nada da UI visual do MVP deve ser reaproveitado; só o comportamento funcional.
   - O **Telegram continua** como uma das portas de entrada do áudio, **com o mesmo escopo do MVP**: canal exclusivo de **registro de aula por áudio** (vincular conta, escolher aluno, enviar áudio, responder pergunta, pedir alteração, aprovar, cancelar). Relatório semestral, histórico e administração ficam **somente na Web**.
   - A **página Web** também é porta de entrada do áudio (gravar no navegador ou enviar arquivo).
   - As stacks podem mudar; a arquitetura deve manter os provedores de IA (transcrição e LLM) **plugáveis**.
4. Sugestão de uso no Spec Kit: seção 18 (constituição) → `/speckit.constitution`; seções 2–14 → insumo para `/speckit.specify` de cada feature (quebra sugerida na seção 19); seção 17 e itens [DECISÃO PENDENTE] → `/speckit.clarify`.

---

## 1. Sumário executivo

O professor grava um áudio contando como foi a aula de um aluno. O sistema **transcreve** o áudio (Speech-to-Text), uma **IA (LLM) estrutura** o conteúdo em um relatório pedagógico (conteúdos trabalhados, evolução, dificuldades, atividades propostas, observações), **pergunta ativamente** o que estiver faltando, gera um **PDF para conferência** e só grava o relatório no histórico do aluno depois que o professor **aprova explicitamente a versão vigente**. O professor pode pedir alterações em **linguagem natural** ("mude a dificuldade para ritmo"), o que gera uma nova versão e um novo PDF.

A cada semestre, o professor escolhe um semestre pré-definido e o sistema **consolida todos os relatórios de aula aprovados** daquele aluno no período em um **relatório semestral** completo (10 seções pedagógicas), com o mesmo ciclo de conferência em PDF → alteração → aprovação.

Uma **secretaria (administrador)** cadastra professores e alunos, associa alunos a professores, emite códigos de vinculação e consulta o histórico de qualquer aluno.

Canais do professor: **Bot do Telegram** (registro de aula) e **Portal Web do Professor** (registro de aula, semestral, histórico, retomada de rascunho). Os dois canais compartilham a **mesma identidade de professor** e o **mesmo ciclo de vida de relatório** — um rascunho iniciado num canal pode ser concluído no outro.

---

## 2. Visão do produto

### 2.1 Dores do cliente
1. Após cada aula, precisa registrar um relatório individual de cada aluno, sem perder tempo digitando.
2. A cada 6 meses, precisa produzir um relatório consolidado de cada aluno com base nos registros das aulas.

### 2.2 Proposta de valor
"Professor manda um áudio e recebe um relatório pronto para revisar." Baixo atrito, uso repetido sem fricção.

### 2.3 Métrica principal de sucesso
**Tempo gasto pelo professor antes vs. depois do sistema.** Critérios complementares validados no MVP:
1. Registrar uma aula rapidamente (pelo Telegram ou Web).
2. Transcrição suficientemente boa.
3. IA interpreta corretamente as informações pedagógicas.
4. Professor faz poucas correções.
5. Relatório final economiza tempo.
6. Relatório semestral representa adequadamente a evolução do aluno.
7. Professor confia no sistema.
8. Fluxo usado repetidamente sem fricção.

Métricas de custo a medir: custo por áudio, por aula, por aluno, por relatório semestral e custo mensal total (transcrição + LLM + infraestrutura).

### 2.4 Atores

| Ator | Descrição | Como se autentica (MVP) | Canais |
|---|---|---|---|
| **Administrador / Secretaria** | Opera a escola: cadastra professores e alunos, associa alunos a professores, emite códigos de vinculação, consulta histórico de qualquer aluno. | E-mail + senha (hash BCrypt), sessão de 2h. Não existe tela/endpoint para criar administrador (somente via seed/banco). | Web (área administrativa) |
| **Professor** | Ministra aulas, registra relatórios de aula, gera relatórios semestrais, consulta histórico **dos seus alunos**. | **Código de vinculação** (mesmo código nos dois canais). Telegram: vínculo permanente pelo `telegramUserId`. Web: sessão deslizante de 12h de inatividade. Professor **não tem** e-mail/senha. | Telegram + Web (portal do professor) |
| **Aluno / Responsável** | Sujeito dos dados (possivelmente menor de idade). Não acessa o sistema no MVP. | — | — |
| **IA (provedores externos)** | Speech-to-Text (transcrição) e LLM (estruturação, perguntas, revisão, consolidação). | Chave de API (segredo de ambiente). | Chamados apenas pelo backend. |
| **Orquestrador do canal Telegram** | [MVP: n8n] Recebe o webhook do Telegram, consulta o backend a cada mensagem e envia respostas. Não tem regra de negócio. | Token de serviço compartilhado (header). | Telegram ↔ backend |

### 2.5 Fora de escopo no MVP (candidatos a roadmap)
Geração semestral automática (cron), WhatsApp, financeiro, app mobile nativo, agenda, presença, dashboard analítico, sugestão de exercícios, objetivos personalizados, personalização do estilo de escrita do professor, notificações proativas.

---

## 3. Glossário do domínio

| Termo | Definição |
|---|---|
| **Professor** | Pessoa que ministra aulas. Identificado por e-mail único. Pode ter vários alunos. |
| **Aluno** | Pessoa que recebe aulas. Identificado unicamente pelo **CPF** (do aluno ou do responsável, se menor). Pode ter vários professores. |
| **Associação Professor–Aluno** | Vínculo de responsabilidade pedagógica (N:N). Base do **controle de acesso**: professor só vê/gera/aprova relatórios de alunos associados a ele. |
| **Código de vinculação** | Código alfanumérico curto emitido pela secretaria para o professor. Serve para (a) vincular a conta do Telegram (uso único) e (b) entrar no portal web (reutilizável até expirar). |
| **Vínculo Telegram** | Associação 1:1 entre um `telegramUserId` e um professor. Identificador de integração, **não** prova de autenticação para ações administrativas. Guarda também o **aluno selecionado** para o próximo áudio. |
| **Aula** | Evento pontual de ensino (professor, aluno, data). Várias aulas no mesmo dia para o mesmo par são permitidas (sem deduplicação). Cada aula gera exatamente um relatório de aula. |
| **Transcrição** | Texto produzido pelo Speech-to-Text a partir do áudio. É persistida no relatório de aula. O áudio **não** é persistido. |
| **Relatório de Aula** | Registro estruturado de uma aula: conteúdos trabalhados, evolução, dificuldades, atividades propostas, observações, perguntas pendentes, versão, status, PDF. |
| **Perguntas pendentes** | Perguntas objetivas geradas pela IA quando uma informação relevante não pôde ser extraída com confiança (ex.: tarefa de casa não mencionada). Bloqueiam a geração do PDF até serem respondidas. |
| **Rascunho / relatório em aberto** | Relatório de aula em `RASCUNHO` ou `PENDENTE_REVISAO` (ou semestral em `PENDENTE_REVISAO`). Pode ser retomado em qualquer canal. |
| **Versão** | Inteiro iniciado em 1, incrementado a cada alteração solicitada pelo professor. Cada versão tem seu próprio PDF. A aprovação exige confirmar a versão vigente. |
| **Aprovação** | Confirmação explícita do professor sobre o PDF da versão vigente. Torna o relatório oficial e visível no histórico. Irreversível. |
| **Cancelamento** | Encerramento definitivo de um relatório que o professor decidiu não concluir (status terminal, o registro não é apagado). Proibido para relatório aprovado. |
| **Relatório Semestral** | Consolidação, feita pela IA, de todos os relatórios de aula **aprovados** de um aluno em um semestre, em 10 seções pedagógicas. |
| **Semestre / chave de período** | Período fixo: 1º semestre = 01/jan a 30/jun; 2º semestre = 01/jul a 31/dez. Chave no formato `AAAA-S` (ex.: `2026-1`), rótulo "1º semestre 2026". |
| **Canal de origem** | `TELEGRAM` ou `WEB`: canal em que o relatório foi criado. Apenas informativo; nenhuma regra se ramifica por canal. |
| **Histórico do aluno** | Lista cronológica dos relatórios **aprovados** (aula + semestral) de um aluno, de todos os canais. |

---

## 4. Modelo de dados conceitual

> Os nomes abaixo são os do MVP (português). Tipos são conceituais. Todas as entidades usam UUID como identificador e carimbos de tempo com fuso (timestamptz).

### 4.1 Diagrama de relacionamentos

```text
Administrador (isolado)

Professor 1 ──── 0..1 VinculoTelegram ──── 0..1 Aluno (aluno selecionado no Telegram)
    │ N
    │
    │ N        (ProfessorAluno: associação N:N, par único)
  Aluno
    │
Aula (professor, aluno, data)  1 ──── 1  RelatorioAula
RelatorioSemestral (aluno, professor, período)  ── consolida N RelatorioAula aprovados (não há FK; é derivado por período)
TentativaAutenticacaoWeb (rate limit do login do portal, por origem)
```

### 4.2 Entidades

#### Professor
| Campo | Tipo | Regras |
|---|---|---|
| id | UUID | PK |
| nome | texto (255) | obrigatório |
| email | texto (255) | obrigatório, **único**, formato e-mail |
| ativo | booleano | default `true` (no MVP não há tela para desativar; login no portal exige `ativo=true`) |
| codigoVinculacao | texto (64) | **único** quando presente; nulo após uso no Telegram |
| codigoVinculacaoExpiraEm | timestamp | nulo quando não há código |
| criadoEm / atualizadoEm | timestamp | obrigatórios |

Regra de domínio: `codigoVinculacaoValidoEm(momento)` ⇔ código não nulo **e** expiração não nula **e** `momento < expiração`.

#### Aluno
| Campo | Tipo | Regras |
|---|---|---|
| id | UUID | PK |
| nome | texto (255) | obrigatório |
| dataNascimento | data | obrigatório |
| cpfCriptografado | binário | obrigatório; CPF normalizado (só dígitos) cifrado com criptografia simétrica autenticada (AES-GCM, IV aleatório de 12 bytes prefixado) |
| cpfHash | texto (128) | obrigatório, **único**; HMAC-SHA-256 determinístico do CPF normalizado (para checar duplicidade sem decifrar) |
| nomeResponsavel | texto (255) | obrigatório **se menor de 18 anos** na data do cadastro |
| ativo | booleano | default `true` (não utilizado no MVP) |
| criadoEm / atualizadoEm | timestamp | `criadoEm` é usado para calcular os semestres disponíveis |

Regra de domínio: `menorDeIdade` ⇔ anos completos entre `dataNascimento` e a data de referência < 18 (considera aniversário).
O CPF **nunca** é devolvido pela API.

#### ProfessorAluno (associação)
| Campo | Regras |
|---|---|
| id, professorId (FK), alunoId (FK), criadoEm | par (professorId, alunoId) **único** |

#### VinculoTelegram
| Campo | Regras |
|---|---|
| id | PK |
| professorId | FK, **único** (1 Telegram por professor) |
| telegramUserId | texto (64), **único** (1 professor por conta Telegram) |
| vinculadoEm | timestamp |
| alunoSelecionadoId | UUID, nulo — aluno escolhido na última mensagem de texto, usado pelo próximo áudio |
| alunoSelecionadoEm | timestamp, nulo — gravado mas não lido no MVP |

#### Aula
| Campo | Regras |
|---|---|
| id, professorId (FK), alunoId (FK), dataAula (data), criadoEm | **sem** unicidade: várias aulas no mesmo dia para o mesmo par são permitidas |

#### RelatorioAula
| Campo | Tipo | Regras |
|---|---|---|
| id | UUID | PK |
| aulaId | UUID | FK, **único** (1 relatório por aula) |
| professorId, alunoId | UUID | FKs (desnormalizados da aula) |
| transcricao | texto longo | texto do STT |
| conteudosTrabalhados | lista de strings (JSON) | |
| evolucao | texto | |
| dificuldades | lista de strings (JSON) | |
| atividadesPropostas | lista de strings (JSON) | "tarefas de casa"/exercícios propostos |
| observacoes | texto | respostas às perguntas de acompanhamento são anexadas aqui |
| perguntasPendentes | lista de strings (JSON) | default `[]`; persistida para permitir acompanhamento por consulta/polling |
| versao | inteiro | default 1 |
| status | enum | `RASCUNHO` (default), `PENDENTE_REVISAO`, `APROVADO`, `CANCELADO` |
| pdfUrl | texto (1024) | rota HTTP do PDF da versão vigente |
| canalOrigem | enum | `TELEGRAM` (default) ou `WEB` |
| criadoEm / atualizadoEm / aprovadoEm | timestamp | |

#### RelatorioSemestral
| Campo | Tipo | Regras |
|---|---|---|
| id | UUID | PK |
| alunoId, professorId | UUID | FKs (professorId = quem gerou) |
| periodoInicio / periodoFim | data | derivados da chave do semestre |
| quantidadeRelatoriosAulaConsiderados | inteiro | quantos relatórios de aula aprovados entraram na consolidação |
| informacoesGerais, frequenciaEEstudo, tecnica, musicalidade, leituraEMemorizacao, pontosDeAtencao, estrategiasPedagogicas, acompanhamentoFamiliar, planejamentoProximoSemestre | JSON (objeto) | seções estruturadas (schema na seção 8.4) |
| parecerFinal | texto | |
| versao | inteiro | default 1 |
| status | enum | `PENDENTE_REVISAO` (default), `APROVADO`, `CANCELADO` (não existe RASCUNHO) |
| pdfUrl | texto | |
| canalOrigem | enum | `TELEGRAM`/`WEB` (na prática, só WEB no escopo atual) |
| criadoEm / atualizadoEm / aprovadoEm | timestamp | |

Não há unicidade por (aluno, período): é possível gerar mais de um semestral para o mesmo aluno/período.

#### Administrador
| Campo | Regras |
|---|---|
| id, email (único), senhaHash (BCrypt), criadoEm | criado apenas por seed/banco no MVP |

#### TentativaAutenticacaoWeb (rate limit do portal)
| Campo | Regras |
|---|---|
| id, identificador (único; **hash SHA-256 do IP de origem**, nunca o IP em claro), tentativasIncorretas, bloqueadoAte, atualizadoEm | ver RN-23 |

---

## 5. Regras de negócio (RN)

### 5.1 Cadastro e identidade
- **RN-01 — Cadastro de professor:** nome e e-mail obrigatórios; e-mail com formato válido e **único** (conflito → `PROFESSOR_EMAIL_DUPLICADO`, "Ja existe um professor cadastrado com este e-mail."). Ao cadastrar, o professor nasce `ativo` e já recebe um **código de vinculação** com validade (RN-05). O código e a validade são exibidos à secretaria para repasse ao professor.
- **RN-02 — Cadastro de aluno:** nome, data de nascimento e CPF obrigatórios. O CPF é normalizado (remove tudo que não é dígito), **cifrado** em repouso e tem um **hash determinístico** para checar unicidade. CPF duplicado → `ALUNO_CPF_DUPLICADO`, "Ja existe um aluno cadastrado com este CPF.". O CPF pode ser do aluno ou do responsável (se menor). O CPF nunca é exibido nem retornado.
- **RN-03 — Menor de idade:** se o aluno tem menos de 18 anos (anos completos na data do cadastro), **nome do responsável é obrigatório** (erro de validação caso ausente). A UI mostra o campo somente quando a data digitada indica menor.
- **RN-04 — Associação a professores:** no cadastro do aluno, a secretaria escolhe **zero ou mais** professores. Professor inexistente → 404. A associação é a base de todo o controle de acesso (RN-18). No MVP não há como alterar associações depois do cadastro.

### 5.2 Código de vinculação e autenticação do professor
- **RN-05 — Formato e validade do código:** 8 caracteres do alfabeto `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (32 símbolos, sem caracteres ambíguos como 0/O, 1/I), gerado com aleatoriedade criptográfica, **único** entre professores (até 10 tentativas de geração; falha → erro). Validade: **7 dias** a partir da emissão (era 24h originalmente; aumentado para reduzir fricção).
- **RN-06 — Reemissão:** a secretaria pode gerar um **novo código** para qualquer professor a qualquer momento (substitui o anterior, nova validade de 7 dias). Uso típico: código expirou, professor quer entrar no portal, ou precisa vincular novo Telegram.
- **RN-07 — Vinculação do Telegram (uso único):** o professor envia o código ao bot. Se válido: cria o vínculo `telegramUserId ↔ professor` e **invalida o código** (fica nulo). Regras de conflito:
  - Código inexistente/expirado → "Codigo de vinculacao invalido ou expirado" (404).
  - Conta Telegram já vinculada a **outro** professor → `TELEGRAM_JA_VINCULADO` (409).
  - Professor já vinculado a **outra** conta Telegram → `PROFESSOR_JA_VINCULADO` (409).
  - Mesmo par já vinculado → apenas consome o código (idempotente).
  - O vínculo é permanente (não há desvinculação no MVP).
- **RN-08 — Login no portal web (código reutilizável):** o professor informa o **mesmo código** no portal. Validações: código existe, não expirou e professor `ativo`. **O login no portal NÃO consome o código** (sem ele o professor não teria como reautenticar após a sessão expirar). A cada login bem-sucedido, a validade do código é **renovada por mais 7 dias** (quem usa com regularidade nunca vê o código expirar). Código inválido → `CODIGO_INVALIDO` (401) "Código inválido ou expirado. Peça um novo código à secretaria da escola."
- **RN-09 — Interação Telegram × portal:** como a vinculação do Telegram consome o código, um professor que vincula o Telegram **depois** precisa de um novo código para entrar no portal. [DECISÃO PENDENTE] no definitivo (ver seção 17).
- **RN-10 — Identidade única entre canais:** não existe "conta web" separada: o código aponta para o mesmo registro de Professor usado pelo bot. Relatórios, rascunhos e histórico são compartilhados entre canais.
- **RN-11 — Telegram não é autenticação administrativa:** o `telegramUserId` é um identificador de integração; nunca autoriza ações de cadastro/edição/administração.

### 5.3 Sessões
- **RN-12 — Sessão do administrador:** token com validade fixa de **2 horas**, sem renovação.
- **RN-13 — Sessão do professor (deslizante):** token com validade de **12 horas de inatividade** (configurável). A cada requisição autenticada, o servidor emite um token novo com a janela reiniciada (MVP: header de resposta `X-Refreshed-Token`), e o cliente substitui o token armazenado.
- **RN-14 — Separação de papéis:** token de professor nunca autentica rotas administrativas e vice-versa. A única rota aceita pelos dois é o download de PDF.

### 5.4 Registro de aula e relatório de aula
- **RN-15 — Data da aula:** é a data **de hoje no fuso da escola** (default `America/Sao_Paulo`), nunca o fuso do servidor. O canal web aceita uma `dataAula` opcional (a UI do MVP não envia). Aula registrada às 21h em Brasília não pode "virar" o dia seguinte.
- **RN-16 — Aluno deve estar associado ao professor:** registrar aula para aluno não associado → `ALUNO_NAO_ASSOCIADO` (403).
- **RN-17 — Transcrição antes de persistir:** o áudio é transcrito **antes** de criar qualquer registro. Se o áudio não puder ser transcrito (vazio, corrompido, silêncio, formato não suportado, falha do provedor), **nenhuma Aula/Relatório é criada** → `AUDIO_NAO_PROCESSAVEL` (422). Áudio acima do limite (50 MB no MVP) → `AUDIO_MUITO_GRANDE` (413) "O audio enviado e maior que o limite permitido. Grave um trecho mais curto."
- **RN-18 — Controle de acesso por professor:** o professor só enxerga, gera, revisa, aprova e cancela relatórios de alunos associados a ele. Aluno de outro professor → **403** (o professor sabe que o aluno existe). Relatório de outro professor → **404** (não confirmar a existência do recurso). O identificador do professor vem **sempre do token**, nunca da URL/corpo.
- **RN-19 — Áudio não é retido:** o áudio só existe em memória durante a transcrição; não é gravado em disco nem em banco. Permanecem apenas a transcrição e o relatório. (Se um provedor exigir arquivo temporário, ele deve ser apagado imediatamente após a chamada.)
- **RN-20 — Estruturação pela IA:** a transcrição é enviada ao LLM, que devolve JSON com `conteudosTrabalhados[]`, `evolucao`, `dificuldades[]`, `atividadesPropostas[]`, `observacoes`, `perguntasPendentes[]`. O JSON é validado antes de persistir (1 tentativa automática de correção de JSON inválido; se falhar, erro).
  - Sem perguntas pendentes → status `PENDENTE_REVISAO` e o **PDF é gerado automaticamente**.
  - Com perguntas pendentes → permanece `RASCUNHO`, perguntas persistidas, **sem PDF**.
- **RN-21 — Perguntas de acompanhamento (simplificação do MVP):** a resposta do professor (texto livre, obrigatório) é **anexada às observações** como `"Pergunta de acompanhamento respondida pelo professor: <resposta>"` (em nova linha se já houver observações). Após **qualquer** resposta, **todas** as pendências são consideradas resolvidas, o status vai para `PENDENTE_REVISAO` e o PDF é gerado. A resposta **não** é reprocessada pela IA. (Ver recomendação na seção 17.)
- **RN-22 — Alteração em linguagem natural:** o professor descreve a alteração (texto obrigatório). O relatório atual (JSON) + instrução vão ao LLM; o resultado substitui os campos, a **versão é incrementada**, status `PENDENTE_REVISAO`, **novo PDF** (arquivo novo por versão; os antigos permanecem no storage).
- **RN-23 — Aprovação explícita da versão vigente:** a aprovação exige informar `versaoConfirmada`. Se diferente da versão atual → 409 ("Versao confirmada (X) nao corresponde a versao vigente (Y) do relatorio; o PDF pode estar desatualizado"). Só aprova a partir de `PENDENTE_REVISAO` (senão 409). Aprovado ⇒ status `APROVADO`, `aprovadoEm` registrado, entra no histórico. Aprovação é irreversível.
- **RN-24 — Cancelamento:** professor pode cancelar relatório em `RASCUNHO` ou `PENDENTE_REVISAO` ⇒ `CANCELADO` (terminal, o registro não é apagado, mantém rastreabilidade e integridade). Cancelar aprovado → 409 "Relatorio de aula ja aprovado nao pode ser cancelado"; cancelar cancelado → 409.
- **RN-25 — Rascunho pendente (retomada cross-channel):** ao iniciar um novo relatório de aula para um aluno, o sistema verifica se há relatório em aberto (`RASCUNHO`/`PENDENTE_REVISAO`) do mesmo professor para o mesmo aluno, **em qualquer canal**, e oferece **continuar** ou **descartar (cancelar) e começar novo**. Se houver pendências de aula e semestral, a de aula tem precedência. A resposta informa o canal de origem ("iniciado pelo Telegram"/"pelo portal web") e a data da última alteração.
  - No Telegram, a verificação é **por professor** (qualquer aluno), pois a mensagem chega sem contexto: se existe relatório de aula em aberto, a próxima mensagem é roteada para ele (RN-30).

### 5.5 Relatório semestral (somente Web no escopo atual)
- **RN-26 — Semestres disponíveis (menu pré-definido, sem digitação de datas):** do semestre em que o aluno foi **cadastrado** até o semestre **corrente**, inclusive. 1º semestre = jan–jun; 2º = jul–dez. Chave `AAAA-S`, rótulo "1º semestre 2026"/"2º semestre 2026". A UI pré-seleciona o último (mais recente).
- **RN-27 — Contagem antes de gerar:** conta os relatórios de aula **APROVADOS** do aluno cujas aulas caem no período (datas inclusive). Relatórios não aprovados não entram (podem mudar de conteúdo). A contagem considera relatórios de **todos os professores** do aluno. A UI exibe "N relatório(s) de aula foi(ram) considerado(s) nesta consolidação".
- **RN-28 — Período sem dados:** se a contagem for zero, **nada é criado** (nem relatório, nem PDF, nem chamada à IA) → `SEM_DADOS_NO_PERIODO` (409, portal) / `PERIODO_SEM_RELATORIOS` (422, defesa em profundidade no caso de uso). Mensagem na UI: "Nenhum relatório de aula aprovado neste período. Registre as aulas do semestre antes de gerar o relatório."
- **RN-29 — Consolidação:** os relatórios de aula aprovados do período são enviados ao LLM (um JSON por linha) com o modelo de referência da escola; a IA deve se basear **exclusivamente** nesses relatórios, sem inventar. O resultado nasce em `PENDENTE_REVISAO`, versão 1, com PDF. Mesmo ciclo de alteração (versão+1, novo PDF), aprovação com versão confirmada e cancelamento das RN-22/23/24.

### 5.6 Telegram (roteamento)
- **RN-30 — Estado da conversa vive no backend:** cada mensagem do Telegram é processada isoladamente; o orquestrador não guarda memória. A cada mensagem, o estado é derivado de: (1) existe vínculo? (2) existe relatório de aula em aberto do professor? (3) ele tem perguntas pendentes? (4) há aluno selecionado? (detalhes na seção 11).
- **RN-31 — Seleção de aluno por nome:** o professor digita o nome; o sistema procura, entre os alunos associados a ele, um nome **igual (ignorando maiúsculas/minúsculas, com trim)**. Se encontrado, grava como aluno selecionado para o próximo áudio; senão, lista os alunos disponíveis.
- **RN-32 — Palavras-chave:** com relatório em aberto, `cancelar` cancela; sem perguntas pendentes, `aprovar` aprova a versão vigente; qualquer outro texto é resposta (se há perguntas) ou instrução de alteração (se não há).

### 5.7 Histórico
- **RN-33 — Histórico contém apenas APROVADOS:** relatórios de aula e semestrais aprovados do aluno, **de todos os canais e de todos os professores**, ordenados cronologicamente (aula pela data da aula; semestral pelo início do período).
- **RN-34 — Escopo de acesso ao histórico:** administrador vê o histórico de **qualquer** aluno; professor vê apenas o de alunos **associados** a ele (403 caso contrário).

### 5.8 Segurança de login e dados
- **RN-35 — Rate limit do login do portal:** após **5** códigos incorretos consecutivos da mesma origem (hash do IP), novas tentativas são recusadas por **15 minutos** (configuráveis), **inclusive com o código correto**, → `BLOQUEADO_TEMPORARIAMENTE` (429) com `retryAfterSeconds` e header `Retry-After`. O bloqueio é verificado **antes** de validar o código. Sucesso zera o contador. Bloqueio vencido zera a contagem (evita rebloqueio imediato). O estado é persistido (sobrevive a reinício). A contabilização da falha precisa sobreviver ao rollback da transação de autenticação.
- **RN-36 — PDFs só para usuários autenticados:** download exige token (admin ou professor). O nome do arquivo é validado por padrão estrito (`relatorio-(aula|semestral)-<uuid>-v<n>.pdf`) para impedir path traversal.
- **RN-37 — Erros sem detalhes internos:** respostas de erro nunca expõem stack trace; formato padrão `{codigo, mensagem}`.

---

## 6. Máquinas de estado

### 6.1 Relatório de Aula

```text
             (áudio transcrito com sucesso)
                        │
                        ▼
                  ┌───────────┐  IA retornou perguntasPendentes ≠ []
                  │ RASCUNHO  │◄─────────────────────────────┐
                  └─────┬─────┘                              │
   IA sem perguntas     │  professor responde (qualquer      │
   (PDF gerado)         │  resposta resolve todas; PDF gerado)
                        ▼                                    │
               ┌──────────────────┐   alteração em linguagem │
               │ PENDENTE_REVISAO │──natural: versão+1, novo PDF
               └───┬─────────┬────┘   (permanece PENDENTE_REVISAO)
   aprovar         │         │  cancelar
  (versão vigente) ▼         ▼
            ┌──────────┐  ┌───────────┐
            │ APROVADO │  │ CANCELADO │ ◄── cancelar também a partir de RASCUNHO
            └──────────┘  └───────────┘
              (terminal)     (terminal)
```

Transições proibidas: aprovar fora de `PENDENTE_REVISAO`; aprovar com versão desatualizada; cancelar `APROVADO` ou `CANCELADO`.

### 6.2 Relatório Semestral

```text
 (≥1 relatório de aula aprovado no período; IA consolida; PDF gerado)
                        │
                        ▼
               ┌──────────────────┐  alteração: versão+1, novo PDF
               │ PENDENTE_REVISAO │◄──────────┐
               └───┬─────────┬────┘───────────┘
        aprovar    │         │ cancelar
                   ▼         ▼
            ┌──────────┐  ┌───────────┐
            │ APROVADO │  │ CANCELADO │
            └──────────┘  └───────────┘
```

### 6.3 Estado derivado da conversa no Telegram

| Estado | Condição (consultada no backend a cada mensagem) | Interpretação da próxima mensagem |
|---|---|---|
| NÃO_VINCULADO | não existe vínculo para o `telegramUserId` | qualquer texto é tentativa de código de vinculação |
| VINCULADO_SEM_ALUNO | vinculado, sem relatório em aberto, sem aluno selecionado | texto = nome de aluno; áudio = erro |
| ALUNO_SELECIONADO | vinculado, sem relatório em aberto, com aluno selecionado | texto = trocar aluno; nota de voz = registrar aula |
| AGUARDANDO_RESPOSTA | relatório de aula em aberto com perguntas pendentes | `cancelar` cancela; outro texto = resposta |
| AGUARDANDO_DECISÃO | relatório em `PENDENTE_REVISAO` sem perguntas | `aprovar` aprova; `cancelar` cancela; outro texto = alteração |

Após aprovar/cancelar, volta para ALUNO_SELECIONADO (a seleção do aluno **não** é limpa no MVP).

---

## 7. Fluxos ponta a ponta

### 7.1 Onboarding (secretaria)
1. Secretaria faz login (e-mail/senha).
2. Cadastra o professor (nome, e-mail) → sistema exibe **código de vinculação** + "Válido até ...".
3. Cadastra alunos (nome, nascimento, CPF, responsável se menor, professores associados).
4. Repassa o código ao professor (fora do sistema).
5. Se o código expirar ou for consumido, secretaria lista professores e clica "Gerar novo código" (a lista mostra se o professor já vinculou o Telegram).

### 7.2 Vinculação do Telegram
```text
Professor ──(qualquer mensagem)──► Bot
Bot ──► Backend: professor vinculado a este telegramUserId?
  ├─ sim → segue para o roteamento (7.3)
  └─ não → Backend: vincular(telegramUserId, texto da mensagem como código)
             ├─ ok  → "Conta vinculada com sucesso, <nome>! Para registrar uma aula, envie o nome do aluno."
             └─ erro (inválido/expirado/conflito/vazio) →
                    "Ola! Para usar o bot, envie o codigo de vinculacao que a secretaria da escola te passou."
```

### 7.3 Registro de aula pelo Telegram
```text
[mensagem de professor vinculado]
Backend: há relatório de aula em aberto para este professor?
 ├─ SIM (rascunho)
 │   ├─ texto == "cancelar" → cancela → "Relatorio cancelado. Voce pode comecar um novo quando quiser (envie o nome do aluno)."
 │   └─ consulta relatório atual
 │       ├─ tem perguntas pendentes → texto = resposta → responder-pergunta → baixa PDF → envia PDF com legenda
 │       └─ sem perguntas
 │           ├─ texto == "aprovar" → aprovar(versão vigente) → "Relatorio aprovado e salvo no historico do aluno."
 │           └─ outro texto → revisar(instrução) → baixa PDF → envia PDF com legenda
 └─ NÃO
     ├─ mensagem é nota de voz → baixa arquivo do Telegram → envia ao backend (resolve professor + aluno selecionado,
     │     registra aula, transcreve, estrutura)
     │     ├─ falhou → "Nao consegui processar esse audio (corrompido, formato invalido ou sem fala). Envie novamente, por favor."
     │     ├─ perguntas pendentes → envia a 1ª pergunta (texto gerado pela IA)
     │     └─ sem perguntas → baixa PDF → envia PDF com legenda
     └─ texto → selecionar aluno por nome
           ├─ encontrado → "Aluno <nome> selecionado! Envie o audio da aula quando quiser."
           └─ não encontrado → "Nao encontrei esse aluno. Alunos disponiveis:\n- <nome>..."
                               (ou "Voce ainda nao tem alunos cadastrados. Peca ao administrador para cadastrar um aluno vinculado a voce.")
```
Legenda do PDF: `Relatorio gerado. Responda 'aprovar' para confirmar ou descreva a alteracao desejada (ex.: 'mude a dificuldade para ritmo').`

### 7.4 Registro de aula pelo Portal Web
1. Professor entra com o código (7.8) → Painel → Alunos → "Novo relatório de aula".
2. Sistema verifica **rascunho pendente** do aluno (RN-25). Se houver relatório de aula em aberto → tela "Você tem um relatório em aberto" (7.6).
3. Professor **grava pelo navegador** (botão gravar/parar) **ou envia arquivo de áudio**. Se o navegador não suporta gravação ou o microfone está bloqueado, cai para modo somente-upload com aviso.
4. "Enviar áudio e gerar relatório" → upload multipart. [MVP] O servidor transcreve e estrutura **dentro da requisição** e responde `202` com `{relatorioId, status, perguntasPendentes}`.
5. Cliente acompanha por **polling a cada 2s** o estado do relatório até: perguntas pendentes, ou status `PENDENTE_REVISAO`/`APROVADO`/`CANCELADO`, ou erro.
6. Se houver perguntas → exibe **todas** as perguntas + um campo único "Sua resposta" → responder (7.5).
7. Tela de conferência: "Confira o relatório antes de aprovar" / "Versão N" / visualizador do PDF / campo de alteração / botões **Aprovar relatório**, **Solicitar alteração**, **Cancelar relatório**.
8. Aprovado → "Relatório aprovado — O relatório desta aula foi aprovado e já consta no histórico do aluno." com atalhos para histórico/alunos.

Mensagens de etapa: "Enviando o áudio..." → "Transcrevendo o áudio e estruturando o relatório..." → "Faltam algumas informações para concluir o relatório." / "Relatório pronto para conferência." / "Não foi possível processar o áudio."

### 7.5 Perguntas pendentes, alteração, aprovação, cancelamento (comum aos canais)
- **Responder:** texto → anexado às observações; pendências zeradas; `PENDENTE_REVISAO`; PDF gerado (RN-21).
- **Alterar:** instrução → LLM → versão+1 → novo PDF (RN-22).
- **Aprovar:** envia a versão que o professor está vendo; 409 se desatualizada (RN-23).
- **Cancelar:** status terminal (RN-24).

### 7.6 Retomada de rascunho (Web)
Tela: "Você tem um relatório em aberto — Este aluno já tem um relatório de aula iniciado {pelo Telegram | pelo portal web} e ainda não concluído (última alteração em dd/mm/aaaa hh:mm)."
- **Continuar de onde parei** → carrega o relatório → perguntas pendentes ou conferência do PDF.
- **Descartar e começar um novo** → cancela o relatório → tela de novo relatório.

### 7.7 Relatório semestral (Web)
1. Alunos → "Relatório semestral" → lista de semestres disponíveis (pílulas), último pré-selecionado.
2. "Gerar relatório semestral" → servidor conta; se zero → erro RN-28; senão consolida via IA, gera PDF, marca canal WEB e responde `{quantidadeRelatoriosEncontrados, relatorioSemestralId, status, pdfUrl, versao}` (síncrono).
3. Exibe "N relatórios de aula foram considerados nesta consolidação." + conferência (mesmo componente do relatório de aula).
4. Alterar / Aprovar / Cancelar como 7.5. Aprovado → "Relatório semestral aprovado — ... já consta no histórico do aluno."

### 7.8 Login do professor no portal
Código de vinculação (trim) → valida rate limit → valida código/expiração/ativo → renova validade do código (+7 dias) → emite sessão de 12h deslizante → Painel.
Mensagens: código inválido (RN-08); bloqueio: "Muitas tentativas incorretas. Tente novamente em {N minuto(s)}." (ou "Aguarde alguns minutos antes de tentar de novo." sem `retryAfterSeconds`); rede: "Não foi possível entrar agora. Verifique sua conexão e tente novamente."

### 7.9 Histórico
- **Secretaria:** escolhe qualquer aluno → lista de relatórios aprovados → abre PDF.
- **Professor (Web):** Alunos → "Histórico" → tabela (Relatório | Aprovado em | PDF) com "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)".
- **Telegram:** não consulta histórico no escopo atual.

---

## 8. Pipeline de IA

### 8.1 Visão geral
```text
Áudio (Telegram: Ogg/Opus | Web: WebM/Opus ou arquivo enviado)
   │  (em memória, nunca persistido)
   ▼
Speech-to-Text  ──► transcrição (texto, pt)  ──► persistida no RelatorioAula
   ▼
LLM: ESTRUTURAÇÃO ──► JSON do relatório de aula (+ perguntasPendentes)
   ▼ (validação de JSON; 1 retry de correção)
Backend aplica campos → gera PDF se não houver pendências

Alteração:  JSON atual + instrução ──► LLM: REVISÃO ──► JSON novo
Semestral:  N JSONs de relatórios aprovados ──► LLM: CONSOLIDAÇÃO ──► JSON semestral
Alteração semestral: JSON atual + instrução ──► LLM: REVISÃO SEMESTRAL ──► JSON novo
```
**Quem chama a IA:** no MVP final, **somente o backend** chama STT e LLM (por portas/gateways substituíveis). O orquestrador do Telegram apenas baixa o arquivo do Telegram e repassa ao backend. (O documento original de arquitetura previa o n8n chamando a IA; isso foi abandonado para manter regra e validação no backend.)

### 8.2 Speech-to-Text [MVP]
- Contrato compatível com a API de transcrição da OpenAI (MVP usa Groq Whisper `whisper-large-v3-turbo`): multipart com `file` (nome `audio.<ext>`, a extensão identifica o contêiner), `model`, `response_format=json`, `language=pt`. Resposta `{ "text": "..." }`.
- Formatos: Web grava `audio/webm` (Opus) e aceita qualquer `audio/*` enviado; Telegram envia nota de voz Ogg/Opus — a extensão real `oga` **não** é aceita pelo provedor, por isso o backend força `ogg`. Formato derivado da extensão do arquivo, senão do content-type, senão default (`webm` web / `ogg` interno).
- Limite de upload: 50 MB (backend e proxy). Telegram limita download de arquivos por bot a 20 MB.
- Erros → "áudio não processável": áudio vazio; HTTP de erro do provedor (401, 413, 429...); falha de rede; transcrição vazia.
- Sem retry e sem timeout explícito no MVP.

### 8.3 LLM [MVP]
- Contrato chat completions (OpenAI-compatível; MVP usa Groq `llama-3.3-70b-versatile`).
- `temperature: 0.2` (tarefa é extrair/reorganizar, não criar), mensagem única `role=user` com o prompt, **modo JSON nativo** (`response_format: json_object`).
- Validação: a resposta precisa ser JSON sintaticamente válido; se não for, **uma** chamada extra com o prompt de correção; se ainda inválida → erro (409 `CONFLITO` no MVP).
- Leitura do conteúdo em `choices[0].message.content` (fallbacks para envelopes alternativos).
- Campos ausentes no JSON viram defaults vazios (listas vazias, strings vazias, nulos nas seções semestrais); campos desconhecidos são ignorados.

### 8.4 Prompts (verbatim do MVP)

> Os prompts estão sem acentuação no código original. No definitivo, recomenda-se revisá-los (com acentos, instruções de estilo, contexto do aluno) e versioná-los — ver seção 17.

**PROMPT_ESTRUTURACAO** (relatório de aula)
```text
Voce e um assistente que estrutura relatorios de aulas de musica a partir da transcricao de um professor. Retorne APENAS um JSON valido (sem markdown, sem comentarios) com exatamente os campos:
{
  "conteudosTrabalhados": ["..."],
  "evolucao": "...",
  "dificuldades": ["..."],
  "atividadesPropostas": ["..."],
  "observacoes": "...",
  "perguntasPendentes": ["..."]
}
Preencha "perguntasPendentes" apenas com perguntas objetivas de acompanhamento quando uma informacao relevante nao puder ser extraida com confianca da transcricao (ex.: tarefa de casa nao mencionada). Se toda a informacao necessaria estiver presente, retorne "perguntasPendentes": [].

Transcricao da aula:
%s
```

**PROMPT_REVISAO** (alteração do relatório de aula)
```text
Voce recebeu um relatorio de aula de musica ja estruturado em JSON e uma instrucao de alteracao em linguagem natural do professor. Aplique a instrucao e retorne APENAS o JSON atualizado, com os mesmos campos do relatorio original (conteudosTrabalhados, evolucao, dificuldades, atividadesPropostas, observacoes, perguntasPendentes), sem markdown e sem comentarios.

Relatorio atual (JSON):
%s

Instrucao de alteracao:
%s
```

**PROMPT_CONSOLIDACAO_SEMESTRAL**
```text
Voce e um assistente pedagogico de uma escola de musica. Consolide a lista de relatorios de aula (em JSON) informada abaixo em um UNICO relatorio semestral, seguindo o modelo de referencia da escola. Retorne APENAS um JSON valido (sem markdown, sem comentarios) com exatamente os campos:
{
  "informacoesGerais": { "nome": "...", "idade": "...", "nivel": "...", "professor": "...", "periodo": "..." },
  "frequenciaEEstudo": { "frequenciaAulas": "...", "regularidadeEstudoEmCasa": "...", "engajamento": "...", "comentarioGeral": "..." },
  "tecnica": { "conteudosTrabalhados": ["..."], "evolucaoTecnica": "...", "pontosADesenvolver": ["..."], "estudosRealizados": ["..."] },
  "musicalidade": { "repertorioEstudado": ["..."], "som": "...", "ritmo": "...", "fraseado": "...", "articulacao": "...", "estilo": "...", "comentarioMusical": "..." },
  "leituraEMemorizacao": { "avaliacao": "...", "comentario": "..." },
  "pontosDeAtencao": { "itens": ["..."] },
  "estrategiasPedagogicas": { "itens": ["..."] },
  "acompanhamentoFamiliar": { "nivelDePresenca": "...", "observacaoDoProfessor": "..." },
  "planejamentoProximoSemestre": { "metasTecnicas": ["..."], "metasMusicais": ["..."], "habitosDeEstudo": ["..."], "repertorioProposto": ["..."], "estudosTecnicos": ["..."], "leituraETeoria": ["..."], "projetoDoSemestre": "..." },
  "parecerFinal": "..."
}
Baseie-se exclusivamente nas informacoes contidas nos relatorios de aula fornecidos; nao invente dados que nao possam ser inferidos deles.

Relatorios de aula do periodo (um JSON por linha):
%s
```
Entrada `%s`: um JSON por linha, cada um com `{conteudosTrabalhados, evolucao, dificuldades, atividadesPropostas, observacoes, perguntasPendentes: []}`. **Atenção:** o MVP **não** envia nome/idade do aluno, nome do professor, datas das aulas nem o período — mas o schema pede `informacoesGerais.nome/idade/professor/periodo` e `frequenciaAulas`. Ver seção 17.

**PROMPT_REVISAO_SEMESTRAL**
```text
Voce recebeu um relatorio semestral de musica ja consolidado em JSON e uma instrucao de alteracao em linguagem natural do professor. Aplique a instrucao e retorne APENAS o JSON atualizado, com os mesmos campos do relatorio original (informacoesGerais, frequenciaEEstudo, tecnica, musicalidade, leituraEMemorizacao, pontosDeAtencao, estrategiasPedagogicas, acompanhamentoFamiliar, planejamentoProximoSemestre, parecerFinal), sem markdown e sem comentarios.

Relatorio semestral atual (JSON):
%s

Instrucao de alteracao:
%s
```

**PROMPT_CORRIGIR_JSON**
```text
A resposta abaixo deveria ser um JSON valido, mas nao pode ser interpretada. Corrija e retorne APENAS o JSON valido, sem markdown e sem comentarios:

%s
```

### 8.5 Modelo de referência do relatório semestral (seções e rótulos)
1. **Informações Gerais** — nome, idade, nível, professor, período.
2. **Frequência e Estudo** — frequência nas aulas, regularidade do estudo em casa, engajamento, comentário geral.
3. **Técnica** — conteúdos trabalhados, evolução técnica, pontos a desenvolver, estudos realizados.
4. **Musicalidade** — repertório estudado, som, ritmo, fraseado, articulação, estilo, comentário musical.
5. **Leitura e Memorização** — avaliação, comentário.
6. **Pontos de Atenção** — itens.
7. **Estratégias Pedagógicas** — itens.
8. **Acompanhamento Familiar** — nível de presença, observação do professor.
9. **Planejamento do Próximo Semestre** — metas técnicas, metas musicais, hábitos de estudo, repertório proposto, estudos técnicos, leitura e teoria, projeto do semestre.
10. **Parecer Final** — texto livre.

---

## 9. Geração de PDF

- **Quando:** relatório de aula ao chegar em `PENDENTE_REVISAO` (estruturação sem pendências, resposta a pergunta, alteração); relatório semestral ao gerar e a cada alteração.
- **Um arquivo por versão:** `relatorio-aula-<id>-v<versao>.pdf` / `relatorio-semestral-<id>-v<versao>.pdf`. O relatório guarda o `pdfUrl` da versão vigente como **rota HTTP** servida pelo backend (MVP: `/api/v1/arquivos/relatorios/<arquivo>`) — nunca um caminho de filesystem (bug corrigido no MVP).
- **Conteúdo do PDF de aula [MVP]:** título "Relatório de Aula"; cabeçalho Aluno / Professor / Data da aula (dd/MM/aaaa); seções Conteúdos Trabalhados (lista), Evolução, Dificuldades (lista), Atividades Propostas (lista), Observações; "-" para vazios.
- **Conteúdo do PDF semestral [MVP]:** nome da escola (configurável) no topo; título "Relatório Semestral"; Aluno / Professor / Período (rótulo + datas) / Relatórios de aula considerados: N; as 9 seções estruturadas (cada subcampo como linha "chave: valor"; listas unidas por "; ") + Parecer Final; rodapé "<Escola> - documento gerado automaticamente a partir dos relatorios de aula do periodo."
- **No MVP o PDF não tem identidade visual** (fonte Helvetica, sem logo, sem acentos, chaves JSON cruas como rótulos). No definitivo, o PDF é um **documento oficial entregue às famílias** → deve seguir a nova identidade (seção 15), com rótulos legíveis.
- **Entrega:** Web busca o PDF com o token (fetch autenticado → blob URL → iframe/nova aba; se pop-up bloqueado, baixa como arquivo). Telegram: o orquestrador baixa os bytes do PDF vigente por rota interna e envia como documento.
- **Storage:** volume persistente dedicado apenas a PDFs (não há volume de áudio).

---

## 10. Contratos de API do MVP (referência)

> Referência para o agente entender as capacidades. O definitivo pode redesenhar rotas/formatos, mas deve cobrir as mesmas operações e regras.

### 10.1 Formato de erro padrão
`{ "codigo": "<CODIGO_ESTAVEL>", "mensagem": "<texto>" }` (429 inclui `retryAfterSeconds`).

| Código | HTTP | Quando |
|---|---|---|
| `DADOS_INVALIDOS` | 400 | validação de payload, corpo malformado, argumento inválido (ex.: `periodoChave` inválida, responsável ausente para menor) |
| `NAO_AUTENTICADO` | 401 | token de serviço ausente/inválido (rotas internas) |
| (401 sem corpo) | 401 | token ausente/expirado nas rotas web |
| `CREDENCIAIS_INVALIDAS` | 401 | login admin: "E-mail ou senha invalidos." |
| `CODIGO_INVALIDO` | 401 | login portal |
| `ALUNO_NAO_ASSOCIADO` | 403 | aluno não associado ao professor |
| `RECURSO_NAO_ENCONTRADO` | 404 | recurso inexistente ou relatório de outro professor |
| `CONFLITO` | 409 | transição de status inválida, versão desatualizada, falha de LLM (no MVP) |
| `PROFESSOR_EMAIL_DUPLICADO`, `ALUNO_CPF_DUPLICADO`, `TELEGRAM_JA_VINCULADO`, `PROFESSOR_JA_VINCULADO`, `SEM_DADOS_NO_PERIODO` | 409 | conflitos de domínio |
| `AUDIO_MUITO_GRANDE` | 413 | upload acima do limite |
| `AUDIO_NAO_PROCESSAVEL`, `PERIODO_SEM_RELATORIOS` | 422 | áudio não transcrito; semestral sem dados |
| `BLOQUEADO_TEMPORARIAMENTE` | 429 | rate limit do login do portal |
| `ERRO_INTERNO` | 500 | "Ocorreu um erro inesperado. Tente novamente mais tarde." |

### 10.2 Área administrativa (token de administrador)
| Método | Rota | Entrada | Saída |
|---|---|---|---|
| POST | `/api/v1/auth/login` (pública) | `{email, senha}` | `{token, expiraEm}` |
| POST | `/api/v1/professores` | `{nome, email}` | 201 `{id, nome, email, codigoVinculacao, codigoVinculacaoExpiraEm}` |
| GET | `/api/v1/professores` | — | `[{id, nome, email, vinculadoTelegram}]` |
| POST | `/api/v1/professores/{id}/codigo-vinculacao` | — | `{id, nome, email, codigoVinculacao, codigoVinculacaoExpiraEm}` |
| POST | `/api/v1/alunos` | `{nome, dataNascimento (AAAA-MM-DD), cpf, nomeResponsavel?, professorIds[]}` | 201 `{id, nome, dataNascimento, nomeResponsavel, menorDeIdade}` |
| GET | `/api/v1/alunos[?professorId=]` | — | lista de alunos |
| GET | `/api/v1/alunos/{id}/historico` | — | `{aluno:{id,nome}, relatorios:[ItemHistorico]}` |

`ItemHistorico = {tipo: "AULA"|"SEMESTRAL", id, dataAula, periodoInicio, periodoFim, status, pdfUrl, aprovadoEm}`

### 10.3 Portal do professor (token de professor, sessão deslizante)
| Método | Rota | Entrada | Saída |
|---|---|---|---|
| POST | `/api/v1/professor/auth/vincular` (pública, rate limit) | `{codigoVinculacao}` (≤64) | `{token, expiraEm, professor:{id, nome}}` |
| GET | `/api/v1/professor/me` | — | `{id, nome, sessaoExpiraEm}` |
| GET | `/api/v1/professor/alunos` | — | `[{id, nome}]` |
| GET | `/api/v1/professor/alunos/{alunoId}/rascunho-pendente` | — | `{existeRascunho, tipo, relatorioId, status, canalOrigem, atualizadoEm}` |
| GET | `/api/v1/professor/alunos/{alunoId}/historico` | — | igual ao admin |
| POST | `/api/v1/professor/alunos/{alunoId}/relatorios-aula/audio` | multipart `audio`, `dataAula?` | 202 `{relatorioId, status, perguntasPendentes[]}` |
| GET | `/api/v1/professor/relatorios-aula/{id}` | — | `RelatorioAulaDto` |
| POST | `/api/v1/professor/relatorios-aula/{id}/responder-pergunta` | `{resposta}` | `RelatorioAulaDto` |
| POST | `/api/v1/professor/relatorios-aula/{id}/revisar` | `{instrucao}` | `RelatorioAulaDto` |
| POST | `/api/v1/professor/relatorios-aula/{id}/aprovar` | `{versaoConfirmada}` | `{relatorioId, status, aprovadoEm}` |
| POST | `/api/v1/professor/relatorios-aula/{id}/cancelar` | — | 204 |
| GET | `/api/v1/professor/alunos/{alunoId}/semestres-disponiveis` | — | `[{chave, rotulo, inicio, fim}]` |
| POST | `/api/v1/professor/alunos/{alunoId}/relatorios-semestrais` | `{semestre}` (chave) | `{quantidadeRelatoriosEncontrados, relatorioSemestralId, status, pdfUrl, versao}` |
| GET | `/api/v1/professor/relatorios-semestrais/{id}` | — | `RelatorioSemestralDto` |
| POST | `/api/v1/professor/relatorios-semestrais/{id}/revisar` | `{instrucao}` | `RelatorioSemestralDto` |
| POST | `/api/v1/professor/relatorios-semestrais/{id}/aprovar` | `{versaoConfirmada}` | `{relatorioId, status, aprovadoEm}` |
| POST | `/api/v1/professor/relatorios-semestrais/{id}/cancelar` | — | 204 |

`RelatorioAulaDto = {relatorioId, status, conteudosTrabalhados[], evolucao, dificuldades[], atividadesPropostas[], observacoes, perguntasPendentes[], pdfUrl, versao}`
`RelatorioSemestralDto = {relatorioId, status, quantidadeRelatoriosAulaConsiderados, informacoesGerais, frequenciaEEstudo, tecnica, musicalidade, leituraEMemorizacao, pontosDeAtencao, estrategiasPedagogicas, acompanhamentoFamiliar, planejamentoProximoSemestre, parecerFinal, pdfUrl, versao}`

### 10.4 Arquivos (token de admin **ou** professor)
`GET /api/v1/arquivos/relatorios/{nomeArquivo}` → `application/pdf`, `Content-Disposition: inline`.

### 10.5 API interna do orquestrador do Telegram (token de serviço no header `X-N8N-Service-Token`; não exposta publicamente)
**Em uso pelo fluxo atual do Telegram:**
| Método | Rota | Uso |
|---|---|---|
| GET | `/internal/v1/telegram/{telegramUserId}/professor` | está vinculado? → `{professorId, nomeProfessor}` ou 404 |
| POST | `/internal/v1/telegram/vinculacao` | `{telegramUserId, codigoVinculacao}` → `{professorId, nomeProfessor}` |
| GET | `/internal/v1/telegram/{telegramUserId}/rascunho-pendente` | relatório de aula em aberto do professor (qualquer aluno) |
| POST | `/internal/v1/telegram/{telegramUserId}/alunos/selecionar` | `{nomeAluno}` → `{encontrado, alunoId, nomeAluno}` |
| POST | `/internal/v1/telegram/{telegramUserId}/relatorios-aula/audio` | multipart `audio` → registra + transcreve + estrutura → `RelatorioAulaDto` |
| GET | `/internal/v1/professores/{professorId}/alunos` | menu de alunos |
| GET | `/internal/v1/relatorios-aula/{id}` | estado atual |
| GET | `/internal/v1/relatorios-aula/{id}/pdf` | bytes do PDF vigente |
| POST | `/internal/v1/relatorios-aula/{id}/responder-pergunta` \| `/revisar` \| `/aprovar` \| `/cancelar` | ciclo do relatório |

**Legado (ainda expostas, usadas pelos workflows antigos):** `POST /internal/v1/relatorios-aula/transcrever` (multipart `alunoId, professorId, dataAula, audio` → `{aulaId, transcricao}`), `POST /internal/v1/relatorios-aula/{aulaId}/estruturar`, `GET /internal/v1/alunos/{id}/semestres-disponiveis`, `POST /internal/v1/relatorios-semestrais/contagem` (`{alunoId, periodoChave}` → `{quantidadeRelatoriosAula}`), `POST /internal/v1/relatorios-semestrais` (`{alunoId, professorId, periodoChave}`, 201), `POST /internal/v1/relatorios-semestrais/{id}/revisar|aprovar`, `GET /internal/v1/professores/{pid}/alunos/{aid}/historico`.

### 10.6 Operacional
Health check público; documentação OpenAPI pública; todo o resto negado por padrão.

---

## 11. Canal Telegram (escopo: registro de aula)

### 11.1 Arquitetura do canal
- O **webhook do Telegram vai para o orquestrador** (MVP: n8n), não para o backend. O Telegram aceita **um único webhook por bot** ⇒ deve haver **um único fluxo de entrada** para o bot.
- Cada mensagem é uma **execução isolada, sem memória**; todo o estado vem do backend (RN-30). O orquestrador **nunca** grava no banco, **nunca** decide regra de negócio e **nunca** chama a IA; apenas: receber update → consultar backend → baixar arquivo do Telegram → repassar ao backend → enviar mensagem/documento.
- Updates tratados: apenas `message` (texto e **nota de voz** `message.voice`). Mensagens do tipo áudio-arquivo, documento, vídeo, video_note, foto e sticker **não** são tratadas como áudio de aula no MVP.
- Não há teclados inline nem `callback_data`; não há tratamento de `/start` ou `/help` (no MVP, `/start` de um usuário não vinculado é tratado como tentativa de código e cai na mensagem de pedir código).
- chatId = `message.from.id` (conversas privadas).
- [Alternativa válida para o definitivo] o backend pode receber o webhook do Telegram diretamente (dispensando o orquestrador), desde que mantenha o canal como adaptador de entrada sem regra própria. **[DECISÃO PENDENTE]**.

### 11.2 Textos do bot (verbatim do MVP; revisar acentuação/tom no definitivo)
| Situação | Texto |
|---|---|
| Vinculação ok | `Conta vinculada com sucesso, {nomeProfessor}! Para registrar uma aula, envie o nome do aluno.` |
| Não vinculado / código inválido / conflito | `Ola! Para usar o bot, envie o codigo de vinculacao que a secretaria da escola te passou.` |
| Aluno selecionado | `Aluno {nomeAluno} selecionado! Envie o audio da aula quando quiser.` |
| Aluno não encontrado | `Nao encontrei esse aluno. Alunos disponiveis:` + `\n- {nome}` por aluno |
| Professor sem alunos | `Voce ainda nao tem alunos cadastrados. Peca ao administrador para cadastrar um aluno vinculado a voce.` |
| Falha no áudio (qualquer erro) | `Nao consegui processar esse audio (corrompido, formato invalido ou sem fala). Envie novamente, por favor.` |
| Pergunta pendente | texto da 1ª pergunta gerada pela IA |
| PDF enviado (legenda) | `Relatorio gerado. Responda 'aprovar' para confirmar ou descreva a alteracao desejada (ex.: 'mude a dificuldade para ritmo').` |
| Aprovado | `Relatorio aprovado e salvo no historico do aluno.` |
| Cancelado | `Relatorio cancelado. Voce pode comecar um novo quando quiser (envie o nome do aluno).` |

### 11.3 Comportamentos problemáticos observados no fluxo do Telegram (corrigir no definitivo)
1. Todo erro de áudio vira a mesma mensagem — inclusive "nenhum aluno selecionado" (404), aluno não associado (403), tamanho (413), falha do LLM (409).
2. Várias falhas não geram resposta nenhuma ao professor (aprovar com 409, revisar/responder com texto vazio, cancelar, PDF ausente, lista de alunos).
3. Nota de voz enviada enquanto há rascunho aberto é tratada como texto vazio (resposta/alteração) e falha silenciosamente.
4. Nome de aluno digitado durante `PENDENTE_REVISAO` é enviado à IA como instrução de alteração.
5. Só a 1ª pergunta pendente é enviada; uma resposta resolve todas; perguntas retornadas após uma revisão são ignoradas.
6. Se a estruturação pela IA falhar após a transcrição, fica um `RASCUNHO` sem campos estruturados; "aprovar" nele dá 409 sem resposta ao usuário.
7. A seleção do aluno nunca expira nem é limpa (próximo áudio vai para o mesmo aluno, sem confirmação).
8. Não há rate limit para tentativas de código no Telegram.
9. Seleção por nome exato (sem busca parcial, sem ignorar acentos, sem desambiguação de homônimos).

### 11.4 Workflows legados (apenas histórico)
Existiam workflows separados de vinculação, registro de aula, relatório semestral (`/relatorio_semestral`) e histórico (`/historico`) que usavam "gatilho no meio do fluxo" para esperar a próxima mensagem — padrão que não funciona no orquestrador. Foram substituídos por um fluxo único com estado no backend. Relatório semestral e histórico via Telegram **saíram do escopo** (decisão mantida para o definitivo). Textos legados, caso sejam úteis: "Encontrei {N} relatorio(s) de aula aprovados nesse periodo. Gerando o relatorio semestral consolidado...", "Nao ha relatorios de aula aprovados nesse periodo para esse aluno. Escolha outro semestre ou registre aulas antes de gerar o relatorio semestral.", "Este aluno nao esta associado a voce. Nao e possivel consultar o historico dele".

---

## 12. Portal Web do Professor e Área Administrativa (funcional)

> Somente comportamento. O layout/visual do MVP (tema escuro roxo, fonte Inter) **será descartado**; o novo design segue a seção 15.

### 12.1 Estrutura de navegação [MVP]
- **Área administrativa ("Secretaria"):** Login → Histórico (página inicial após login) · Professores (lista + reemitir código) · Cadastrar professor · Cadastrar aluno. Botão Sair.
- **Portal do professor:** Entrar (código) → Painel · Alunos (busca por nome) → por aluno: Novo relatório de aula · Relatório semestral · Histórico · (Retomar rascunho, automático). Busca global de aluno no cabeçalho. Botão Encerrar sessão.
- A raiz do site leva ao portal do professor; o login da secretaria tem link "É professor? Acesse o portal do professor." [DECISÃO PENDENTE: rotas/entrada no definitivo.]
- Uso esperado em **celular** (gravar a aula do telefone) ⇒ design mobile-first.

### 12.2 Telas da secretaria
| Tela | Campos / ações | Regras / mensagens |
|---|---|---|
| **Login** | E-mail, Senha, "Entrar" | erro da API ou "Nao foi possivel entrar. Tente novamente." |
| **Cadastrar professor** | Nome*, E-mail* → "Cadastrar" | Sucesso: "Professor cadastrado com sucesso" + **Código de vinculação** + "Válido até ..." + "Repasse este código ao professor para que ele vincule sua conta do Telegram pelo bot." |
| **Professores** | Tabela Nome · E-mail · Telegram (Vinculado/Não vinculado) · "Gerar novo código" | Mostra código novo + validade na linha; estados carregando/vazio ("Nenhum professor cadastrado ainda.")/erro. A lista **não** mostra o código atual. |
| **Cadastrar aluno** | Nome*, Data de nascimento* (máscara dd/mm/aaaa), CPF*, Nome do responsável (aparece e é obrigatório só se menor de 18), Professores (multi-seleção, opcional) | Erros: "Data de nascimento invalida. Use o formato dd/mm/aaaa.", "Nome do responsavel e obrigatorio para alunos menores de idade."; sucesso: "Aluno cadastrado com sucesso". |
| **Histórico** | Seleção de aluno (todos) → lista de relatórios aprovados → detalhe (tipo, status, aprovado em) + PDF | vazio: "Nenhum relatorio aprovado encontrado para este aluno." |

Não existem no MVP: listagem/edição/inativação de alunos, edição/inativação de professores, alteração de associações, gestão de administradores, desvinculação do Telegram.

### 12.3 Telas do portal do professor
| Tela | Conteúdo | Regras / mensagens |
|---|---|---|
| **Entrar** | "Informe o mesmo código de vinculação que você usa no Telegram para acessar seus alunos e relatórios." Campo código → "Entrar" | RN-08, RN-35; mensagens em 7.8 |
| **Painel** | "Olá, {nome}" · "Registre uma aula por áudio ou gere o relatório semestral de um aluno." · cartão com nº de alunos associados · lista dos 5 primeiros alunos | vazio: "Você ainda não tem alunos associados. Fale com a secretaria da escola." |
| **Alunos** | cards com nome e ações: Novo relatório de aula / Relatório semestral / Histórico; filtro `?busca=` (substring, sem ignorar acentos) | `Nenhum aluno encontrado para "{busca}".` |
| **Novo relatório de aula** | 7.4 | sem limite de duração/tamanho no cliente; sem prévia de áudio |
| **Retomar rascunho** | 7.6 | apenas rascunho de **aula** é tratado |
| **Relatório semestral** | 7.7 | "Nenhum semestre disponível para este aluno ainda." |
| **Histórico** | tabela "Relatório · Aprovado em · PDF" | "Este aluno ainda não tem relatórios aprovados."; datas `AAAA-MM-DD` formatadas sem conversão de fuso (evita mostrar o dia anterior) |

Componente de conferência (aula e semestral): título "Confira o relatório antes de aprovar", "Versão N", visualizador do PDF (ou "O PDF ainda está sendo gerado."), campo "Precisa ajustar algo? Descreva a alteração com suas palavras." (ex.: "o aluno teve mais facilidade em leitura do que consta nas dificuldades."), botões Aprovar / Solicitar alteração / Cancelar.

### 12.4 Comportamentos de cliente a corrigir
- Sem timeout nem tratamento de falha no polling; polling não reinicia após responder pergunta nem na retomada.
- Cancelar não pede confirmação e navega mesmo se a requisição falhou.
- 401 no meio da sessão não redireciona imediatamente para o login.
- Campos estruturados do relatório e o parecer final nunca são mostrados em texto (só o PDF).
- CPF sem máscara/validação de dígitos; data sem validação de calendário (31/02 passa).

---

## 13. Segurança, privacidade e LGPD

### 13.1 Princípios (mantidos da constituição do MVP)
- Dados de alunos, inclusive **menores de idade**: privacidade desde o design.
- Segredos (tokens do Telegram, chaves de IA, credenciais de banco, chave de criptografia do CPF, segredo de assinatura de tokens, token de serviço) **somente em variáveis de ambiente/secret manager**, nunca no repositório.
- Toda comunicação externa via **HTTPS**. Banco e orquestrador **não** expostos publicamente (em dev, só em loopback).
- Logs sem dados sensíveis desnecessários (áudio, transcrição completa, identificadores pessoais). IP nunca armazenado em claro (hash).
- Controle de acesso por professor (RN-18) centralizado — nenhum endpoint novo pode "esquecer" a checagem.
- Nenhum dado pessoal real no repositório (os áudios de teste em `assets/` são **fictícios**, produzidos pelo cliente).

### 13.2 Controles implementados no MVP
- CPF cifrado (AES-GCM) + hash HMAC para unicidade; CPF nunca retornado.
- Senha do administrador com BCrypt.
- Tokens assinados (HMAC); papel no token (`role=PROFESSOR`) impede cruzamento admin/professor.
- Sessão deslizante do professor; admin 2h.
- Rate limit persistido no login do portal.
- Áudio não retido.
- Validação estrita do nome do arquivo de PDF; PDFs só com autenticação.
- CORS com origem explícita (nunca `*`), expondo o header do token renovado.
- Cadeia de segurança "negar por padrão" para rotas não mapeadas.
- API interna isolada por token de serviço.

### 13.3 Pontos de LGPD a decidir com o cliente [DECISÃO PENDENTE]
Quais dados vão para provedores de IA (hoje: transcrição completa e relatórios; sem nome/CPF); política de retenção (transcrições, versões antigas de PDF, relatórios cancelados); quem pode excluir dados e como (direito de eliminação); consentimento do responsável para menores; tratamento de dados pelos fornecedores de IA (termos, região, não-treinamento); backups; auditoria de acesso.

---

## 14. Infraestrutura e operação (agnóstico de stack)

### 14.1 Componentes lógicos necessários
| Componente | Responsabilidade |
|---|---|
| Frontend web | Área administrativa + portal do professor (SPA no MVP, servida por proxy que encaminha `/api/`). |
| Backend (API) | **Único** ponto de regra de negócio, autorização, validação, escrita no banco, chamadas à IA e geração de PDF. |
| Banco relacional | Fonte oficial da verdade (inclui estado da conversa do Telegram e rate limit). Migrações versionadas. |
| Storage de arquivos | PDFs (um por versão). Sem áudio. |
| Orquestrador/adaptador do Telegram | Recebe webhook do Telegram, consulta o backend, repassa áudio, envia mensagens/documentos. |
| Provedores externos | Telegram Bot API, Speech-to-Text, LLM. |

### 14.2 Configurações (nomes do MVP e significado)
| Variável | Significado / default |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (ou `POSTGRES_*`) | conexão com o banco |
| `JWT_SECRET` | segredo de assinatura dos tokens (≥32 caracteres) |
| `CPF_ENCRYPTION_KEY` | chave de cifragem do CPF (Base64 de 16/24/32 bytes; texto é derivado por SHA-256 em dev) |
| `N8N_SERVICE_TOKEN` | segredo compartilhado da API interna |
| `TELEGRAM_BOT_TOKEN` | token do bot |
| `STT_API_KEY`, `STT_PROVIDER_URL`, `STT_MODEL`, `STT_LANGUAGE` | provedor de transcrição (default `whisper-large-v3-turbo`, `pt`) |
| `LLM_API_KEY`, `LLM_PROVIDER_URL`, `LLM_MODEL` | provedor de LLM (default `llama-3.3-70b-versatile`) |
| `WEB_SESSION_HOURS` | inatividade da sessão do professor (12) |
| `WEB_LOGIN_MAX_ATTEMPTS`, `WEB_LOGIN_LOCKOUT_MINUTES` | rate limit (5 / 15) |
| `CORS_ALLOWED_ORIGIN` | origens do frontend (lista separada por vírgula) |
| `PDF_STORAGE_PATH` | diretório de PDFs |
| `ESCOLA_NOME` | nome da escola impresso no PDF (default "Escola de Música" → no definitivo: "Instituto Musical Felipe Blaft") |
| `ESCOLA_TIMEZONE` | fuso da escola (`America/Sao_Paulo`) |
| Limite de upload | 50 MB (backend e proxy) |

### 14.3 Dados de desenvolvimento
- Seed: um administrador de teste e um professor de teste com código de vinculação válido por 7 dias (valores fictícios no seed do MVP). Alunos **não** podem ser inseridos por SQL (CPF precisa ser cifrado pela aplicação) — cadastrar via API.
- Arquivos de teste fictícios fornecidos pelo cliente (em `assets/` do MVP): `Aula Matteo Mafra_21_09_2026.ogg`, `Aula Olavo Torrente_24_09_2026.ogg`, `Aula Miguel de Castro Torrente_20_09_26.mp4`, `Aula Olavo de Castro Torrente_2026-09-20 at 09.57.15.mp4`. Úteis como fixtures de ponta a ponta (note que há `.mp4` — o definitivo deve decidir se aceita vídeo/áudio em contêiner mp4).

### 14.4 Qualidade (do MVP)
Testes unitários com cobertura mínima de **90% (linha e branch)** exigida pela constituição; testes de contrato para as APIs consumidas pelo orquestrador; integrações reais (IA, Telegram, banco) não contam para cobertura unitária. Uma branch por feature/spec a partir da `main` atualizada.

---

## 15. Identidade visual (base para o novo design)

Arquivos do cliente: `assets/Logomarca_0001.jpeg` (versão preta sobre branco) e `assets/Logomarca_0002.jpeg` (versão dourada metálica sobre fundo preto texturizado).

- **Nome da marca:** **INSTITUTO MUSICAL — FELIPE BLAFT**.
- **Monograma:** letras **"F" e "B" entrelaçadas**, em que as barras horizontais do "F" são **teclas de piano** (retângulos sólidos) e o "B" é desenhado em **traço fino** (outline) conectado às teclas — referência direta a teclado/piano.
- **Tipografia do logotipo:** **serifada clássica em caixa-alta** (estilo romano/Trajan). "INSTITUTO MUSICAL" com espaçamento entre letras amplo e peso leve; "FELIPE BLAFT" maior e mais pesado. Entre as linhas, **filete horizontal com ornamento de flor-de-lis** centralizado.
- **Paletas observadas:**
  - Monocromática: preto (#000 aprox.) sobre branco.
  - Premium: **dourado metálico** (tons ~#C9A96E / #B8955A / #8C6D3F) sobre **preto profundo** (~#0B0B0B / #151515).
- **Personalidade:** clássica, elegante, sofisticada, erudita (música de concerto/piano).
- **Diretrizes para o novo design [RECOMENDAÇÃO]:** preto + dourado como cores de marca, com um neutro claro (off-white/marfim) para legibilidade em telas longas; serifada para títulos e sans-serif legível para interface; monograma FB como ícone do app/favicon/avatar do bot do Telegram; contraste mínimo WCAG AA (dourado sobre preto precisa ser checado em textos pequenos); modo claro e escuro; mobile-first; **PDFs com a marca** (cabeçalho com logotipo, rodapé institucional, tipografia coerente) pois são entregues às famílias.
- Pedir ao cliente os **arquivos vetoriais** (SVG/PDF/AI) e os nomes/códigos oficiais das cores e fontes, se existirem. **[DECISÃO PENDENTE]**

---

## 16. Lições aprendidas no MVP (bugs já corrigidos — não repetir)

| # | Problema | Causa | Solução adotada |
|---|---|---|---|
| 1 | PDF abria a tela de login | `pdfUrl` guardava caminho de filesystem do container; o navegador resolvia contra o frontend e o fallback da SPA devolvia `index.html` | `pdfUrl` passou a ser rota HTTP servida pelo backend, com migração dos registros antigos |
| 2 | PDF não abria em `<iframe>`/`<a>` | esses elementos não enviam o header `Authorization` | fetch autenticado → blob URL |
| 3 | Data da aula um dia à frente | servidor em UTC; aula às 21h de Brasília já é o dia seguinte em UTC | "hoje" sempre calculado no fuso da escola; no front, datas `AAAA-MM-DD` formatadas sem conversão de fuso |
| 4 | Rate limit nunca bloqueava | a contagem de falhas entrava na transação da autenticação e era desfeita pelo rollback do erro "código inválido" | registrar a tentativa em transação própria/independente |
| 5 | Rebloqueio imediato após fim do bloqueio | contador continuava no limite | ao vencer o bloqueio, zerar a contagem |
| 6 | Polling do portal nunca terminava para relatório com perguntas | perguntas pendentes só existiam na resposta da chamada que as gerou | persistir `perguntasPendentes` no relatório; a consulta é a fonte da verdade |
| 7 | Bot não conseguia "esperar a próxima mensagem" | o gatilho do Telegram só inicia execuções; não há memória entre mensagens | todo estado da conversa no backend (vínculo + aluno selecionado + relatório em aberto) |
| 8 | Telegram só aceita 1 webhook por bot | vários workflows com gatilho próprio | fluxo de entrada único |
| 9 | Provedor de STT recusava nota de voz do Telegram | extensão `oga` não suportada, apesar de ser Ogg/Opus | enviar como `ogg` |
| 10 | Upload de alguns minutos de áudio falhava | limite padrão de 1 MB do servidor | 50 MB alinhado entre proxy e backend |
| 11 | Portal não lia o token renovado | headers de resposta cross-origin ficam ocultos | CORS expondo o header do token renovado |
| 12 | Login administrativo exigia token do orquestrador | filtro do token de serviço registrado globalmente | filtro aplicado só às rotas internas |
| 13 | Token de professor aceito como administrador | mesma chave de assinatura | papel no token + validação por papel em cada cadeia de segurança |
| 14 | Cadastro de aluno com professores dava 500 | ORM inseria `NULL` explícito e o DEFAULT do banco não era aplicado | preencher timestamps/defaults na aplicação |
| 15 | Professor tinha de pedir código novo a cada 24h para usar o portal | código de uso único e curto | código reutilizável no portal, validade 7 dias, renovada a cada login |
| 16 | Registros órfãos quando o áudio era inválido | persistia antes de transcrever | transcrever primeiro; persistir só em caso de sucesso |

---

## 17. Lacunas, riscos e recomendações para o definitivo [RECOMENDAÇÃO]

> Seção separada de propósito: são observações sobre o código do MVP, **não** comportamento a ser copiado. Levar para `/speckit.clarify` e para a constituição quando fizer sentido.

### 17.1 Segurança e controle de acesso (prioridade alta)
1. **PDF acessível por qualquer usuário autenticado:** a rota de download aceita qualquer token de professor e só valida o formato do nome do arquivo — um professor que conheça o nome (contém o UUID do relatório) baixa o PDF de aluno de outro professor. Recomenda-se autorizar o download pelo **relatório** (checar dono/associação) e/ou usar URLs assinadas de curta duração.
2. **API interna sem checagem de dono:** `responder-pergunta`, `revisar`, `aprovar`, `cancelar`, `consultar` e `pdf` internos agem por `relatorioId` sem verificar que o relatório pertence ao professor do `telegramUserId`. Recomenda-se que todas as rotas do canal Telegram sejam **escopadas pelo telegramUserId** e resolvam o professor no backend.
3. **Revisar/responder não verificam status:** um relatório `APROVADO` ou `CANCELADO` pode ser "revisado" (volta para `PENDENTE_REVISAO` e ganha nova versão) ou "respondido". Definir no domínio as transições permitidas (revisar/responder somente relatório em aberto).
4. **Vinculação do Telegram não verifica `ativo`** (o portal verifica) e não tem rate limit. Aplicar as mesmas regras nos dois canais.
5. **Sessão do professor sem revogação:** tokens stateless; encerrar sessão não invalida no servidor; reemitir código não derruba sessões existentes. Avaliar sessão revogável ou versão de credencial.
6. **Código de vinculação como "senha":** reutilizável por 7 dias renováveis, sem segundo fator. Avaliar login do professor por e-mail + link mágico/senha, ou manter o código com expiração, rate limit e revogação.
7. **Token de serviço estático** para a API interna: torná-lo rotacionável, comparar em tempo constante, manter em rede privada.
8. **Sem gestão de administradores** (só seed) e **sem trilha de auditoria** (quem cadastrou, quem aprovou, quem baixou PDF).

### 17.2 Regras de negócio a decidir [DECISÃO PENDENTE]
1. **Telegram × portal (RN-09):** vincular o Telegram consome o código e quebra o login no portal até a reemissão. Sugestão: separar "código de vinculação do Telegram" (uso único) da "credencial do portal".
2. **Consolidação semestral multiprofessor:** hoje o semestral de um aluno consolida relatórios **de todos os professores** dele, mas registra só o professor que gerou; o histórico de aluno compartilhado mostra relatórios de outros professores. Decidir: semestral por (aluno, professor) ou por aluno (visão da escola)? Quem pode ver o quê?
3. **Unicidade do semestral:** nada impede gerar vários semestrais para o mesmo aluno/período (inclusive vários aprovados). Definir regra (ex.: um aprovado por aluno/período/professor).
4. **Contexto enviado à IA no semestral:** o LLM não recebe nome/idade do aluno, professor, datas das aulas, período nem quantidade de aulas — mas o modelo pede essas informações (Informações Gerais, frequência). Preencher esses campos **deterministicamente no backend** e enviar ao LLM as datas das aulas; avaliar incluir "nível"/instrumento do aluno (dados que hoje não existem no cadastro).
5. **Perguntas pendentes:** hoje a resposta é só anexada às observações e resolve todas. Recomendado: reprocessar com a IA (incorporar a resposta nos campos certos), permitir responder pergunta a pergunta e limitar o número de rodadas.
6. **Seleção de aluno no Telegram:** nome exato. Recomendado: busca aproximada (ignorar acentos, primeiro nome), desambiguação de homônimos, confirmação do aluno antes de processar o áudio, expiração da seleção após aprovar/cancelar ou após N horas; considerar teclado inline com a lista de alunos.
7. **Formatos aceitos:** apenas nota de voz no Telegram; o cliente gravou exemplos em `.mp4` e `.ogg`. Decidir aceitar áudio-arquivo/documento/vídeo (extraindo o áudio) e limites explícitos de duração/tamanho com mensagem clara.
8. **Semestres disponíveis:** calculados a partir da data de **cadastro** do aluno (alunos antigos cadastrados agora não podem gerar semestres passados). Avaliar usar a data da primeira aula.
9. **Data da aula:** sempre "hoje". Permitir informar/editar a data (aula registrada no dia seguinte).
10. **Edição manual:** hoje a única forma de ajustar é pedir à IA. Avaliar edição direta dos campos na Web antes de aprovar.
11. **Correção pós-aprovação:** aprovado é imutável; não há "retificação". Definir processo (nova versão com trilha, ou cancelamento administrativo).
12. **Entrega às famílias:** o relatório aprovado não é enviado a ninguém. Avaliar envio ao responsável (e-mail/WhatsApp) e/ou área do responsável.
13. **Cadastros:** falta listar/editar/inativar alunos e professores, alterar associações, desvincular Telegram, validar CPF (dígitos verificadores) e data real.
14. **Geração semestral automática** (roadmap): lembrar o professor no fim do semestre ou gerar automaticamente para revisão.
15. **Retenção** de transcrições, versões antigas de PDF e relatórios cancelados; exclusão a pedido (LGPD).
16. **Instrumento/curso do aluno:** o MVP não registra instrumento, nível ou turma; relevante para relatórios e para a identidade (piano).

### 17.3 Robustez e experiência
1. Processamento de áudio **síncrono** dentro da requisição (Web e Telegram), sujeito a timeouts de proxy/provedores. Recomendado: processamento assíncrono com status explícitos (ex.: `PROCESSANDO`, `FALHOU`) e polling/push; o cliente Web já está preparado para polling.
2. Falha do LLM após a transcrição deixa um rascunho sem campos (não transacional). Adicionar status de falha e opção "tentar novamente" sem reenviar o áudio (a transcrição está salva).
3. Sem timeouts/retries configurados nas chamadas de IA; sem circuit breaker; sem fila.
4. Tratar **todas** as respostas de erro no Telegram com mensagens específicas (seção 11.3) e sempre responder algo ao professor.
5. Polling com timeout, tratamento de status de falha e retomada.
6. Confirmação antes de cancelar; não navegar se a ação falhou.
7. Mostrar os campos estruturados em texto na Web (além do PDF), para leitura rápida no celular.
8. Prompts versionados, com acentuação correta, tom institucional, instruções de estilo do professor e testes de regressão com as gravações fictícias do cliente; registrar custo por chamada.
9. Observabilidade: logs estruturados sem dados sensíveis, métricas de tempo/custo por relatório, alertas de falha de provedor.
10. Backups automatizados do banco e do storage de PDFs.

---

## 18. Princípios sugeridos para a constituição do definitivo (`/speckit.constitution`)

Herdados do MVP (constituição v1.1.0) e adaptados para não prender stack:

1. **Domínio independente de canal e de fornecedor.** Regras de negócio vivem no núcleo da aplicação (domínio → casos de uso → adaptadores). Telegram, Web e futuros canais (WhatsApp) são **adaptadores de entrada** que apenas traduzem requisições para os mesmos casos de uso; provedores de STT/LLM/PDF/storage ficam atrás de **portas** substituíveis. Dependências sempre apontam para dentro.
2. **Uma única fonte da verdade.** Só o backend escreve no banco. Orquestradores/automações nunca contêm regra de negócio, autorização ou validação de domínio, e nunca persistem dados de domínio. Estado de conversa é persistido no backend.
3. **Humano no controle da IA.** Nenhum relatório gerado por IA se torna oficial sem aprovação explícita da versão vigente pelo professor; toda alteração gera nova versão rastreável; a IA não inventa dados ausentes da fonte.
4. **Privacidade desde o design (LGPD, menores de idade).** Minimização de dados enviados à IA; áudio não retido; CPF cifrado; segredos fora do código; logs sem dados sensíveis; professor restrito aos próprios alunos; HTTPS; serviços internos não expostos.
5. **Qualidade obrigatória.** Cobertura mínima de testes unitários de 90% (linha e branch) na regra de negócio; testes de contrato para APIs consumidas por outros componentes; CI bloqueia merge abaixo do limite.
6. **Nomenclatura previsível** por camada/responsabilidade (sufixos padronizados) e linguagem ubíqua do domínio. [DECISÃO PENDENTE: a constituição do MVP exigia código em inglês, mas o código real ficou em português; escolher uma convenção e aplicá-la de forma consistente.]
7. **Simplicidade e foco.** Novas integrações/abstrações só quando uma spec exigir; itens de roadmap exigem justificativa e aprovação.
8. **Fluxo de trabalho.** `main` atualizada → branch por feature/spec → PR com testes e revisão de camadas, segurança e privacidade; artefatos de automação versionados no repositório.
9. **Identidade visual consistente.** Interfaces e documentos gerados (PDFs, mensagens do bot) seguem a identidade do Instituto Musical Felipe Blaft; acessibilidade WCAG AA; mobile-first.

---

## 19. Quebra sugerida em features/specs para o definitivo

| Ordem | Feature (spec) | Conteúdo principal | Seções de referência |
|---|---|---|---|
| 001 | Fundação, identidade e acesso | papéis (secretaria, professor), autenticação admin, credencial/código do professor, sessões, rate limit, controle de acesso professor↔aluno, auditoria básica | 2.4, 5.2, 5.3, 5.8, 13 |
| 002 | Cadastros da secretaria | professores (CRUD, ativo, códigos), alunos (CRUD, CPF cifrado, menor/responsável), associações, listagens | 4, 5.1, 12.2 |
| 003 | Núcleo do relatório de aula (IA) | registrar aula, STT, estruturação, perguntas, revisão, versões, aprovação, cancelamento, PDF de aula, rascunho pendente | 5.4, 6.1, 8, 9 |
| 004 | Portal web do professor | login, painel, alunos, gravação/upload, acompanhamento, conferência, retomada, histórico | 7.4–7.9, 12.3 |
| 005 | Canal Telegram (registro de aula) | vinculação, roteamento por estado, seleção de aluno, áudio, perguntas, aprovar/alterar/cancelar, mensagens | 5.6, 6.3, 7.2, 7.3, 11 |
| 006 | Relatório semestral | semestres disponíveis, contagem, consolidação, PDF semestral, revisão, aprovação | 5.5, 6.2, 7.7, 8.4, 8.5 |
| 007 | Histórico e documentos | histórico unificado, download autorizado de PDFs, identidade visual dos PDFs | 5.7, 9, 15 |
| 008 | Design system e identidade | tokens da marca, tipografia, componentes, acessibilidade, mobile-first | 15 |
| 009 | Privacidade, retenção e operação | políticas LGPD, retenção, exclusão, backups, observabilidade, custos | 13, 14, 17 |

---

## Apêndice A — Checklist de paridade com o MVP (critérios de aceite mínimos)

- [ ] Secretaria cadastra professor e recebe código de 8 caracteres válido por 7 dias; e-mail duplicado é recusado.
- [ ] Secretaria reemite código; a lista mostra se o professor tem Telegram vinculado.
- [ ] Secretaria cadastra aluno com CPF único (armazenado cifrado, nunca exibido); menor de 18 exige responsável; associa 0..N professores.
- [ ] Professor vincula o Telegram enviando o código; o código é consumido; conflitos de vínculo são recusados.
- [ ] Professor entra no portal com o código; sessão expira após 12h de inatividade e é renovada a cada uso; 5 erros → bloqueio de 15 min por origem.
- [ ] Professor só vê/age sobre alunos associados (403) e relatórios próprios (404).
- [ ] Professor registra aula por nota de voz no Telegram (após escolher o aluno pelo nome) e por gravação/upload na Web.
- [ ] Áudio inválido não cria registro; áudio nunca é armazenado; data da aula no fuso da escola.
- [ ] A IA estrutura o relatório; se faltar informação, pergunta antes de gerar o PDF.
- [ ] Todo relatório tem PDF por versão; alteração em linguagem natural gera nova versão e novo PDF.
- [ ] Aprovação exige a versão vigente; aprovado é imutável e vai para o histórico; cancelamento é terminal e proibido após aprovação.
- [ ] Relatório iniciado em um canal pode ser retomado/cancelado no outro; a Web oferece "continuar" ou "descartar".
- [ ] No Telegram: "aprovar", "cancelar", resposta e instrução de alteração funcionam conforme o estado.
- [ ] Semestral (Web): menu de semestres do cadastro até o atual; contagem de aprovados; bloqueio sem dados; consolidação em 10 seções; PDF; alteração; aprovação; cancelamento.
- [ ] Histórico só com aprovados, ordenado; secretaria vê qualquer aluno, professor só os seus.
- [ ] Erros sempre no formato `{codigo, mensagem}`, sem detalhes internos.

## Apêndice B — Referências no repositório do MVP

| Assunto | Caminho |
|---|---|
| Visão original do produto | `MVP_Escola_de_Musica_IA_n8n_Telegram_Docker.md` |
| Constituição do MVP | `.specify/memory/constitution.md` |
| Migrações (modelo de dados) | `backend/src/main/resources/db/migration/V1..V14` |
| Configuração | `backend/src/main/resources/application.yml`, `infrastructure/.env.example`, `infrastructure/docker-compose.yml` |
| Regras de domínio | `backend/.../domain/*.java` (RelatorioAula, RelatorioSemestral, Professor, Aluno, TentativaAutenticacaoWeb) |
| Casos de uso | `backend/.../usecase/*.java`, `usecase/support/PeriodoSemestralResolver.java` |
| Prompts e integração de IA | `backend/.../gateway/LlmGatewayImpl.java`, `SpeechToTextGatewayImpl.java` |
| PDF | `backend/.../service/PdfGeracaoService.java` |
| Segurança | `backend/.../config/SecurityConfig.java`, `ProfessorJwtAuthenticationFilter.java`, `N8nServiceTokenFilter.java`, `service/RateLimiterAutenticacaoWebService.java`, `service/CriptografiaService.java` |
| Controle de acesso | `backend/.../service/ControleAcessoProfessorService.java` |
| Fluxo Telegram atual | `n8n/workflows/RegistroAulaTelegram.json`, `backend/.../controller/TelegramRegistroAulaController.java`, `TelegramWebhookController.java` |
| Portal e admin (funcional) | `frontend/src/portal/**`, `frontend/src/pages/**` |
| Seed e teste local | `infra/seed-dev.sql`, `infra/TESTE-LOCAL.md`, `infra/postman_collection.json` |
| Identidade visual e áudios de teste | `assets/` |
