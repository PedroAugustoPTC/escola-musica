# Plano de Execução — do MVP ao sistema definitivo (Spec Kit / SDD)

> **Projeto:** Sistema de Relatórios Pedagógicos por Voz — Instituto Musical Felipe Blaft
> **Base:** `HANDOFF_MVP_Relatorios_Escola_Musica.md` + specs `001` e `002` + constituição v1.4.0
> **Criado em:** 08/10/2026
> **Para quem:** quem vai conduzir o Spec Kit (você e o Claude Code), fase por fase, copiando os prompts abaixo.

---

## 0. Como usar este documento

1. Siga as etapas **na ordem**. Cada uma termina com uma linha "**Pronto quando**": só avance quando ela for verdadeira.
2. Os prompts estão em blocos `text`. Copie e cole depois do comando (`/speckit-specify <prompt>`).
3. A **constituição prevalece** sobre este plano e sobre o handoff (`.specify/memory/constitution.md`). Se um prompt entrar em conflito com ela, a constituição vale.
4. Regras que valem em **todas** as etapas (vêm do `CLAUDE.md` raiz e do Princípio VIII):
   - Toda feature, spec, correção ou emenda nasce em **branch nova criada a partir da `main` atualizada**.
   - Ao concluir, **commit + Pull Request para a `main`**, sem esperar novo pedido.
   - **Ninguém mescla o próprio PR** e não existe merge automático: o merge é seu, depois da revisão.
   - Depois de criar cada PR, rodar `code-review-graph build`.
5. Antes de mexer em uma estrutura (`backend/`, `frontend/`, `infra/`...), leia o `CLAUDE.md` dela.
6. **Idioma dos artefatos:** specs, planos, tasks, documentação, comentários e mensagens de commit/PR continuam em **português**. A partir da Fase 1 deste plano, o **código** (pacotes, classes, tabelas, rotas, campos de API, variáveis de ambiente) passa a ser em **inglês**; só os **textos mostrados ao usuário** e os **logs** ficam em português.

### Legenda

| Marca | Significado |
|---|---|
| **[VOCÊ]** | Ação que só você faz (decidir, revisar, mesclar PR) |
| **[CLAUDE]** | Ação que o Claude Code executa a partir do prompt |
| **Pronto quando** | Critério de saída da etapa |

---

## 1. Onde estamos

### 1.1 Já entregue (specs `001` e `002`, na `main`; todas as tasks marcadas como concluídas)

- Registro de aula por áudio até a aprovação, na **Web e no Telegram**, com paridade.
- Perguntas pendentes, alteração em linguagem natural, versões, aprovação e cancelamento.
- Retomada de relatório em aberto entre canais, relatório semestral e histórico do professor.
- Login do professor por código, rate limit, controle de acesso por professor, PDFs autorizados por dono.
- Carga inicial por comando (`CargaComando`), sem tela de cadastro.
- Backend da API interna do Telegram (outbox, idempotência, token de serviço com rotação).

> Esta conclusão vem da leitura das specs, dos checkboxes de `tasks.md` e de buscas no código. **Não rodei a suíte de testes nem subi o sistema.** A Fase 0 abaixo existe para confirmar isso.

### 1.2 O que falta (nenhum código existe ainda)

| # | Lacuna | Origem |
|---|---|---|
| A | **Área de administração**: login do administrador, cadastro/edição/inativação de professores e alunos (o professor, só dos seus), associações, emissão e reemissão de código, histórico de qualquer aluno, gestão de administradores | Handoff 2.4, 5.1, 12.2, 17.1, 17.2; spec 001 (Assumptions) |
| B | **Orquestrador n8n** como transporte do Telegram (workflows, contêiner, troca do webhook, remoção do adaptador antigo) | Spec 002 (fora de escopo); constituição v1.4.0 |
| C | **Privacidade, retenção e operação**: retenção/exclusão, consentimento do responsável, auditoria, observabilidade e custos, backup automatizado | Handoff 13.3, 17.3, 19 (item 009) |

### 1.3 Novas diretrizes que mudam o plano

| Diretriz | Impacto |
|---|---|
| **Código e pacotes em inglês**; só texto ao usuário e logs em português | Exige **emenda da constituição** (Princípio VI hoje manda português sem acentos) e **migração do código existente** (feature `003`) |
| **VPS genérica**, sem assumir Hostinger | Exige emenda da constituição ("Containerização e implantação") e limpeza de `infra/`, `.github/` e docs |
| **Sem evoluções de produto por enquanto** | Nenhuma feature de evolução entra neste plano |

> A constituição também manda (Princípio I e "Restrições Adicionais") que **a administração exista nos dois canais**, Web e Telegram. O handoff dizia "somente Web". Vale a constituição, então a administração nasce com paridade.

---

## 2. Visão geral das etapas

| Etapa | O que é | Por que nesta posição |
|---|---|---|
| **Fase 0** | Preparação, auditoria de `001`/`002`, decisões de produto | Garante base confiável e decisões fechadas antes de especificar |
| **Fase 1** | Emenda da constituição **v2.0.0** (idioma + VPS genérica) e `CLAUDE.md` | Todo código novo precisa nascer sob a regra nova |
| **Feature 003** | **Migração do código para inglês** (refatoração, comportamento idêntico) | Antes de qualquer feature nova, para não escrever mais código em português |
| **Feature 004** | Orquestrador **n8n** (transporte do Telegram) | Exigido pela constituição; a administração já nasce sobre o transporte final |
| **Feature 005** | **Administração — acesso e cadastros** (Web + Telegram) | Maior lacuna funcional |
| **Feature 006** | **Administração — visão da escola** (histórico de qualquer aluno) | Depende dos cadastros da 005 |
| **Feature 007** | **Privacidade, retenção e operação (LGPD)** | Antes de operar com dados reais de menores |
| **Fase final** | Implantação em VPS genérica e aceite global | Fecha o ciclo |

> **Atenção à numeração:** a spec `002` e a constituição v1.4.0 chamam o n8n de "feature 003". Como a migração para inglês entra primeiro como `003`, o n8n vira `004`. A feature `003` inclui corrigir essas referências nos documentos.

---

## 3. Ciclo padrão de cada feature

Todas as features (003 a 007) seguem **o mesmo ciclo**. Cada feature abaixo traz os prompts prontos para cada passo.

| Passo | Comando | Quem | Resultado |
|---|---|---|---|
| 0 | `git checkout main && git pull --ff-only origin main` | [VOCÊ] ou [CLAUDE] | `main` local igual à remota |
| 1 | `/speckit-specify <prompt>` | [CLAUDE] | Cria a branch `NNN-nome` e o `spec.md` |
| 2 | `/speckit-clarify <prompt>` | [CLAUDE] + [VOCÊ] responde | Até 5 perguntas; respostas gravadas na spec |
| 3 | `/speckit-checklist <prompt>` *(opcional)* | [CLAUDE] | Checklist de qualidade dos requisitos |
| 4 | `/speckit-plan <prompt>` | [CLAUDE] | `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md` + Constitution Check |
| 5 | `/speckit-tasks <prompt>` | [CLAUDE] | `tasks.md` ordenado por dependência |
| 6 | `/speckit-analyze <prompt>` | [CLAUDE] | Relatório de consistência entre spec, plano e tasks (não altera nada) |
| 7 | `/speckit-implement <prompt>` | [CLAUDE] | Código e testes, task a task |
| 8 | `/speckit-converge <prompt>` | [CLAUDE] | Compara código x spec/plano/tasks e acrescenta o que faltou |
| 9 | `/code-review` e, se tocar autenticação ou dado de aluno, `/security-review` | [CLAUDE] | Achados corrigidos |
| 10 | Commit + Pull Request para a `main` (sem merge) | [CLAUDE] | PR aberto |
| 11 | `code-review-graph build` | [CLAUDE] | Grafo atualizado |
| 12 | Revisar e **mesclar o PR** | [VOCÊ] | Feature na `main` |

### 3.1 Critérios de saída por passo

| Passo | Pronto quando |
|---|---|
| specify | `spec.md` sem `[NEEDS CLARIFICATION]` que bloqueie; descreve o quê/por quê, **sem stack** |
| clarify | Respostas gravadas na seção *Clarifications*; nenhuma ambiguidade crítica aberta |
| plan | *Constitution Check* aprovado; desvios justificados na seção de complexidade; contratos escritos |
| tasks | Cada user story tem tasks independentes e testáveis; testes antes da implementação |
| analyze | Zero achados `CRITICAL`; `HIGH` resolvidos ou justificados |
| implement | Todas as tasks marcadas; suíte verde nos contêineres; cobertura ≥ 90% (linha e branch) na regra de negócio |
| converge | Nenhuma task nova pendente |
| PR | Descrição com resumo, testes executados e riscos; CI verde; **sem merge** |

### 3.2 Comandos de teste (contêiner)

```bash
sh infra/scripts/local.sh up               # sobe o ambiente local completo
sh infra/scripts/local.sh tests backend    # testes do backend (JaCoCo, ArchUnit)
sh infra/scripts/local.sh tests frontend   # Vitest, lint, typecheck
sh infra/scripts/local.sh tests e2e        # Playwright + axe (acessibilidade)
```

> O script já tem os subcomandos em inglês (feature `003`); este bloco foi atualizado nessa PR. O restante do documento é planejamento histórico e mantém os nomes da época.

### 3.3 Quality gates em todo PR (constituição)

- Camadas e regra de dependência respeitadas (Princípio I); nomenclatura conforme a constituição vigente (Princípio VI).
- **Paridade Web x Telegram** em qualquer funcionalidade nova ou alterada. Entregar em um canal só é desvio e precisa de justificativa no plano, com a entrega do outro canal registrada como pendente.
- Testes incluídos; cobertura mínima mantida; CI como fonte de verdade.
- Revisão de segurança e privacidade se tocar dado de aluno, áudio, autenticação ou IA.
- Revisão da identidade visual e acessibilidade (WCAG 2.1 AA, mobile-first) se tocar interface, PDF ou mensagem do bot.
- Aprovação explícita e versionamento preservados (Princípio III).

---

## 4. Fase 0 — Preparação, auditoria e decisões de produto

### 4.1 Passo a passo

1. **[VOCÊ]** Confirme que o PR #9 (`CLAUDE.md` por estrutura) está mesclado e atualize a `main`:
   ```bash
   git checkout main && git pull --ff-only origin main
   ```
2. **[CLAUDE]** Suba o ambiente e rode a suíte completa para registrar a **linha de base** (quantos testes, cobertura, tempo):
   ```bash
   sh infra/scripts/local.sh subir
   sh infra/scripts/local.sh testes backend
   sh infra/scripts/local.sh testes frontend
   sh infra/scripts/local.sh testes e2e
   ```
3. **[CLAUDE]** Audite as duas specs entregues. Faça em uma branch própria (`chore/auditoria-001-002`) e depois abra PR se algo for acrescentado:
   ```text
   /speckit-converge Audite a feature specs/001-fluxo-relatorios contra o código atual da main. Liste, requisito por requisito (FR-001 a FR-056 e SC-001 a SC-013), o que está implementado e coberto por teste, o que está implementado sem teste, e o que não está implementado. Dê atenção especial a FR-056 (registro de canal, meio, tempo de processamento e custo estimado das chamadas de IA por relatório), FR-022 (falha no processamento com nova tentativa sem reenviar áudio) e FR-025 (incorporação da resposta pela IA, no máximo 2 rodadas). Não implemente nada novo fora da spec; apenas acrescente ao tasks.md o que estiver faltando.
   ```
   ```text
   /speckit-converge Audite a feature specs/002-api-interna-canal-telegram contra o código atual da main, requisito por requisito (FR-001 a FR-030 e SC-001 a SC-007). Confirme especialmente idempotência por update_id, confirmação de ações, entrega proativa com retentativas (30 s dobrando até 15 min, prazo de 24 h), dois tokens de serviço simultâneos e ausência de dados pessoais em logs e ações. Apenas acrescente ao tasks.md o que estiver faltando.
   ```
   > Se o `.specify/feature.json` apontar para outra feature, o prompt já informa o diretório. Se a skill insistir na feature do `feature.json`, ajuste-o na branch de auditoria.
4. **[CLAUDE]** Se o `converge` acrescentar tasks, rode `/speckit-implement` só para elas, em branch `fix/...`, e abra PR.
5. **[VOCÊ]** Preencha a tabela de decisões da seção 4.2.

**Pronto quando:** a linha de base está registrada (número de testes, cobertura, tempo), o `converge` de `001` e `002` não deixa task pendente (ou as pendentes foram entregues em PR), e a tabela 4.2 está preenchida.

### 4.2 Decisões de produto (preencher antes de especificar)

Estas decisões são suas (ou do cliente). A coluna "Recomendação" é um ponto de partida, **não uma decisão tomada**. Os itens de **retenção** e **consentimento** têm implicação jurídica: valide com o cliente e com quem responde pela LGPD na escola.

| # | Decisão | Alimenta | Recomendação | Decisão do dono |
|---|---|---|---|---|
| D1 | **Semestral com vários professores.** A spec 001 consolida só as aulas do próprio professor e permite um semestral aprovado por (aluno, professor, período). O administrador vê todos. | 006 | Manter por professor; o administrador só **lê** os semestrais de todos os professores. Sem consolidação entre professores agora. | ☐ |
| D2 | **Como o administrador (e o professor, nas ações de cadastro) se autentica no Telegram.** O handoff (RN-11) diz que o `telegramUserId` não autentica ação administrativa, mas a constituição exige administração nos dois canais. | 005 | Vinculação do administrador por **código de uso único** emitido por outro administrador (ou pelo comando de bootstrap), com as mesmas regras de bloqueio por tentativas, e **confirmação explícita** nas ações destrutivas (inativar, reemitir código). Para o professor, o vínculo que ele já tem basta para cadastrar e manter **os seus** alunos, com **confirmação explícita** dos dados antes de gravar e nada além do seu escopo. | ☐ |
| D3 | **Revogação de acessos ao reemitir código ou inativar professor.** | 005 | Reemitir código **encerra** sessões Web e (opcional) o vínculo do Telegram; inativar professor encerra tudo. | ☐ |
| D4 | **Inativar professor/aluno com relatório em aberto.** | 005 | Bloquear com aviso listando os relatórios em aberto; permitir só após cancelar ou concluir. | ☐ |
| D5 | **Retirar a associação professor–aluno com relatório em aberto.** | 005 | Relatório em aberto fica visível ao professor até ser concluído ou cancelado; aprovados permanecem no histórico. | ☐ |
| D6 | **Retenção de dados** (transcrição, PDFs de versões antigas, relatórios cancelados). | 007 | *Proposta para validar:* apagar a transcrição N dias após a aprovação; manter só o PDF da versão aprovada; anonimizar cancelados após N dias; N configurável. | ☐ |
| D7 | **Eliminação a pedido (direito do titular).** | 007 | Fluxo conduzido pelo administrador, com anonimização do aluno e dos relatórios, registro do pedido e do atendimento. | ☐ |
| D8 | **Consentimento do responsável (menores).** | 007 | Registrar no cadastro quem consentiu, quando e a versão do termo; sem consentimento registrado, o aluno não pode ter relatório de IA. | ☐ |
| D9 | **O que a auditoria registra.** | 005, 006, 007 | Quem, quando, o quê, sobre qual registro: logins (sucesso/falha), cadastros e alterações, emissão de código, aprovação, download de PDF, exclusões. **Nunca** o conteúdo do relatório. | ☐ |
| D10 | **O administrador pode alterar ou aprovar relatório de professor?** | 006 | **Não.** Só o professor aprova (Princípio III). O administrador apenas consulta e baixa. | ☐ |
| D11 | **Quem é o responsável pela LGPD na escola** (contato e papel). | 007 | Definir uma pessoa; aparece nos termos e nos fluxos de eliminação. | ☐ |

---

## 5. Fase 1 — Emenda da constituição e diretrizes do projeto

**Objetivo:** registrar as duas novas diretrizes (idioma do código e VPS genérica) **antes** de qualquer código novo. É a única etapa que não segue o ciclo completo: é uma emenda (`/speckit-constitution`), em branch própria, no padrão das emendas anteriores.

- Branch: `docs/constitution-v2.0.0`
- Versão: **2.0.0 (MAJOR)**, porque o Princípio VI é redefinido (a regra "português" é substituída) e "Containerização e implantação" muda de premissa.

### 5.1 Passo a passo

1. **[VOCÊ/CLAUDE]** Atualize a `main` e crie a branch:
   ```bash
   git checkout main && git pull --ff-only origin main
   git checkout -b docs/constitution-v2.0.0
   ```
2. **[CLAUDE]** Rode a emenda:

   ```text
   /speckit-constitution Emende a constituição para a versão 2.0.0 (MAJOR, pois redefine o Princípio VI). Duas mudanças, e nada além delas.

   (1) PRINCÍPIO VI — passa a se chamar "Nomenclatura Previsível, Linguagem Ubíqua e Idioma". Nova regra de idioma:
   - EM INGLÊS: pacotes, módulos, tipos, funções, variáveis, constantes, nomes de arquivo e de pasta, tabelas e colunas do banco, valores de enumerações persistidas, rotas e campos das APIs e dos contratos, códigos de erro, ações e dados de botões do Telegram, chaves de catálogos de mensagens, variáveis de ambiente, nomes de scripts e de workflows de CI, classes e variáveis CSS.
   - EM PORTUGUÊS DO BRASIL, com acentuação correta: todo texto mostrado ao usuário (telas Web, mensagens e botões do bot, PDFs, e-mails se houver) e todas as mensagens de log. Palavras-chave que o usuário digita no bot ("aprovar", "cancelar", "início") continuam em português.
   - EM PORTUGUÊS: documentação, specs, planos, tasks, comentários de código e mensagens de commit e de Pull Request.
   - Os sufixos padronizados passam a ser em inglês, mantendo a mesma lógica de camadas: entidade de domínio sem sufixo técnico; *UseCase; *Controller; *Service; *Repository; *Port; *Adapter; *Mapper; *Dto, *Request, *Response. Atualize os exemplos (ex.: ApproveLessonReportUseCase, StudentRepository, TranscriptionPort, StudentRepositoryAdapter).
   - Inclua um GLOSSÁRIO DO DOMÍNIO pt→en como parte do princípio, com os termos canônicos (sem sinônimos): Professor→Teacher, Aluno→Student, Aula→Lesson, RelatorioAula→LessonReport, RelatorioSemestral→SemesterReport, VinculoTelegram→TelegramLink, Administrador→Administrator, CodigoDeAcesso→AccessCode, Associacao professor-aluno→TeacherStudentAssignment, Rascunho→Draft, Versao→Version, Aprovacao→Approval.
   - Mantenha as exceções permitidas (palavras reservadas, APIs de terceiros, nomes exigidos por ferramentas, siglas consagradas como PDF, LLM, HTTP, DTO).
   - REGRA DE TRANSIÇÃO: o código existente ainda está em português e é dívida registrada, a ser quitada pela feature 003 (migração para inglês). Enquanto a 003 não for mesclada, nenhuma feature nova pode ser iniciada.

   (2) CONTAINERIZAÇÃO E IMPLANTAÇÃO — onde está "VPS da Hostinger", passa a valer "uma VPS Linux genérica, de qualquer provedor". Exigências neutras de provedor: Linux com Docker Engine 27+ e Docker Compose v2 como únicas dependências; subir os contêineres a partir das definições versionadas no repositório; apenas o ponto de entrada HTTPS exposto publicamente (mais SSH para administração, restrito); nenhum recurso exclusivo de um provedor (painel, snapshot proprietário, DNS ou rede específicos) pode ser requisito. Atualize também o texto histórico do Sync Impact Report que cite Hostinger apenas se for necessário para não induzir erro, preservando o histórico de versões.

   Produza o Sync Impact Report da 2.0.0, confira os templates em .specify/templates/ (o plan-template lê a constituição em tempo de execução) e registre como "artefatos dependentes a revisar" a feature 003 e a atualização dos CLAUDE.md. Não altere os Princípios I a V, VII, VIII e IX.
   ```
3. **[CLAUDE]** Atualize as diretrizes do projeto (**mesma branch, mesmo PR**). Só texto: **não** renomeie identificadores (isso é da feature 003).

   ```text
   Alinhe os arquivos de diretrizes à constituição 2.0.0 que acabamos de aprovar, sem renomear nenhum identificador de código (isso será a feature 003):
   - CLAUDE.md da raiz: a frase "Código e identificadores em português do Brasil, sem acentos" passa a descrever a nova regra de idioma (código em inglês; texto ao usuário e logs em português; docs e commits em português) e registrar que o código atual ainda está em português até a feature 003.
   - frontend/CLAUDE.md: a regra "Nomes em português sem acentos (arquivos, componentes, funções)" deve refletir a nova regra.
   - backend/CLAUDE.md, backend/nucleo/CLAUDE.md, backend/aplicacao/CLAUDE.md, specs/CLAUDE.md, infra/CLAUDE.md, .github/CLAUDE.md e .specify/CLAUDE.md: revise qualquer menção a convenção de nomes em português.
   - Remova "Hostinger" de infra/CLAUDE.md, .github/CLAUDE.md, infra/README.md, infra/scripts/README.md, infra/compose.yaml, infra/compose.prod.yaml, infra/scripts/implantar.sh, .github/workflows/implantar.yml e de specs/001-fluxo-relatorios e specs/002-api-interna-canal-telegram (plan, research, quickstart, tasks), trocando por "VPS" genérica. Onde o texto descrever passo específico de painel do provedor, reescreva de forma neutra.
   Ao final, rode uma busca por "hostinger" (sem diferenciar maiúsculas) em todo o repositório, exceto node_modules e .git, e liste o que restar e por quê (por exemplo, o histórico do Sync Impact Report ou o HANDOFF).
   ```
4. **[CLAUDE]** Revise, faça commit e abra o PR (`docs: emenda 2.0.0 — idioma do código em inglês e VPS genérica`). Rode `code-review-graph build`.
5. **[VOCÊ]** Revise e mescle.

**Pronto quando:** a constituição está na v2.0.0, os `CLAUDE.md` refletem a regra nova, `grep -rIni hostinger` só encontra histórico justificado, e o PR está mesclado.

---

## 6. Feature 003 — Migração do código para inglês

> **Esta é a seção específica de conversão do código atual de português para inglês.**

### 6.1 O que é e o que não é

- **É** uma refatoração: o sistema deve se comportar **exatamente** como hoje. Nenhuma regra de negócio, texto de tela, texto do bot ou conteúdo de PDF muda.
- **É** transversal: backend, banco, contratos, frontend, infra, CI e scripts.
- **Não é** a hora de corrigir bugs, otimizar ou reorganizar camadas. Se aparecer defeito, anote e trate fora desta feature.

### 6.2 Tamanho do trabalho (levantado em 08/10/2026)

| Item | Quantidade / local |
|---|---|
| Classes Java (main / teste) | 307 / 129 |
| Arquivos TS/TSX (frontend) | 64 |
| Migrações SQL | 6 (`V1` a `V6`) |
| Contratos | `api-portal.openapi.yaml`, `canal-telegram-interno.openapi.yaml`, `acoes-v1.schema.json` |
| Pacotes (nucleo) | `casodeuso`, `dominio`, `porta`, `servico` |
| Pacotes (aplicacao) | `configuracao`, `entrada` (carga, interno, telegram, web), `saida` (armazenamento, audio, fila, ia, pdf, persistencia, seguranca, telegram) |
| Pastas do frontend | `app`, `componentes`, `estilos`, `mensagens`, `paginas`, `servicos` |
| Scripts / CI | `infra/scripts/local.sh`, `implantar.sh`, `.github/workflows/implantar.yml` e variáveis do `.env.example` |

### 6.3 Decisões já tomadas

| Pergunta | Decisão |
|---|---|
| Abrangência | **Tudo que é código**: pacotes, classes, variáveis, tabelas/colunas, rotas e campos JSON, códigos de erro, chaves de mensagens, variáveis de ambiente, CSS, nomes de arquivo, scripts |
| Dados existentes | **Não há produção nem dados reais**; pode renomear sem período de transição |
| Docs e comentários | Continuam em **português** |
| Texto ao usuário e logs | Continuam em **português**; só as **chaves** dos catálogos passam para inglês |

### 6.4 Glossário proposto (confirmar no `clarify`)

| Português | Inglês | | Português | Inglês |
|---|---|---|---|---|
| Professor | Teacher | | `dominio` | `domain` |
| Aluno | Student | | `casodeuso` | `usecase` |
| Aula | Lesson | | `porta` | `port` |
| RelatorioAula | LessonReport | | `servico` | `service` |
| RelatorioSemestral | SemesterReport | | `entrada` | `inbound` |
| VinculoTelegram | TelegramLink | | `saida` | `outbound` |
| Administrador | Administrator | | `configuracao` | `config` |
| CodigoDeAcesso | AccessCode | | `persistencia` | `persistence` |
| CasoDeUso | UseCase | | `armazenamento` | `storage` |
| Controlador | Controller | | `seguranca` | `security` |
| Servico | Service | | `fila` | `queue` |
| Repositorio | Repository | | `ia` | `ai` |
| Porta | Port | | `paginas` | `pages` |
| Adaptador | Adapter | | `componentes` | `components` |
| Mapeador | Mapper | | `servicos` (front) | `services` |
| Requisicao / Resposta | Request / Response | | `estilos` | `styles` |
| Rascunho | Draft | | `mensagens` | `messages` |

### 6.5 Pontos a decidir no `clarify` (com recomendação)

| # | Ponto | Recomendação |
|---|---|---|
| M1 | Valores de status persistidos e expostos (ex.: `RASCUNHO`, `PENDENTE_REVISAO`) | Passam para inglês (`DRAFT`, `PENDING_REVIEW`...); a interface mostra o rótulo em português pelo catálogo |
| M2 | Códigos de erro da API (`CODIGO_INVALIDO`, `BLOQUEADO_TEMPORARIAMENTE`) | Inglês (`INVALID_CODE`, `TEMPORARILY_BLOCKED`); mensagens ao usuário seguem em português |
| M3 | Dados dos botões do Telegram (`v1:<acao>:<arg>`) | Ações em inglês (`v1:approve:<id>`). Sem produção, não há botão antigo em circulação para suportar |
| M4 | Palavras que o professor digita no bot (`aprovar`, `cancelar`, `início`) | **Ficam em português**: é entrada do usuário, não identificador |
| M5 | Nome do arquivo PDF que o usuário baixa/recebe (`relatorio-aula-<id>-v<n>.pdf`) | Nome **mostrado ao usuário** continua em português (cabeçalho de download e nome do documento no Telegram); o nome interno de armazenamento fica em inglês |
| M6 | Rotas da SPA (ex.: `/entrar`, `/alunos`) | Inglês (`/sign-in`, `/students`): é código, não texto de tela |
| M7 | Banco: renomear por nova migração `V7` ou reescrever `V1`–`V6` | **Nova `V7`** que renomeia tabelas, colunas, índices, constraints e valores de enumeração; não editar migrações já aplicadas |
| M8 | Variáveis de ambiente (`CPF_CHAVE_CIFRAGEM`, `SEGREDO_HMAC`...) | Inglês (`CPF_ENCRYPTION_KEY`, `HMAC_SECRET`...); atualizar `.env.example`, compose, CI e READMEs; quem já tem `.env` local precisa regerá-lo |
| M9 | Como impedir regressão para português | Teste guardião automático (ver 6.6) |
| M10 | Nomes de scripts e workflows (`implantar`, `local.sh testes`) | Inglês (`deploy`, subcomandos `up`, `test`...), com o README atualizado |

### 6.6 Salvaguardas

1. **Suíte inalterada**: unidade, contrato, paridade, e2e e acessibilidade passam **sem mudar nenhuma expectativa de texto**. Se uma expectativa de texto precisar mudar, pare e reporte: é sinal de que algo além do nome mudou.
2. **Renomeação mecânica por ferramenta** (IDE ou `refactor_tool` do grafo), nunca à mão, arquivo por arquivo.
3. **Teste guardião**: um teste que falha se surgir identificador com termo do glossário em português (lista inicial: `aluno`, `professor`, `relatorio`, `aula`, `semestral`, `vinculo`, `codigo`, `casodeuso`, `porta`, `servico`, `repositorio`, `controlador`, `adaptador`, `entrada`, `saida`, `tela`, `pagina`, `componente`, `rascunho`, `aprovacao`...). Precisa de lista de exceções para siglas e nomes de terceiros. Cobre Java (ArchUnit), TypeScript (regra de lint ou script) e SQL/YAML (script).
4. **Fatias que compilam e passam a cada passo**, cada uma com commit próprio: glossário e guardião → `nucleo` → `aplicacao` (+ banco `V7` + configuração) → contratos e tipos gerados do frontend → frontend → infra, CI e scripts → documentação.
5. **Specs antigas (001 e 002)** que citam nomes antigos de classe e arquivo ficam como **histórico**, com uma nota no topo apontando para o glossário. Já as referências a "feature 003" (n8n) são corrigidas para `004`.
6. Os `CLAUDE.md` de cada estrutura que descrevem pacotes e pastas são atualizados **no mesmo PR**.

### 6.7 Prompts da feature 003

**Passo 0 — preparação**
```bash
git checkout main && git pull --ff-only origin main
```

**Passo 1 — `/speckit-specify`**
```text
/speckit-specify Migrar todo o código existente do sistema do português para o inglês, sem alterar nenhum comportamento observável, conforme a constituição 2.0.0 (Princípio VI).

Contexto: o sistema foi construído com identificadores em português. A constituição 2.0.0 passou a exigir código em inglês, mantendo em português apenas os textos mostrados ao usuário (telas, bot, PDFs) e os logs; documentação, specs, comentários e commits seguem em português.

Valor: eliminar a dívida de nomenclatura registrada na regra de transição da constituição, para que toda feature futura (n8n, administração, privacidade) nasça no idioma correto, e deixar o código legível por qualquer desenvolvedor.

Escopo (tudo que é código): pacotes, módulos, classes, interfaces, métodos, variáveis, constantes, arquivos e pastas; tabelas, colunas, índices, constraints e valores de enumeração do banco; rotas e campos das APIs e dos contratos (OpenAPI e JSON Schema); códigos de erro; ações e dados dos botões do Telegram; chaves dos catálogos de mensagens; variáveis de ambiente; classes CSS; componentes, páginas e rotas do frontend; nomes de scripts e de workflows de CI; testes.

Fora de escopo: qualquer mudança de regra de negócio, de texto exibido ao usuário, de conteúdo de PDF, de mensagem de log (permanecem em português), de layout, de comportamento do bot, de contrato semântico ou de desempenho; correção de bugs; reorganização de camadas; reescrita de specs antigas (ficam como histórico com nota). Palavras que o professor digita no bot ("aprovar", "cancelar", "início") continuam em português por serem entrada do usuário.

Regras e critérios:
- O sistema deve se comportar exatamente como antes: toda a suíte existente (unidade, contrato, paridade entre canais, e2e, acessibilidade) passa sem alterar nenhuma expectativa de texto.
- Deve existir um teste guardião automatizado que falha quando surgir identificador com termo do glossário em português, com lista de exceções documentada (siglas, nomes de terceiros).
- O glossário pt→en da constituição 2.0.0 é a fonte de verdade; um mesmo conceito tem um único nome em inglês.
- Não há produção nem dados reais: o banco pode ser migrado por migração nova, sem período de compatibilidade; rotas e botões antigos deixam de existir.
- As diretrizes (CLAUDE.md de cada estrutura), READMEs e scripts devem ser atualizados no mesmo trabalho.
- As referências a "feature 003" (n8n) nas specs 001/002 e na constituição passam a "feature 004".
Critérios de sucesso mensuráveis: zero identificadores em português fora da lista de exceções; suíte 100% verde; cobertura mínima mantida; ambiente local sobe com o comando único documentado; nenhum texto visível ao usuário mudou (verificado por comparação dos catálogos de mensagens e dos testes de paridade).
```

**Passo 2 — `/speckit-clarify`**
```text
/speckit-clarify Priorize as decisões M1 a M10 abaixo, que afetam o escopo e a segurança da migração; proponha a recomendação indicada e peça confirmação.
M1 valores de status persistidos em inglês com rótulo em português pelo catálogo; M2 códigos de erro em inglês; M3 ações dos botões do Telegram em inglês; M4 palavras digitadas pelo professor continuam em português; M5 nome do arquivo PDF mostrado ao usuário continua em português e o nome interno de armazenamento vai para inglês; M6 rotas da SPA em inglês; M7 banco por nova migração V7 sem editar V1–V6; M8 variáveis de ambiente em inglês; M9 teste guardião cobrindo Java, TypeScript e SQL/YAML; M10 scripts e workflows renomeados para inglês. Confirme também o glossário pt→en da constituição e peça os termos de domínio que faltarem.
```

**Passo 3 — `/speckit-plan`**
```text
/speckit-plan Planeje a migração para inglês como sequência de fatias, cada uma compilando e com a suíte verde, na ordem: (1) glossário e teste guardião; (2) módulo nucleo (pacotes, tipos, portas, casos de uso, testes, ArchUnit); (3) módulo aplicacao (adaptadores de entrada e saída, configuração) junto com a migração V7 do banco e as chaves de configuração; (4) contratos OpenAPI e JSON Schema e regeneração dos tipos do frontend; (5) frontend (pastas, componentes, páginas, rotas, chaves de mensagens, CSS, testes Vitest e Playwright); (6) infra, scripts, compose, variáveis de ambiente e workflow de CI; (7) documentação e CLAUDE.md.
Registre no research.md: como renomear por ferramenta (IDE ou refactor_tool do grafo) e como validar que nada além do nome mudou; como a migração V7 trata tabelas, colunas, índices, constraints e valores de enumeração em bancos locais existentes; como o catálogo de mensagens mantém os valores em português trocando só as chaves; como o teste guardião é escrito para Java (ArchUnit), TypeScript e SQL/YAML, com lista de exceções.
Faça o Constitution Check com atenção aos Princípios I (camadas intactas), V (cobertura mantida) e VI (v2.0.0). Não introduza nova abstração. Os testes continuam rodando em contêiner (sh infra/scripts/local.sh testes backend|frontend|e2e). Gere o data-model.md apenas com o mapa de renomeação de tabelas e colunas, e os contratos já com os nomes novos.
```

**Passo 4 — `/speckit-tasks`**
```text
/speckit-tasks Gere as tasks por fatia (7 fases, uma por fatia do plano), cada fase terminando em um checkpoint explícito: "compila, suíte da área verde, guardião verde para o que já foi migrado". Em cada fase, a primeira task é rodar a suíte e registrar a linha de base e a última é rodar a suíte de novo e comparar. Nenhuma task pode misturar renomeação com mudança de comportamento. Marque como paralelizáveis só tasks que não tocam os mesmos arquivos. Inclua tasks para: atualizar cada CLAUDE.md afetado, atualizar READMEs e .env.example, corrigir as referências "feature 003" para "004" nas specs 001/002 e na constituição, e acrescentar a nota de histórico no topo das specs antigas.
```

**Passo 5 — `/speckit-analyze`**
```text
/speckit-analyze Foque em: (a) toda categoria de identificador do escopo da spec (pacotes, tabelas, rotas, campos, códigos de erro, botões, chaves de mensagens, variáveis de ambiente, CSS, scripts) tem task correspondente; (b) nenhuma task altera comportamento; (c) o teste guardião cobre todas as categorias; (d) o Constitution Check da v2.0.0 está coerente; (e) não há conflito entre o glossário e termos já existentes no código.
```

**Passo 6 — `/speckit-implement`**
```text
/speckit-implement Execute fase por fase e pare em cada checkpoint para eu validar. Regras: use renomeação por ferramenta, nunca edição manual em massa; faça um commit por fase; rode a suíte da área nos contêineres a cada checkpoint; NÃO altere nenhuma expectativa de texto de teste: se um teste de texto quebrar, pare e relate em vez de ajustá-lo; mantenha os textos ao usuário e os logs em português e traduza só as chaves; se encontrar bug ou código morto, anote em vez de corrigir.
```

**Passo 7 — `/speckit-converge`**
```text
/speckit-converge Compare o código final com a spec: procure qualquer identificador ainda em português (inclusive em testes, SQL, YAML, JSON, scripts, workflows, Dockerfiles, CSS e chaves de mensagens), qualquer texto de usuário que tenha mudado, e qualquer referência quebrada em documentação. Acrescente ao tasks.md o que sobrar.
```

**Passo 8 — fechamento:** `/code-review`, commit, PR (sem merge), `code-review-graph build`. Atualizar a seção 3.2 deste documento com os nomes novos dos comandos.

**Pronto quando:** guardião verde; suíte completa verde com a mesma cobertura; ambiente local sobe pelo comando único; `grep` por termos do glossário em português não encontra identificador fora das exceções; PR mesclado por você.

---

## 7. Feature 004 — Orquestrador n8n (transporte do Telegram)

### 7.1 Objetivo e escopo

Fechar a arquitetura do canal Telegram: o **n8n passa a ser o único transporte**, só recebendo o update, repassando ao backend e executando as ações devolvidas. O backend já entrega a API interna (feature 002). O professor **não pode perceber nenhuma diferença**.

| Dentro | Fora |
|---|---|
| Workflows do n8n versionados como código | Qualquer regra de negócio, autorização ou persistência no n8n (Princípio II) |
| Serviço n8n em contêiner, imagem de versão fixada, no compose local e de produção | Mudar comportamento visível da conversa |
| Troca do webhook registrado no Telegram | Novas funcionalidades do bot |
| Remoção do adaptador antigo de envio direto ao Telegram (depois da paridade provada) | |
| Atualizar docs da 001 que assumem "sem n8n" (`research` R-04, `plan`, `quickstart`, `tasks`) | |

**Referências:** constituição "Orquestrador de canal (n8n)"; `specs/002-api-interna-canal-telegram/` (contratos e `quickstart`); `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` e `mensagens.md`; handoff 11, 16 (lições 7, 8, 9) e 17.1 (itens 2 e 7).

**Dependência:** feature 003 mesclada (os workflows e variáveis já nascem com nomes em inglês).

### 7.2 Prompts

**Passo 0:** `git checkout main && git pull --ff-only origin main`

**`/speckit-specify`**
```text
/speckit-specify Colocar o n8n como único transporte do canal Telegram, sem mudar nada do que o professor vê hoje. Esta é a feature que a spec 002 deixou como "fora de escopo".

Contexto: o backend já expõe a API interna de canal (feature 002): recebe o update bruto, devolve a lista ordenada de ações a executar no chat, entrega ações proativas com garantia (reentrega e idempotência) e serve PDFs por identificador opaco. Falta o componente que faz o transporte. Pela constituição v1.4.0 (Princípio II), o n8n é admitido somente como transporte: recebe o update, repassa ao backend e executa as ações devolvidas; nunca decide o que responder, nunca grava dados de domínio e nunca chama a IA.

Valor: separar o transporte do Telegram da aplicação, conforme a constituição, mantendo uma só fonte da verdade e permitindo reiniciar o orquestrador sem perder conversa.

O que a feature entrega:
- O n8n roda em contêiner, com versão de imagem fixada, no ambiente local e na produção, subindo pelo mesmo comando único dos demais serviços.
- Os workflows ficam versionados no repositório como código e o que roda na instância é reproduzível a partir deles; os arquivos não contêm segredo nem identificador de credencial.
- Há um único fluxo de entrada para o bot (o Telegram aceita um só webhook por bot).
- O webhook do Telegram é trocado para o n8n de forma controlada e reversível; o caminho antigo (backend falando direto com o Telegram) é removido somente depois de provada a paridade.
- Toda mensagem recebe resposta; toques em botão são confirmados; falha ao executar uma ação é informada ao backend com código de motivo, e as ações seguintes continuam em ordem.
- Com o n8n fora do ar, nenhuma mensagem proativa se perde: o backend reentrega quando ele volta, sem duplicar no chat.
- Os documentos da feature 001 que assumem "sem n8n" (research R-04, plan, quickstart, tasks) são atualizados.

Critérios: todos os testes de conversa e de paridade existentes passam também pelo caminho do n8n, com as mesmas entradas e as mesmas expectativas de texto, botões e ordem; 95% dos updates recebem resposta em até 2 s (SC-003 da 002); nenhum segredo nem dado de aluno em workflow, log ou exportação; reiniciar o n8n não perde estado de conversa.

Fora de escopo: qualquer regra de negócio ou autorização no n8n; novas funcionalidades do bot; mudança de textos; administração pelo Telegram (feature 005).
```

**`/speckit-clarify`**
```text
/speckit-clarify Priorize: (1) estratégia de troca do webhook e de reversão (janela, como provar paridade antes de remover o adaptador antigo); (2) como os workflows são importados na subida do contêiner e como credenciais e segredos chegam ao n8n sem aparecer nos JSON; (3) comportamento quando o n8n recebe update durante reinício; (4) quem opera o n8n em produção (acesso à interface, exposição pública proibida); (5) como validar a idempotência de ponta a ponta com updates repetidos.
```

**`/speckit-plan`**
```text
/speckit-plan Planeje o contêiner do n8n com versão de imagem fixada (nunca latest), na mesma rede interna dos demais, sem porta pública; só o Caddy expõe HTTPS e encaminha o webhook do Telegram ao n8n. Workflows exportados em JSON no repositório (pasta própria em infra/), sem segredos nem IDs de credencial (credenciais vindas de variáveis de ambiente), importados automaticamente na subida e com procedimento documentado para exportar de volta. Um workflow de entrada único. Reaproveite integralmente a API interna da feature 002 e seus contratos (specs/002-api-interna-canal-telegram/contracts/); não crie rota nova no backend sem necessidade justificada. Planeje: script de troca/restauração do webhook com setWebhook e secret token validado; rotação de token de serviço usando os dois tokens simultâneos; healthchecks e política de reinício; testes de contrato e de paridade passando pelo n8n em contêiner (Telegram simulado); remoção do adaptador antigo como última fase; atualização das docs da 001. Faça o Constitution Check dos Princípios II, IV, VIII e das restrições de contêiner e de orquestrador. Código, nomes de workflow, scripts e variáveis em inglês; mensagens ao usuário e logs em português.
```

**`/speckit-tasks`**
```text
/speckit-tasks Organize por user story, com testes antes da implementação. Fases: (1) contêiner e importação de workflows; (2) fluxo de entrada e execução de ações; (3) confirmação e tratamento de falha por ação; (4) entrega proativa e reentrega; (5) segurança (tokens, secret do webhook, rotação); (6) troca controlada do webhook com reversão; (7) remoção do adaptador antigo, somente após a suíte de paridade completa passar pelo n8n; (8) atualização das docs da 001 e dos CLAUDE.md/READMEs de infra. Inclua uma task de varredura que garante que nenhum JSON de workflow contém segredo ou ID de credencial.
```

**`/speckit-analyze`**
```text
/speckit-analyze Foque em: (a) algum requisito deixa regra de negócio ou autorização no n8n (violaria o Princípio II); (b) cobertura de todos os 10 estados da conversa e de todos os tipos de ação; (c) idempotência e reentrega têm teste; (d) nenhum segredo ou dado de aluno em workflow, log ou exportação; (e) a remoção do adaptador antigo só ocorre depois da prova de paridade.
```

**`/speckit-implement`**
```text
/speckit-implement Execute por fase, parando a cada checkpoint. Rode a suíte de conversa e de paridade pelo caminho do n8n em contêiner antes de avançar para a remoção do adaptador antigo. Não coloque lógica de decisão em nós do n8n: o workflow só repassa e executa. Mantenha o adaptador antigo funcionando até a fase de remoção.
```

**`/speckit-converge`**
```text
/speckit-converge Compare o resultado com a spec e a constituição: workflows sem segredos, imagem com versão fixada, único webhook, paridade completa, docs da 001 atualizadas, adaptador antigo removido sem deixar código morto, ambiente local sobe com o comando único.
```

**Fechamento:** `/code-review` + `/security-review` (toca integração e token de serviço), commit, PR (sem merge), `code-review-graph build`.

**Pronto quando:** o bot funciona de ponta a ponta pelo n8n em contêiner; suíte de paridade verde; `grep` por segredos nos workflows vazio; docs da 001 coerentes; PR mesclado.

---

## 8. Feature 005 — Administração: acesso e cadastros (Web + Telegram)

### 8.1 Objetivo e escopo

Dar à escola autonomia: o **administrador do sistema** passa a cadastrar e manter professores, alunos e associações, e o **professor** passa a cadastrar e manter os **seus próprios alunos**, **sem depender do comando de carga**, nos **dois canais**. **Aluno e responsável não têm nenhuma função administrativa** (nem acesso de qualquer tipo ao sistema).

> **Perspectiva de acesso (muda o handoff 2.4):** só **dois papéis** fazem administração: **Administrador** e **Professor**. O handoff concentrava todo o cadastro no "Administrador / Secretaria"; aqui o administrador cuida da escola inteira e o professor cuida apenas do que é seu. **Aluno e responsável nunca são atores**: não há conta, login, vínculo de Telegram, tela, rota nem comando para eles; o responsável existe só como **dado do cadastro** do aluno menor. Os papéis seguem separados (RN-14): a credencial de professor nunca autentica rota administrativa e a de administrador nunca autentica rota do professor.

**Quem faz o quê**

| Operação | Administrador | Professor |
|---|---|---|
| Entrar | E-mail e senha, sessão fixa de 2 h | Código de acesso (já entregue na spec 001) |
| Administradores: criar, inativar | Sim | Não |
| Professores: cadastrar, listar, editar, inativar/reativar | Sim, qualquer um | Não |
| Código de acesso: emitir, reemitir; desvincular Telegram | Sim, de qualquer professor | Não |
| Alunos: cadastrar | Sim, com 0..N professores associados | Sim; o aluno já nasce associado a ele |
| Alunos: listar/buscar, editar, inativar/reativar | Qualquer aluno | Somente os seus (associados a ele) |
| Associações professor–aluno | Incluir e retirar, em qualquer aluno | Não (só a associação automática ao cadastrar) |
| CPF | Informa; nunca é exibido de volta | Informa; nunca é exibido de volta |

| Dentro | Fora (feature 006 ou 007) |
|---|---|
| Login do administrador (sessão fixa de 2 h, bloqueio por tentativas) | Histórico de qualquer aluno (006) |
| Gestão de administradores (criar, inativar; nunca ficar sem nenhum ativo) | Auditoria completa, retenção e consentimento do responsável (007) |
| Professores, pelo administrador: cadastrar, listar, editar, inativar/reativar | Qualquer conta, login, tela, rota, comando ou aviso para aluno ou responsável |
| Alunos, pelo administrador (qualquer aluno): cadastrar, listar, editar, inativar/reativar; CPF cifrado e **validado**; data **real**; responsável obrigatório se menor; instrumento opcional | Professor administrar outros professores, administradores, códigos de acesso ou associações |
| Alunos, pelo professor (somente os seus): cadastrar (já associado a ele), listar, editar, inativar/reativar, com as mesmas validações | Administrador aprovar ou alterar relatório (Princípio III) |
| Escopo do professor imposto no núcleo: nunca vê nem altera aluno que não seja seu | |
| Associações professor–aluno editáveis a qualquer momento, pelo administrador | |
| Código de acesso: emitir, reemitir (validade de 7 dias), com revogação de acessos conforme D3 | |
| Desvincular o Telegram de um professor | |
| Paridade Web + Telegram para tudo acima, para o papel que executa cada operação | |
| Registro mínimo de autoria (quem criou/alterou, com o papel) para a 007 usar | |
| Ajustar os textos e o link da spec 001 que mandam o professor "falar com a secretaria" | |

**Referências do handoff:** 2.4, 4.2 (Administrador), 5.1 (RN-01 a RN-04), 5.2 (RN-05, RN-06, RN-11), 5.3 (RN-12, RN-14), 7.1, 12.2, 17.1 (itens 4 a 8), 17.2 (itens 13, 16). **Spec 001:** FR-011, Assumptions (secretaria). **Constituição:** Princípios I, III, IV, IX. **Decisões:** D2, D3, D4, D5 e D9 da seção 4.2. Nesta feature, D2 passa a cobrir também o **professor no Telegram** (o handoff, RN-11, diz que o `telegramUserId` não autoriza ação administrativa): fechar no `clarify` e, se aprovado, refletir na tabela 4.2.

**Dependência:** features 003 e 004 mescladas; decisões D2 a D5 preenchidas.

> **Risco:** é a maior feature funcional. Se o `plan` indicar mais de ~12 dias de trabalho, divida em duas branches/PRs por prioridade de user story (P1: acesso do administrador + professores + alunos, pelo administrador e pelo professor; P2: associações, administradores, código e desvincular Telegram), mantendo a paridade dentro de cada fatia.

### 8.2 Prompts

**Passo 0:** `git checkout main && git pull --ff-only origin main`

**`/speckit-specify`**
```text
/speckit-specify Área de administração para cadastros e acesso, disponível na Web e no Telegram com paridade (constituição, Princípio I e restrições de produto), de modo que a escola deixe de depender do procedimento operacional de carga inicial. Somente dois papéis têm função administrativa: o Administrador do sistema e o Professor (este apenas sobre os seus próprios alunos). Aluno e responsável não têm nenhuma função administrativa nem qualquer acesso ao sistema.

Contexto: hoje professores, alunos e associações entram no sistema por um comando de carga; não existe tela nem fluxo de cadastro. O professor já acessa por código de acesso (Web e Telegram) e só enxerga seus alunos. Esta feature cria o ator Administrador e as operações de cadastro, e dá ao Professor o cadastro dos seus próprios alunos (o handoff atribuía todo o cadastro à secretaria). Referências: HANDOFF seções 2.4, 5.1, 5.2, 7.1, 12.2 e 17; spec 001 (FR-004 a FR-011 e Assumptions); a feature fica sobre o transporte do Telegram da feature 004.

Atores: Administrador (opera a escola inteira); Professor (administra somente os alunos associados a ele); Aluno e responsável NÃO são atores: são sujeitos dos dados, sem conta, login, vínculo de Telegram, tela, rota nem comando. O responsável existe apenas como dado do cadastro do aluno menor. O conjunto de papéis autenticáveis é fechado em dois: Administrador e Professor.

Histórias, por prioridade:
P1 — Acesso do administrador: entra com e-mail e senha; sessão com validade fixa de 2 horas, sem renovação; após 5 tentativas incorretas consecutivas da mesma origem, bloqueio de 15 minutos (inclusive com a senha correta), com tempo restante informado, mesmas regras de zeragem das demais autenticações; nunca é possível sair do sistema sem pelo menos um administrador ativo; credencial do professor nunca autentica rota administrativa e vice-versa (RN-14). No Telegram, o administrador é identificado por vínculo próprio e as ações destrutivas exigem confirmação explícita (modelo de autenticação conforme a decisão D2).
P1 — Professores (administrador): cadastrar (nome, e-mail único; nasce ativo e com código de acesso de 8 caracteres válido por 7 dias exibido uma única vez para repasse), listar (com indicação de Telegram vinculado e situação), editar, inativar e reativar. E-mail duplicado é recusado com mensagem clara. O professor não gerencia professores.
P1 — Alunos (administrador): cadastrar (nome, data de nascimento real, CPF, nome do responsável obrigatório se menor de 18 anos na data do cadastro, instrumento opcional, professores associados — zero ou mais), listar com busca, editar, inativar e reativar, em qualquer aluno. O CPF é validado (dígitos verificadores), armazenado cifrado, usado para detectar duplicidade sem ser exibido e nunca é devolvido por nenhuma API nem aparece em log. Data inexistente (ex.: 31/02) é recusada.
P1 — Alunos (professor): cadastra aluno com os mesmos campos e validações; o aluno já nasce associado a ele; lista e busca somente os seus; edita e inativa/reativa somente os seus. Nunca vê nem altera aluno de outro professor, e nenhuma resposta revela a existência ou os dados de aluno que não seja seu. Se o CPF informado já pertencer a um aluno que não é dele, o cadastro é recusado com mensagem neutra orientando procurar o administrador, sem revelar nome nem professor. Se o aluno tem mais de um professor, o professor edita os dados cadastrais, mas só o inativa quando for o único professor associado; do contrário, a inativação é do administrador (confirmar no clarify). O professor não altera associações nem cadastra aluno para outro professor.
P2 — Associações (administrador): incluir e retirar professores de um aluno a qualquer momento, com o efeito sobre relatórios em aberto definido pelas decisões D4/D5.
P2 — Código de acesso (administrador): reemitir para qualquer professor (substitui o anterior, validade de 7 dias, exibição única) e, conforme D3, encerrar as sessões Web existentes e opcionalmente o vínculo do Telegram. Desvincular o Telegram de um professor.
P2 — Administradores: criar e inativar outros administradores; ninguém inativa a si mesmo se for o último.
P3 — Primeiro administrador: procedimento operacional seguro de bootstrap (sem senha padrão versionada).

O que muda no que já existe: os textos e o link da spec 001 que mandam o professor "falar com a secretaria" ou entrar na "área da secretaria" passam a refletir os papéis novos (professor sem alunos pode cadastrar o primeiro; código vencido ou conta já vinculada orientam procurar o administrador; o link da tela de entrada passa a falar em administrador). A carga inicial deixa de ter regra própria e passa a usar as mesmas operações.

Paridade: cada operação acima existe na Web e no Telegram, para o papel que a executa, com as mesmas regras de autorização, validação, mensagens (adaptadas ao meio) e resultado. No Telegram, o professor usa o vínculo que já possui, restrito aos seus alunos, e confirma explicitamente os dados antes de gravar (modelo conforme a decisão D2, estendida ao professor). Entrega em um canal só exige justificativa no plano.

Regras de privacidade e segurança: CPF e dados de menores nunca em logs; ações registram autoria, com o papel (administrador ou professor), para a futura trilha de auditoria (feature 007); o escopo do professor é imposto no núcleo, nunca só na interface; mensagens de erro sem detalhes internos; senha com hash forte; toda rota negada por padrão.

Fora de escopo: histórico de qualquer aluno e visão da escola (feature 006); retenção, exclusão a pedido, consentimento do responsável e auditoria completa (feature 007); qualquer conta, login, tela, rota, comando ou aviso para aluno ou responsável; professor gerenciar professores, administradores, códigos de acesso ou associações; edição/aprovação de relatório de professor pelo administrador (Princípio III: só o professor aprova); evoluções de produto.

Critérios mensuráveis: o administrador cadastra professor e aluno e entrega o código em até 3 minutos, em cada canal; o professor cadastra um aluno seu em até 3 minutos, em cada canal; 100% das operações disponíveis nos dois canais, para o papel que as executa; zero CPF em logs e respostas; zero acesso administrativo com credencial de professor; zero acesso do professor a aluno que não seja seu; zero papel, credencial ou rota para aluno ou responsável.
```

**`/speckit-clarify`**
```text
/speckit-clarify Priorize as decisões abertas de produto: D2 (como o administrador se autentica e confirma ações no Telegram, dado que o identificador do Telegram sozinho não é autenticação administrativa, e se o vínculo que o professor já tem basta, com confirmação, para cadastrar os próprios alunos), D3 (o que a reemissão de código e a inativação revogam), D4 (inativar professor ou aluno com relatório em aberto, inclusive quando quem inativa é o professor), D5 (retirar associação com relatório em aberto), D9 (que eventos de autoria são gravados agora, com o papel do autor). Verifique também: o que o professor pode fazer com aluno compartilhado com outro professor (editar, inativar); como recusar CPF que já pertence a aluno de outro professor sem vazar dado; se o bloqueio por tentativas do administrador compartilha o serviço das demais autenticações; se a reativação restaura vínculo e código; a política para o último administrador; e a confirmação de que aluno e responsável não terão nenhum acesso, nem de leitura, nesta feature.
```

**`/speckit-checklist`** *(recomendado nesta feature, por tocar autenticação)*
```text
/speckit-checklist Gere o checklist de qualidade dos requisitos de segurança e privacidade: autenticação e sessão do administrador, separação entre os dois papéis, isolamento do professor aos seus alunos, ausência de qualquer papel ou acesso para aluno e responsável, bloqueio por tentativas, tratamento do CPF, menores de idade, mensagens de erro (sem vazar existência de aluno de outro professor), autoria das ações e paridade entre canais.
```

**`/speckit-plan`**
```text
/speckit-plan Planeje reaproveitando o que existe: serviço de tentativas de acesso e bloqueio (usado por Web e Telegram), cifragem e hash do CPF, geração e renovação de código de acesso, casos de uso de carga inicial (a carga deve passar a usar os mesmos casos de uso novos, sem duplicar regra), vínculo do Telegram e fila de ações do canal. Núcleo com um caso de uso por operação, que recebe o ator (papel e identidade) e aplica o escopo: administrador, escola inteira; professor, somente os alunos associados a ele. Os adaptadores Web e Telegram não têm regra de negócio e chamam os mesmos casos de uso; rotas administrativas e rotas do professor ficam separadas e cada uma aceita só a sua credencial. O modelo de autorização tem exatamente dois papéis (Administrador e Professor), sem papel para aluno ou responsável. Definir: modelo de sessão do administrador (validade fixa de 2 h), modelo de vínculo e confirmação no Telegram conforme D2 (administrador e professor), revogação de sessão (token com versão de credencial ou sessão revogável), colunas de autoria com o papel do autor, validação de CPF e de data no núcleo, tratamento de CPF duplicado sem vazar dado de outro professor, contrato OpenAPI da área administrativa e do cadastro de alunos pelo professor (nova seção ou arquivo), telas do portal (área administrativa e "Meus alunos" do professor) seguindo portal-do-professor-design-system e a identidade do Instituto (mobile-first, WCAG AA, modo claro e escuro) e fluxo do bot. Faça o Constitution Check de I, II, III, IV, VI (código em inglês; textos e logs em português) e IX. Registre em research.md as alternativas descartadas (por exemplo, administração só na Web; professor sem cadastro próprio de alunos) e justifique qualquer desvio de paridade.
```

**`/speckit-tasks`**
```text
/speckit-tasks Organize por user story (P1 acesso do administrador; P1 professores; P1 alunos pelo administrador; P1 alunos pelo professor; P2 associações; P2 código e Telegram; P2 administradores; P3 bootstrap), cada uma entregando Web e Telegram juntos e testável de forma independente. Testes de contrato, de paridade entre canais, de segurança (credencial cruzada, bloqueio, CPF fora de logs e respostas), de isolamento (professor não encontra nem altera aluno de outro professor; CPF duplicado de outro professor recusado sem vazar dado), de que não existe papel, credencial nem rota para aluno ou responsável, e de acessibilidade antes da implementação. Inclua tasks de atualização dos CLAUDE.md, do .env.example e do README de infra, de migração da carga inicial para os casos de uso novos e de ajuste dos textos e do link da spec 001 que citam a secretaria.
```

**`/speckit-analyze`**
```text
/speckit-analyze Foque em: paridade (toda operação existe nos dois canais, para o papel que a executa); separação entre administrador e professor; escopo do professor imposto no núcleo; nenhum papel, credencial, rota ou aviso para aluno ou responsável; CPF e menores nunca em log nem resposta; ações destrutivas com confirmação; decisões D2 a D5 refletidas nos requisitos e nas tasks; ausência de regra de negócio em adaptadores; consistência dos nomes com o glossário em inglês.
```

**`/speckit-implement`**
```text
/speckit-implement Execute uma user story por vez, Web e Telegram juntos, parando a cada checkpoint. Escreva os testes primeiro e mantenha cobertura mínima de 90% no núcleo. Código, nomes de tabela, rotas e variáveis em inglês; textos ao usuário e logs em português com acentuação correta. O escopo do professor é decidido no núcleo, não na interface. Não coloque senha, CPF ou código de acesso em log. Rode a suíte nos contêineres (backend, frontend, e2e com axe) a cada checkpoint.
```

**`/speckit-converge`**
```text
/speckit-converge Confira requisito por requisito e a paridade Web x Telegram, a separação entre os dois papéis, o isolamento do professor aos seus alunos, a ausência de qualquer papel ou acesso para aluno e responsável, a revogação de acessos (D3), o tratamento dos casos D4/D5, a validação de CPF e de data, o bloqueio por tentativas e que a carga inicial usa os mesmos casos de uso. Acrescente ao tasks.md o que faltar.
```

**Fechamento:** `/code-review` + `/security-review` (autenticação e dado de menor), commit, PR (sem merge), `code-review-graph build`.

**Pronto quando:** o administrador opera todo o cadastro e o professor cuida dos seus alunos, pelos dois canais; nenhum acesso existe para aluno ou responsável; suíte verde com cobertura ≥ 90%; revisão de segurança sem achado crítico; carga inicial convertida; PR mesclado.

---

## 9. Feature 006 — Administração: visão da escola

### 9.1 Objetivo e escopo

O administrador **consulta** o que a escola produziu, sem interferir no ciclo do relatório.

| Dentro | Fora |
|---|---|
| Histórico de **qualquer aluno** (relatórios de aula e semestrais aprovados de **todos os professores**), ordenado como no histórico do professor | Aprovar, alterar, cancelar ou regerar relatório (D10: só o professor) |
| Filtros por aluno, professor, tipo e período | Consolidação semestral entre professores (D1) |
| Abrir e baixar PDFs com autorização específica para o administrador | Relatórios em aberto (rascunho) de professor |
| Paridade Web + Telegram | Métricas de custo e de uso (007) |

**Referências:** handoff 5.7 (RN-33, RN-34), 7.9, 12.2 (Histórico), 17.2 (item 2); spec 001 FR-047, FR-048. **Decisões:** D1, D10, D9.

**Dependência:** feature 005 mesclada.

### 9.2 Prompts

**Passo 0:** `git checkout main && git pull --ff-only origin main`

**`/speckit-specify`**
```text
/speckit-specify Visão da escola para o administrador: consultar o histórico de relatórios aprovados de qualquer aluno, de todos os professores, e abrir ou baixar os PDFs, na Web e no Telegram com paridade.

Contexto: o professor já consulta o histórico dos seus alunos, com os relatórios de sua autoria (spec 001, FR-047). O administrador (feature 005) ainda não consegue ver nada do que foi produzido. Referências: HANDOFF 5.7 (RN-33 e RN-34), 7.9 e 12.2; spec 001 FR-047 e FR-048.

Regras: o administrador vê o histórico de qualquer aluno cadastrado, com os relatórios de aula e semestrais APROVADOS de todos os professores, em ordem cronológica (aula pela data da aula; semestral pelo início do período), com professor autor, data e hora da aprovação e versão aprovada; pode filtrar por aluno, professor, tipo e período; abre e baixa PDFs. Relatórios em rascunho, aguardando respostas, em conferência, com falha ou cancelados NÃO aparecem. O administrador é somente leitura: não aprova, não altera, não cancela e não gera relatório (Princípio III). O download de PDF exige administrador autenticado e autorizado para aquele relatório (FR-048 estendido ao papel administrativo), nunca apenas o conhecimento do endereço. Cada consulta e cada download ficam registrados com autoria para a trilha da feature 007, sem conteúdo do relatório. Os semestrais continuam por professor (decisão D1): o administrador lê todos, sem consolidação entre professores.

Privacidade: o histórico contém dados de menores; nunca registrar conteúdo em log; mensagens de erro sem detalhe interno.

Fora de escopo: qualquer escrita sobre relatórios; métricas e painel analítico; exportação em lote; envio às famílias.

Critérios: o administrador encontra o histórico de um aluno em até 3 interações, em cada canal; 100% dos relatórios listados são aprovados; zero download de PDF sem autorização; paridade total entre canais.
```

**`/speckit-clarify`**
```text
/speckit-clarify Priorize: D1 (confirmar semestral por professor e leitura de todos pelo administrador), D10 (administrador estritamente somente leitura), D9 (o que a consulta e o download registram), formato do histórico no Telegram para listas longas (paginação), e se alunos inativos continuam consultáveis.
```

**`/speckit-plan`**
```text
/speckit-plan Reaproveite o caso de uso de histórico e a autorização de PDF por dono da feature 001, estendendo-os para o papel administrativo sem duplicar regra: o núcleo decide quem pode ver o quê. Defina o contrato da consulta administrativa, a paginação e os filtros, a tela Web (identidade do Instituto, mobile-first, WCAG AA) e o fluxo do bot com botões. Planeje o registro de autoria de consulta e download de modo compatível com a trilha da feature 007. Constitution Check de I, III, IV e IX; código em inglês e textos/logs em português.
```

**`/speckit-tasks`**
```text
/speckit-tasks Organize por user story com testes antes: histórico por aluno; filtros; PDF autorizado ao administrador; paridade com o Telegram (paginação por botões); registro de autoria. Inclua teste de acesso cruzado (token de professor em rota administrativa e vice-versa), teste de que nenhum relatório não aprovado aparece e teste de acessibilidade das telas novas.
```

**`/speckit-analyze`**
```text
/speckit-analyze Foque em: o administrador não tem nenhuma operação de escrita sobre relatórios; só aprovados aparecem; autorização de PDF cobre o papel administrativo; paridade; ausência de conteúdo de relatório em logs e registros de autoria.
```

**`/speckit-implement`**
```text
/speckit-implement Execute por user story, Web e Telegram juntos, testes primeiro, parando a cada checkpoint, com a suíte nos contêineres a cada checkpoint. Código em inglês; textos ao usuário e logs em português.
```

**`/speckit-converge`**
```text
/speckit-converge Confirme requisito por requisito, a ausência de qualquer rota de escrita para o administrador sobre relatórios, a autorização do PDF e a paridade. Acrescente ao tasks.md o que faltar.
```

**Fechamento:** `/code-review` + `/security-review`, commit, PR (sem merge), `code-review-graph build`.

**Pronto quando:** o administrador consulta qualquer aluno nos dois canais, sem poder alterar nada; testes de acesso cruzado verdes; PR mesclado.

---

## 10. Feature 007 — Privacidade, retenção e operação (LGPD)

### 10.1 Objetivo e escopo

Preparar o sistema para operar com **dados reais de menores**: política de dados aplicada em código, rastreabilidade e operação confiável.

| Bloco | Conteúdo |
|---|---|
| Retenção e eliminação | Retenção configurável de transcrições, PDFs de versões antigas e relatórios cancelados; fluxo de eliminação a pedido (D6, D7) |
| Consentimento | Registro do consentimento do responsável por aluno menor, com versão do termo (D8) |
| Auditoria | Trilha imutável de quem fez o quê e quando, sem conteúdo de relatório (D9) |
| Observabilidade e custos | Logs estruturados sem dados sensíveis; métricas de tempo e custo por áudio, por aula, por aluno, por semestral e mensal; alertas de falha de provedor e de fila |
| Backup | Backup automatizado do banco e do armazenamento de PDFs, fora da VPS e cifrado, com teste de restauração |
| Inventário de dados | Quais dados vão a quais provedores de IA, finalidade e base legal documentadas |

**Referências:** handoff 13.3, 14.1, 17.1 (item 8), 17.2 (itens 15, 16), 17.3 (itens 8 a 10), 2.3 (métricas de custo); spec 001 FR-055, FR-056, Assumptions (retenção); constituição Princípio IV. **Decisões:** D6 a D9 e D11.

**Dependência:** features 005 e 006 mescladas (a auditoria cobre os eventos delas).

> **Aviso:** esta feature **não é consultoria jurídica**. Os prazos e as bases legais devem ser validados pelo cliente e por quem responde pela LGPD na escola antes do `plan`.

### 10.2 Prompts

**Passo 0:** `git checkout main && git pull --ff-only origin main`

**`/speckit-specify`**
```text
/speckit-specify Privacidade, retenção e operação do sistema, para operar com dados reais de alunos, inclusive menores de idade, conforme a LGPD e o Princípio IV da constituição.

Contexto: o sistema já cifra o CPF, não retém áudio e restringe cada professor aos seus alunos, mas nada é apagado automaticamente (spec 001, Assumptions), não há trilha de auditoria, o consentimento do responsável não é registrado, as métricas de custo e tempo são limitadas e o backup é um comando manual documentado. Referências: HANDOFF 13.3, 14.1, 17.1 (item 8), 17.2 (itens 15 e 16), 17.3 (itens 8 a 10); spec 001 FR-055 e FR-056; decisões D6 a D9 e D11 desta etapa.

Entregas:
1. Retenção: política configurável que remove ou anonimiza transcrições, PDFs de versões antigas e relatórios cancelados após prazos definidos pelo dono do produto (D6), com execução automática, registro do que foi tratado (sem conteúdo) e proteção contra apagar relatório aprovado vigente.
2. Eliminação a pedido: fluxo conduzido pelo administrador, nos dois canais, que registra o pedido, anonimiza o aluno e seus relatórios conforme D7 e registra o atendimento.
3. Consentimento: registro, por aluno menor, de quem consentiu, quando e qual versão do termo (D8); regra de bloqueio quando ausente.
4. Auditoria: trilha imutável de eventos (login e falha de login, cadastro e alteração, emissão de código, aprovação, cancelamento, consulta e download de PDF, eliminação), com ator, momento, tipo e identificador do registro, nunca o conteúdo; consultável pelo administrador; com prazo de guarda próprio.
5. Observabilidade: logs estruturados em português sem áudio, transcrição, CPF nem dados pessoais além do necessário; métricas de tempo e custo estimado por áudio, aula, aluno, relatório semestral e mês, por canal e por provedor (confirmar o que o FR-056 já cobre e completar o resto); alertas para falha de provedor de IA, fila de entrega do canal acumulada e erro de armazenamento.
6. Backup e restauração: rotina automática do banco e do armazenamento de PDFs, cifrada, enviada para fora da VPS, com retenção configurável e teste de restauração documentado e automatizado; as chaves de cifragem do CPF e do HMAC guardadas junto, fora do repositório.
7. Inventário de tratamento: documento versionado dizendo quais dados vão para quais provedores de IA, por que, onde ficam e sob quais termos.

Paridade: as ações do administrador (eliminação, consulta de auditoria) existem na Web e no Telegram.

Fora de escopo: parecer jurídico; mudança de provedor de IA; evoluções de produto; anonimização de logs antigos fora da política definida.

Critérios mensuráveis: zero dado de aluno em logs, métricas e trilha (verificado por teste automatizado); 100% dos eventos listados na trilha; restauração testada de ponta a ponta dentro do tempo-alvo definido no clarify; eliminação a pedido concluída sem deixar dado pessoal recuperável fora do que a lei exigir manter.
```

**`/speckit-clarify`**
```text
/speckit-clarify Priorize: prazos de retenção (D6) e o que "anonimizar" significa para cada tipo de dado; efeitos da eliminação sobre relatórios aprovados e sobre o histórico (D7); regra de bloqueio sem consentimento (D8); lista final de eventos da trilha e prazo de guarda (D9); tempo-alvo e frequência do backup e onde ele é guardado; quem recebe os alertas; responsável pela LGPD na escola (D11).
```

**`/speckit-plan`**
```text
/speckit-plan Planeje a retenção como tarefa agendada do backend usando as portas existentes (sem regra de negócio fora do núcleo); a trilha de auditoria como registro append-only com proteção contra alteração; o consentimento no cadastro do aluno; as métricas sobre o que o FR-056 já grava, completando custo e tempo por unidade; o backup como serviço em contêiner no mesmo compose (local e produção), com destino configurável em variáveis de ambiente, cifragem e teste de restauração em CI; os alertas com o menor mecanismo que atenda. Para qualquer ferramenta de observabilidade nova, justifique no research.md por que é necessária (Princípio VII) e mantenha tudo em contêiner e sem exposição pública. Constitution Check de II, IV, V, VII e das restrições de contêiner e de implantação em VPS genérica. Código em inglês; textos e logs em português.
```

**`/speckit-tasks`**
```text
/speckit-tasks Organize por user story: retenção; eliminação a pedido; consentimento; auditoria; observabilidade e custos; backup e restauração; inventário. Testes antes, incluindo teste automatizado que varre logs, métricas e trilha atrás de CPF, nome de aluno, transcrição e texto de relatório, e teste de restauração do backup em contêiner limpo.
```

**`/speckit-analyze`**
```text
/speckit-analyze Foque em: nenhum requisito apaga relatório aprovado vigente por engano; a trilha não guarda conteúdo; backup e chaves ficam fora do repositório; todo prazo é configurável e tem valor padrão registrado; paridade das ações do administrador; coerência com as decisões D6 a D11.
```

**`/speckit-implement`**
```text
/speckit-implement Execute por user story, testes primeiro, parando a cada checkpoint, com a suíte nos contêineres. Nada de segredo no repositório. Teste a restauração do backup em um contêiner limpo antes de dar a história por concluída.
```

**`/speckit-converge`**
```text
/speckit-converge Confira requisito por requisito, a varredura de dados pessoais em logs/métricas/trilha, a restauração do backup, a configurabilidade dos prazos e a documentação operacional (README de infra). Acrescente ao tasks.md o que faltar.
```

**Fechamento:** `/code-review` + `/security-review`, commit, PR (sem merge), `code-review-graph build`.

**Pronto quando:** política de retenção ativa e testada; trilha completa; consentimento registrado; custo e tempo por unidade visíveis; backup restaurado com sucesso em ambiente limpo; PR mesclado.

---

## 11. Fase final — Implantação em VPS genérica e aceite global

### 11.1 Checklist da VPS (neutro de provedor)

- [ ] VPS Linux atual com **Docker Engine 27+** e **Compose v2** como únicas dependências.
- [ ] Firewall liberando só **80/443** (entrada HTTPS) e **SSH restrito** (por chave, sem senha, de preferência por IP permitido); nenhuma outra porta pública (banco, backend, n8n e `telegram-bot-api` ficam na rede interna).
- [ ] DNS apontando para a VPS; HTTPS emitido e renovado pelo Caddy.
- [ ] Arquivo `.env` de produção criado **na VPS**, fora do Git, com todos os segredos (chaves de cifragem do CPF e do HMAC, tokens, chaves de IA, token de serviço do n8n).
- [ ] Imagens com versão fixada; mesmas definições do ambiente local, variando só a configuração.
- [ ] Deploy pelo workflow manual com ambiente protegido por aprovação, usando SSH com chave dedicada guardada como segredo do repositório.
- [ ] Backup automatizado em destino **fora da VPS**, cifrado, com restauração testada e as chaves guardadas à parte.
- [ ] Webhook do Telegram apontando para o n8n e perfil do bot configurado (monograma FB).
- [ ] Verificações pós-implantação: health check, login do professor e do administrador, um áudio de ponta a ponta com as gravações fictícias do cliente, PDF abrindo com autenticação, restauração de backup.
- [ ] Plano de reversão: comando para voltar à imagem anterior e para restaurar o último backup.

### 11.2 Aceite global (Apêndice A do handoff, atualizado)

| # | Critério | Feature |
|---|---|---|
| 1 | Administrador cadastra professor, recebe código de 8 caracteres válido por 7 dias; e-mail duplicado recusado | 005 |
| 2 | Administrador reemite código; lista mostra se há Telegram vinculado | 005 |
| 3 | Administrador cadastra aluno com CPF único, cifrado e nunca exibido; menor exige responsável; associa 0..N professores; o professor cadastra aluno seu e não acessa aluno de outro; aluno e responsável não têm acesso | 005 |
| 4 | Professor vincula o Telegram e entra na Web com o mesmo código; sessão e bloqueio por tentativas conforme regras | 001 |
| 5 | Professor só vê alunos associados (403) e relatórios próprios (404) | 001 |
| 6 | Registro de aula por voz no Telegram e por gravação/upload na Web; áudio inválido não cria registro; áudio não é armazenado | 001 |
| 7 | IA estrutura, pergunta antes do PDF se faltar informação, versão e PDF por alteração, aprovação com versão vigente e irreversível | 001 |
| 8 | Relatório iniciado em um canal é retomado ou descartado no outro | 001 |
| 9 | Semestral: menu de semestres, contagem de aprovados, bloqueio sem dados, 10 seções, PDF, alteração, aprovação | 001 |
| 10 | Histórico do professor só com aprovados; administrador vê qualquer aluno, somente leitura | 001, 006 |
| 11 | Bot funciona de ponta a ponta pelo n8n; mesma experiência de antes | 004 |
| 12 | Administração disponível na Web e no Telegram | 005, 006 |
| 13 | Retenção, eliminação a pedido, consentimento, auditoria e backup restaurável | 007 |
| 14 | Código em inglês; texto ao usuário e logs em português; guardião verde | 003 |
| 15 | Erros sempre no formato padrão, sem detalhes internos | todas |

### 11.3 Acompanhamento

| Etapa | Branch | Spec | Clarify | Plan | Tasks | Analyze | Implement | Converge | Review | PR | Merge |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Fase 0 | `chore/auditoria-001-002` | — | — | — | — | — | ☐ | ☐ | — | ☐ | ☐ |
| Fase 1 | `docs/constitution-v2.0.0` | — | — | — | — | — | — | — | ☐ | ☐ | ☐ |
| 003 Inglês | `003-migracao-para-ingles` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 004 n8n | `004-orquestrador-n8n` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 005 Administração cadastros | `005-admin-registrations` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 006 Administração visão | `006-admin-school-overview` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 007 LGPD e operação | `007-privacidade-operacao` | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Implantação | — | — | — | — | — | — | — | — | — | — | ☐ |

> Os nomes de branch são os que o `speckit-specify` costuma gerar a partir da descrição; ajuste conforme o que ele criar de fato.

---

## 12. Apêndice

### A. Mapa do handoff (seção 19) para as features reais

| Handoff | Assunto | Onde foi/será entregue |
|---|---|---|
| 001 | Fundação, identidade e acesso | Spec 001 (professor) + **005** (administrador) |
| 002 | Cadastros da administração | **005** |
| 003 | Núcleo do relatório de aula (IA) | Spec 001 |
| 004 | Portal web do professor | Spec 001 |
| 005 | Canal Telegram (registro de aula) | Specs 001 e 002 + **004** (n8n) |
| 006 | Relatório semestral | Spec 001 |
| 007 | Histórico e documentos | Spec 001 (professor) + **006** (escola) |
| 008 | Design system e identidade | Spec 001 (+ cada feature nova segue o design) |
| 009 | Privacidade, retenção e operação | **007** |
| — | Idioma do código e VPS genérica | **Fase 1** e **003** |

### B. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| A migração para inglês (003) é grande e transversal | Fatias que compilam e passam a cada passo; renomeação por ferramenta; teste guardião; nenhuma mudança de comportamento |
| Expectativa de texto de teste quebra durante a migração | Parar e relatar; não ajustar teste de texto |
| A administração (005) é grande | Dividir por prioridade de user story em PRs separados, mantendo paridade em cada um |
| Administração no Telegram sem autenticação forte | Decisão D2 antes do `plan`; confirmação explícita em ações destrutivas; `/security-review` obrigatório |
| Prazos de retenção sem validação jurídica | Validar com o cliente antes do `plan` da 007 |
| Specs antigas citando nomes em português | Nota de histórico no topo; não reescrever |
| Dependência de provedor de IA (custo e privacidade) | Provedores já são plugáveis por configuração; inventário de tratamento na 007 |
| `.env` locais antigos quebram após renomear variáveis (003) | Documentar a troca no README e no PR; regerar o `.env` a partir do `.env.example` |

### C. Fora de escopo deste plano

Evoluções de produto (data da aula editável, edição manual de campos, retificação após aprovação, envio às famílias, geração semestral automática, WhatsApp, financeiro, app nativo). Não há feature para elas; o Princípio VII exige justificativa e aprovação do dono do produto antes de qualquer uma entrar.

### D. Comandos do Spec Kit (resumo)

| Comando | Para quê |
|---|---|
| `/speckit-constitution` | Criar ou emendar a constituição |
| `/speckit-specify` | Criar a spec e a branch da feature |
| `/speckit-clarify` | Resolver ambiguidades com perguntas dirigidas |
| `/speckit-checklist` | Checklist de qualidade dos requisitos |
| `/speckit-plan` | Plano técnico, pesquisa, modelo de dados, contratos |
| `/speckit-tasks` | Lista de tarefas ordenada por dependência |
| `/speckit-analyze` | Análise de consistência entre spec, plano e tasks (não altera) |
| `/speckit-implement` | Executar as tasks |
| `/speckit-converge` | Comparar código com spec/plano/tasks e acrescentar o que falta |
| `/speckit-taskstoissues` | Converter tasks em issues do GitHub (opcional) |
