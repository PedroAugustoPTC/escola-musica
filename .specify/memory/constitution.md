<!--
Sync Impact Report
- Version change: 2.1.0 → 2.2.0
- Rationale: MINOR — exceção deliberada e delimitada à paridade do Princípio I, por decisão do dono do
  produto (2026-10-20): as funções do **Administrador** (acesso e cadastro de professores, alunos, associações,
  administradores e códigos de acesso, e as futuras consultas de administração) existem **somente na Web**; o
  Telegram é canal do **Professor**, nunca do Administrador. A paridade entre Web e Telegram continua valendo,
  integral, para tudo o que o Professor faz. Nenhum princípio é removido; a regra de dependência, a separação de
  responsabilidades (Princípio II) e a autorização no backend não mudam. Motivo de produto: o administrador
  autentica com senha e sessão curta na Web; vincular uma conta de mensageria a um papel que enxerga a escola
  inteira ampliaria a superfície de ataque sem ganho para o uso real (a secretaria trabalha no computador).
- Modified principles: I. Domínio Independente de Canal e de Fornecedor (nova regra de exceção; regra de paridade
  e justificativa ajustadas)
- Modified sections: Restrições Adicionais ("Decisões de produto vinculantes": administração deixa de ser
  "nos dois canais"; passa a ser do Administrador só Web e do Professor nos dois); Fluxo de Desenvolvimento e
  Quality Gates (item de revisão de paridade ressalva a exceção)
- Added sections: nenhuma
- Removed sections: nenhuma
- Princípios II a IX: inalterados
- Templates verificados (`.specify/templates/`): `plan-template.md` (Constitution Check lê esta constituição em
  tempo de execução), `spec-template.md`, `tasks-template.md`, `checklist-template.md` e
  `constitution-template.md` — nenhum cita paridade nem administração; nenhum requer alteração.
- Artefatos dependentes (fora do escopo desta emenda, atualizados nas suas próprias branches):
  `specs/005-admin-registrations-access/spec.md` (já reflete a decisão), `PLANO_EXECUCAO_SPECKIT.md` (seções 4,
  8, 9 e 10: administrador sem Telegram; features 006 e 007 só Web para o administrador), `frontend/CLAUDE.md` e
  `CLAUDE.md` da raiz (nesta mesma PR, para a referência de versão e a nota de paridade).
- Aprovação: pedida e dada pelo dono do produto no `/speckit-clarify` da feature 005 (2026-10-20); o merge do PR a
  registra.
- Deferred items: nenhum

Histórico — v2.1.0 (2026-10-15)
- Version change: 2.0.0 → 2.1.0
- Rationale: MINOR — quitação da dívida do Princípio VI pela feature 003 (migração do código existente para
  inglês) e expansão material do princípio: (a) a **regra de transição** deixa de valer, porque o código, o banco,
  os contratos, os scripts, o CI e o frontend já estão em inglês, e o bloqueio "nenhuma feature nova pode ser
  iniciada" cai; (b) o glossário do domínio ganha um complemento oficial em forma de dados,
  `.specify/memory/glossary.json`, que alimenta o teste guardião de idioma (Java e TypeScript) e é parte da
  regra; (c) uma quinta categoria de exceção, "nomes próprios e material de referência", é ratificada; (d) a lista
  de sufixos padronizados ganha `*Entity`, `*Command`, `*Filter`, `*Converter`, `*Codec` e `*Config`, que a
  migração encontrou em uso e que o glossário já aceita.
- Modified principles: VI. Nomenclatura Previsível, Linguagem Ubíqua e Idioma (sufixos; exceções; regra de
  transição reescrita como registro da quitação; guardião de idioma)
- Modified sections: nenhuma além do Princípio VI e do cabeçalho de versão
- Added sections: nenhuma (o "Guardião de idioma" é subparte do Princípio VI)
- Removed sections: nenhuma; a **regra de transição** deixa de ser norma e fica como registro histórico
- Princípios I a V e VII a IX: inalterados
- Histórico: os textos das versões anteriores foram preservados, inclusive o item da 2.0.0 que diz que a
  feature 003 "ainda NÃO existe spec", que era verdade na época; o número 003 ficou com a migração, e a feature
  que traz o n8n passou a ser a 004 (documentos da spec 002 corrigidos nessa PR).
- Templates verificados (`.specify/templates/`): `plan-template.md`, `spec-template.md`, `tasks-template.md`,
  `checklist-template.md` e `constitution-template.md` — nenhum cita idioma de identificadores, sufixos,
  Hostinger nem a regra de transição; nenhum requer alteração. `.specify/memory/.constitution-template.json`
  guarda só o hash do modelo da constituição, que não mudou.
- Artefatos dependentes atualizados na mesma PR: todos os `CLAUDE.md` (raiz, `.specify`, `backend`,
  `backend/core`, `backend/application`, `frontend`, `infra`, `.github`, `specs`, `assets`,
  `portal-do-professor-design-system`), `README.md` da raiz e de `infra/`, `specs/001-*` e `specs/002-*` (nota de
  histórico no topo) e `PLANO_EXECUCAO_SPECKIT.md` (bloco de comandos da seção 3.2).
- Aprovação: a emenda exige a aprovação do dono do produto (Governança); ela é pedida na descrição do PR da
  feature 003 e o merge do PR a registra.
- Deferred items: nenhum

Histórico — v2.0.0 (2026-10-10)
- Version change: 1.4.0 → 2.0.0
- Rationale: MAJOR — redefinição incompatível do Princípio VI: identificadores de código, nomes de
  arquivo e pasta, tabelas e colunas, rotas e campos de API, códigos de erro, chaves de catálogos,
  variáveis de ambiente, nomes de scripts e de workflows de CI e classes CSS passam de português do
  Brasil sem acentos para INGLÊS; textos mostrados ao usuário e mensagens de log passam a ser
  explicitamente em português do Brasil acentuado; documentação, specs, planos, tasks, comentários,
  commits e Pull Requests permanecem em português. Sufixos padronizados passam a ser em inglês e o
  princípio ganha um glossário de domínio pt→en. Segunda mudança, de menor impacto: a hospedagem de
  produção deixa de ser "VPS da Hostinger" e passa a ser "uma VPS Linux genérica, de qualquer
  provedor", com exigências neutras de provedor.
- Modified principles: VI. Nomenclatura Previsível e Linguagem Ubíqua → VI. Nomenclatura Previsível,
  Linguagem Ubíqua e Idioma (nova regra de idioma; sufixos e exemplos em inglês; glossário do
  domínio; regra de transição)
- Modified sections: Restrições Adicionais ("Containerização e implantação": VPS da Hostinger →
  VPS Linux genérica, com exigências neutras de provedor)
- Added sections: nenhuma (o "Glossário do domínio" e a "Regra de transição" são subpartes do
  Princípio VI)
- Removed sections: nenhuma
- Princípios I a V, VII, VIII e IX: inalterados
- Histórico: os textos das versões anteriores foram preservados; só a menção a Hostinger da v1.2.0
  recebeu uma nota de que foi superada, para não induzir a erro.
- Templates verificados (`.specify/templates/`): `plan-template.md` (Constitution Check lê esta
  constituição em tempo de execução, sem texto fixo sobre idioma ou provedor), `spec-template.md`,
  `tasks-template.md`, `checklist-template.md` e `constitution-template.md` — nenhuma menção a
  idioma de identificadores, sufixos ou Hostinger; nenhum requer alteração.
- Artefatos dependentes a revisar (fora do escopo desta emenda):
  - Feature 003 (migração do código existente para inglês): ainda NÃO existe spec; DEVE ser criada
    (`/speckit-specify`) e cobrir backend, frontend, banco (tabelas, colunas e valores de
    enumerações persistidas, com migração de dados), contratos e rotas das APIs, workflows do n8n,
    ações e dados de botões do Telegram, chaves de catálogos de mensagens, variáveis de ambiente,
    scripts e workflows de CI e classes CSS. Até ela ser mesclada, nenhuma feature nova pode ser
    iniciada (regra de transição do Princípio VI).
  - Atualização dos `CLAUDE.md`, que ainda afirmam "código e identificadores em português do Brasil,
    sem acentos": o da raiz, `.specify/CLAUDE.md`, `backend/CLAUDE.md`, `backend/nucleo/CLAUDE.md`,
    `backend/aplicacao/CLAUDE.md`, `frontend/CLAUDE.md`, `infra/CLAUDE.md`, `specs/CLAUDE.md`,
    `.github/CLAUDE.md`, `assets/CLAUDE.md` e `portal-do-professor-design-system/CLAUDE.md`
    (revisar cada um e ajustar o que citar idioma, nomenclatura ou Hostinger).
  - Menções a "VPS da Hostinger" (sem requisito real de provedor) em `infra/`
    (`README.md`, `compose.yaml`, `compose.prod.yaml`, `scripts/implantar.sh`, `CLAUDE.md`),
    `.github/` (`workflows/implantar.yml`, `CLAUDE.md`), `specs/001-fluxo-relatorios/`
    (`plan.md`, `research.md`, `quickstart.md`, `tasks.md`), `specs/002-api-interna-canal-telegram/plan.md`
    e `PLANO_EXECUCAO_SPECKIT.md`.
- Deferred items: nenhum

Histórico — v1.4.0 (2026-10-02)
- Version change: 1.3.0 → 1.4.0
- Rationale: MINOR — expansão material do Princípio II e de "Restrições Adicionais": o n8n passa a
  ser admitido, e SOMENTE, como transporte do canal Telegram (recebe o update, repassa ao backend e
  executa as ações devolvidas por ele). Reverte a decisão R-04 de
  `specs/001-fluxo-relatorios/research.md` ("sem n8n; webhook direto no backend"). A proibição de
  regra de negócio, autorização e persistência nos orquestradores é mantida e agora explicitada
  para o n8n; todo o estado de conversa continua persistido no backend. Nenhum princípio removido.
- Modified principles: II. Fonte Única da Verdade (título mantido; n8n explicitado como transporte
  do Telegram, com limites do que ele pode e não pode fazer)
- Modified sections: Restrições Adicionais ("Containerização e implantação": serviço n8n em
  contêiner, imagem com versão fixada, no mesmo comando único, local e produção; novo item
  "Orquestrador de canal (n8n)": workflows versionados como código, sem segredo nem ID de
  credencial nos JSON)
- Added sections: nenhuma
- Removed sections: nenhuma
- Princípios I e III–IX: inalterados
- Complexity Tracking: a linha do contêiner `telegram-bot-api` (`plan.md`) permanece válida; o
  servidor local da API de bots continua necessário para o limite de 50 MB (FR-015) e não é
  substituído pelo n8n.
- Templates verificados (`.specify/templates/`): `plan-template.md` (Constitution Check lê esta
  constituição em tempo de execução), `spec-template.md`, `tasks-template.md`,
  `checklist-template.md` e `constitution-template.md` — sem referência a n8n ou a orquestrador;
  nenhum requer alteração.
- Artefatos dependentes a revisar (fora do escopo desta emenda): `specs/001-fluxo-relatorios/`
  (`research.md` R-04 e Alternativas consideradas, `plan.md` Summary/Technical Context/estrutura e
  Complexity Tracking, `quickstart.md`, `tasks.md`) e `infra/` (`compose.yaml`, `compose.prod.yaml`,
  scripts e READMEs) assumem "sem n8n" e deverão ser atualizados pela feature que reintroduzir o
  serviço.
- Deferred items: nenhum

Histórico — v1.3.0 (2026-09-30)
- Version change: 1.2.0 → 1.3.0
- Rationale: MINOR — expansão material do Princípio I: Web e Telegram passam a ser canais de
  entrada equivalentes, com paridade funcional (tudo o que se faz em um se faz no outro, pelos
  mesmos casos de uso). A decisão de produto que restringia relatório semestral, histórico e
  administração à Web é substituída pela paridade. Nenhum princípio removido; a regra de
  dependência e as demais garantias do Princípio I permanecem.
- Modified principles: I. Domínio Independente de Canal e de Fornecedor (título mantido; regras de
  paridade entre canais e justificativa ampliadas)
- Modified sections: Restrições Adicionais ("Decisões de produto vinculantes": exclusividade da
  Web substituída por paridade; aceitação de áudio estendida aos dois canais); Fluxo de
  Desenvolvimento e Quality Gates (novo item de revisão de paridade)
- Added sections: nenhuma
- Removed sections: nenhuma
- Templates requiring follow-up: nenhum (`plan-template.md` lê esta constituição no
  "Constitution Check")
- Artefatos dependentes: `specs/001-fluxo-relatorios/spec.md` ainda restringe semestral e
  histórico à Web e deve ser revisada à luz da paridade.
- Deferred items: nenhum

Histórico — v1.2.0 (2026-09-30)
- Version change: 1.1.0 → 1.2.0
- Rationale: MINOR — expansão material de "Restrições Adicionais": o item "Ambiente reproduzível"
  vira "Containerização e implantação" (todos os serviços em contêiner, localmente, nos testes e
  em produção; implantação definitiva em VPS da Hostinger — superado na v2.0.0, que exige apenas
  uma VPS Linux genérica, de qualquer provedor). "Stack agnóstica" passa a abrir
  exceção apenas para a hospedagem de produção. Nenhum princípio removido ou redefinido.
- Modified principles: nenhum (Princípios I–IX inalterados)
- Modified sections: Restrições Adicionais ("Ambiente reproduzível" → "Containerização e
  implantação"; "Stack agnóstica" ajustado)
- Added sections: nenhuma
- Removed sections: nenhuma
- Templates requiring follow-up: nenhum
- Deferred items: nenhum

Histórico — v1.1.0 (2026-09-30)
- Version change: 1.0.0 → 1.1.0
- Rationale: MINOR — expansão material do Princípio VIII e do Fluxo de Desenvolvimento: ao concluir
  alterações, commit e abertura de PR passam a ser obrigatórios; merge só após revisão e aprovação
  humanas, sem auto-merge e sem o autor (humano ou agente de IA) mesclar o próprio PR. Nenhum
  princípio removido ou redefinido de forma incompatível.
- Modified principles: VIII. Fluxo de Trabalho (título mantido; regras ampliadas)
- Added sections: nenhuma (novo item em "Fluxo de Desenvolvimento e Quality Gates")
- Removed sections: nenhuma
- Templates requiring follow-up: nenhum
- Deferred items: nenhum

Histórico — v1.0.0 (2026-09-30)
- Version change: (template sem versão) → 1.0.0
- Rationale: primeira ratificação da constituição do sistema definitivo. Base: seção 18 do
  HANDOFF_MVP_Relatorios_Escola_Musica.md (9 princípios) e constituição do MVP `mvp-relatorios`
  v1.1.0, adaptada para não depender de stack.
- Modified principles: n/a (documento novo). Correspondência com o MVP v1.1.0:
  - MVP I. Clean Architecture → I. Domínio Independente de Canal e de Fornecedor
  - MVP IV. Separação n8n / Backend / Banco → II. Fonte Única da Verdade
  - (novo, da seção 18) → III. Humano no Controle da IA
  - MVP V. Segurança e Privacidade (LGPD) → IV. Privacidade desde o Design
  - MVP III. Cobertura 90% → V. Qualidade Obrigatória
  - MVP II. Nomenclatura → VI. Nomenclatura Previsível e Linguagem Ubíqua (código em pt-BR)
  - MVP VI. Simplicidade e Foco no MVP → VII. Simplicidade e Foco
  - MVP "Fluxo de Desenvolvimento" (regra de branch) → VIII. Fluxo de Trabalho
  - (novo, da seção 18) → IX. Identidade Visual Consistente e Acessível
- Added sections: Core Principles (I–IX), Restrições Adicionais, Fluxo de Desenvolvimento e
  Quality Gates, Governance
- Removed sections: "Stack Tecnológica" do MVP (substituída por Restrições Adicionais agnósticas)
- Templates requiring follow-up: nenhum alterado nesta etapa; `plan-template.md` lê esta
  constituição em tempo de execução no "Constitution Check".
- Deferred items: nenhum placeholder pendente.
-->

# Sistema de Relatórios Pedagógicos por Voz — Instituto Musical Felipe Blaft Constitution

## Core Principles

### I. Domínio Independente de Canal e de Fornecedor (Inegociável)

- As regras de negócio DEVEM viver no núcleo da aplicação, organizado nas camadas
  domínio → casos de uso → adaptadores.
- As dependências DEVEM apontar sempre para dentro: o domínio NÃO DEVE depender de casos de uso,
  adaptadores, frameworks, banco de dados ou bibliotecas de terceiros; os casos de uso NÃO DEVEM
  depender de adaptadores.
- Cada caso de uso DEVE representar uma única ação de negócio e NÃO DEVE conhecer detalhes de
  transporte (HTTP, Telegram, webhook) nem de persistência.
- Canais (Telegram, Web e futuros, como WhatsApp) DEVEM ser adaptadores de entrada que apenas
  traduzem requisições para os mesmos casos de uso. Adaptadores de entrada NÃO DEVEM conter regra
  de negócio.
- Web e Telegram DEVEM ser canais de entrada equivalentes (paridade funcional) para o **Professor**:
  toda funcionalidade do Professor disponível em um deles DEVE estar disponível no outro, executando
  os mesmos casos de uso com as mesmas regras de autorização, validação e versionamento.
- **Exceção, por decisão do dono do produto**: as funções do **Administrador** (acesso, cadastro e
  manutenção de professores, alunos, associações, administradores e códigos de acesso, e as consultas
  administrativas) existem **somente na Web**. O Telegram é canal do Professor e NÃO DEVE vincular,
  autenticar nem oferecer menu, comando ou botão ao Administrador; ações administrativas pedidas por
  ele devem receber negativa neutra. Os casos de uso continuam independentes de canal (esta regra
  limita quais adaptadores de entrada os expõem). Estender o Telegram ao Administrador exige emenda.
- Um canal PODE adaptar a forma de interação ao seu meio (ex.: gravação no navegador ↔ mensagem
  de voz; formulário ↔ conversa guiada ou botões; download de PDF ↔ documento enviado no chat),
  mas NÃO DEVE restringir, ampliar ou alterar o resultado de negócio obtido no outro canal.
- Um trabalho iniciado em um canal DEVE poder ser consultado e continuado no outro.
- Canais futuros (ex.: WhatsApp) NÃO estão sujeitos à paridade automaticamente; o escopo de cada
  um DEVE ser definido em spec própria (Princípio VII).
- Provedores externos (transcrição de áudio, LLM, geração de PDF, armazenamento de arquivos,
  mensageria) DEVEM ficar atrás de portas definidas pelo núcleo. Trocar um provedor DEVE exigir
  apenas um novo adaptador e configuração, sem alteração no domínio nem nos casos de uso.
- Nenhum artefato do núcleo DEVE citar nome de fornecedor, SDK ou modelo de IA específico.
- Violação da regra de dependência encontrada em revisão é bloqueante.

**Justificativa**: o mesmo ciclo de vida de relatório precisa funcionar em qualquer canal e
sobreviver à troca de stack e de fornecedores de IA, que são as partes mais voláteis do produto.
Com a paridade entre Web e Telegram, o professor escolhe o canal mais conveniente no momento sem
perder nenhuma funcionalidade. A administração, feita por poucas pessoas no computador e com poder
sobre a escola inteira, fica na Web, onde há senha, sessão curta e confirmação em tela.

### II. Fonte Única da Verdade

- Somente o backend (núcleo da aplicação) DEVE escrever no banco de dados.
- Orquestradores, automações e integrações de canal NÃO DEVEM conter regra de negócio,
  autorização ou validação de domínio e NÃO DEVEM persistir dados de domínio; todo dado que
  entra por eles DEVE passar por um caso de uso do backend.
- O n8n é admitido SOMENTE como transporte do canal Telegram: recebe o update do Telegram, o
  repassa ao backend e executa as ações (ex.: enviar mensagem, botões, documento) que o backend
  devolver. Ele NÃO DEVE decidir o que responder, interpretar a conversa, autorizar, validar,
  chamar provedores de IA nem gravar dados de domínio; qualquer outro uso do n8n exige emenda.
- O estado da conversa de qualquer canal DEVE ser persistido no backend; o orquestrador DEVE poder
  ser reiniciado sem perda de estado.
- Identificadores de canal (ex.: ID de usuário do Telegram) NÃO DEVEM substituir autenticação e
  autorização, que são sempre decididas pelo backend.

**Justificativa**: lógica espalhada em workflows é difícil de testar, versionar e auditar; um único
ponto de escrita garante consistência entre canais e permite continuar em um canal o que começou
em outro.

### III. Humano no Controle da IA (Inegociável)

- Nenhum relatório gerado por IA DEVE se tornar oficial (entrar no histórico do aluno) sem
  aprovação explícita do professor.
- A aprovação DEVE se referir a uma versão identificada; aprovar NÃO DEVE ser possível se a versão
  apresentada ao professor não for a versão vigente.
- Toda alteração (pedida pelo professor ou gerada pela IA) DEVE criar uma nova versão rastreável;
  versões anteriores NÃO DEVEM ser sobrescritas.
- A IA NÃO DEVE inventar informação ausente da fonte (transcrição, respostas do professor ou
  relatórios aprovados usados como base); o que faltar DEVE ficar vazio ou virar pergunta ao
  professor.

**Justificativa**: o relatório é um documento pedagógico oficial sobre um aluno; a confiança do
professor, métrica central do produto, depende de ele ter a palavra final sobre cada versão.

### IV. Privacidade desde o Design (LGPD, Menores de Idade)

- Os dados enviados a provedores de IA DEVEM ser o mínimo necessário para a tarefa; dados
  identificadores que a tarefa não exige NÃO DEVEM ser enviados.
- O áudio original NÃO DEVE ser retido após o processamento.
- CPF DEVE ser armazenado cifrado.
- Segredos (tokens, chaves de API, credenciais) NÃO DEVEM estar no código versionado; DEVEM vir do
  ambiente ou de um cofre de segredos.
- Logs NÃO DEVEM conter dados sensíveis (áudio, transcrição completa, CPF, dados pessoais do
  aluno) além do estritamente necessário para depuração.
- Um professor DEVE acessar somente dados dos próprios alunos; essa verificação DEVE acontecer no
  backend em todo caso de uso que lê ou altera dados de aluno.
- Toda comunicação externa DEVE usar HTTPS. Serviços internos (banco, orquestrador, APIs internas)
  NÃO DEVEM ficar expostos publicamente.

**Justificativa**: o sistema trata dados pedagógicos de crianças e adolescentes; um incidente gera
risco legal (LGPD) e perda de confiança que inviabiliza o produto.

### V. Qualidade Obrigatória (Inegociável)

- A regra de negócio (domínio e casos de uso) DEVE ter no mínimo 90% de cobertura de testes
  unitários, medida por linha e por branch.
- Os testes unitários DEVEM cobrir caminhos de sucesso, erro e borda (ex.: dados incompletos,
  resposta inválida da IA, aluno de outro professor).
- APIs consumidas por outros componentes (ex.: orquestrador de canal, frontend) DEVEM ter testes de
  contrato.
- Chamadas reais a provedores externos NÃO DEVEM contar para a cobertura unitária; nos testes
  unitários elas DEVEM ser substituídas por dublês.
- O CI DEVE bloquear o merge se a cobertura ficar abaixo do limite ou se qualquer teste falhar.
- Nenhuma mudança de comportamento DEVE ser mesclada sem os testes correspondentes, inclusive
  correções pequenas.

**Justificativa**: saídas de IA não são determinísticas e alimentam documentos oficiais; testes
rigorosos no núcleo são a rede de segurança contra regressões silenciosas.

### VI. Nomenclatura Previsível, Linguagem Ubíqua e Idioma

**Idioma por tipo de artefato**

- Em **inglês** DEVEM ser escritos: pacotes, módulos, tipos, funções, variáveis, constantes, nomes
  de arquivo e de pasta, tabelas e colunas do banco, valores de enumerações persistidas, rotas e
  campos das APIs e dos contratos, códigos de erro, ações e dados de botões do Telegram, chaves de
  catálogos de mensagens, variáveis de ambiente, nomes de scripts e de workflows de CI, classes e
  variáveis CSS (ex.: `LessonReport`, `lesson_report`, `/lesson-reports`, `LESSON_NOT_FOUND`).
- Em **português do Brasil, com acentuação correta**, DEVEM ser escritos: todo texto mostrado ao
  usuário (telas Web, mensagens e botões do bot, PDFs e e-mails, se houver) e todas as mensagens de
  log. As palavras-chave que o usuário digita no bot (ex.: "aprovar", "cancelar", "início")
  continuam em português.
- Em **português** DEVEM ser escritos: documentação, specs, planos, tasks, comentários de código e
  mensagens de commit e de Pull Request.

**Linguagem ubíqua e sufixos**

- Os nomes DEVEM usar a linguagem ubíqua do domínio, nos termos canônicos do glossário abaixo;
  NÃO DEVEM existir sinônimos para o mesmo conceito.
- Os nomes DEVEM indicar camada e responsabilidade por meio dos sufixos padronizados:
  - Entidades de domínio: substantivo de negócio, sem sufixo técnico (ex.: `Student`, `Lesson`).
  - `*UseCase`: uma ação de negócio (ex.: `ApproveLessonReportUseCase`).
  - `*Controller`: adaptador de entrada (ex.: `LessonReportController`).
  - `*Service`: serviço de aplicação que apoia casos de uso sem substituí-los.
  - `*Repository`: porta de persistência (ex.: `StudentRepository`).
  - `*Port`: porta de saída para provedor externo (ex.: `TranscriptionPort`, `LlmPort`).
  - `*Adapter`: implementação concreta de uma porta ou repositório
    (ex.: `StudentRepositoryAdapter`).
  - `*Mapper`: conversão entre objetos de transporte e de domínio.
  - `*Dto`, `*Request`, `*Response`: objetos de transporte na borda.
  - `*Entity`: entidade de persistência (mapeamento JPA), quando distinta da entidade de domínio.
  - `*Command`: comando operacional ou de linha de comando (ex.: `SeedCommand`).
  - `*Filter`: filtro de requisição na borda (ex.: de sessão ou de segurança).
  - `*Converter`: conversão de tipo de e para o banco ou o transporte.
  - `*Codec`: codificação e decodificação de um formato persistido ou trafegado.
  - `*Config`: classe de configuração (beans e propriedades).
- Exceções permitidas, e somente estas: palavras reservadas da linguagem, APIs de terceiros, nomes
  exigidos por ferramentas ou frameworks, siglas consagradas (PDF, LLM, HTTP, DTO) e **nomes próprios e
  material de referência**: o nome da organização no pacote Java (`br.com.felipeblaft`), o prefixo de marca
  dos arquivos de identidade visual (`fb-`), o domínio público do portal, as pastas de material de referência
  que não são código (`assets/`, `portal-do-professor-design-system/`, `.claude/`), os nomes das pastas de
  feature em `specs/` (ex.: `001-fluxo-relatorios`) e os valores já persistidos que não podem mudar sem
  invalidar dados (os prefixos de domínio do HMAC dos códigos de acesso). Texto mostrado ao usuário e
  palavras digitadas por ele (ex.: o nome sugerido ao baixar o PDF, a palavra de confirmação do
  `local.sh clean`) seguem em português, como já diz esta seção.
- Toda exceção DEVE estar registrada em `.specify/memory/glossary.json`, com o motivo e a categoria; exceção
  que não casa com nada no código é "morta" e reprova o guardião.

**Glossário do domínio (pt → en)**

Os termos abaixo são os canônicos; o equivalente em inglês é o único nome admitido no código.

| Termo do domínio (pt) | Termo canônico (en) |
|---|---|
| Professor | `Teacher` |
| Aluno | `Student` |
| Aula | `Lesson` |
| RelatorioAula | `LessonReport` |
| RelatorioSemestral | `SemesterReport` |
| VinculoTelegram | `TelegramLink` |
| Administrador | `Administrator` |
| CodigoDeAcesso | `AccessCode` |
| Associação professor-aluno | `TeacherStudentAssignment` |
| Rascunho | `Draft` |
| Versão | `Version` |
| Aprovação | `Approval` |

**Guardião de idioma**

- Um teste guardião DEVE rodar no CI e reprovar o build quando uma palavra em português aparecer em
  identificador, nome de arquivo ou de pasta, tabela, coluna, chave de catálogo, variável de ambiente, YAML,
  script, workflow ou classe CSS. Ele existe em Java (código e artefatos de dados e configuração) e em
  TypeScript (frontend), consome `.specify/memory/glossary.json` e NÃO examina texto mostrado ao usuário,
  logs, comentários nem documentação.
- Termo novo DEVE entrar no `glossary.json`, com um único equivalente em inglês, **antes** de ser usado. Não existe
  lista de exclusão: o que não pode ser traduzido é uma exceção documentada (acima).

**Regra de transição (quitada)**

- Até a versão 2.0.0, o código existente estava em português e constituía dívida registrada em relação a este
  princípio, e nenhuma feature nova podia ser iniciada enquanto ela não fosse quitada.
- A dívida foi **quitada pela feature 003** (`specs/003-migrate-code-to-english/`), em sete fatias, sem mudança
  de comportamento. A partir da 2.1.0 não há mais regra de transição nem bloqueio de feature nova: todo código
  novo segue este princípio desde o primeiro commit.

**Justificativa**: nomes previsíveis tornam a arquitetura autoexplicativa e impedem que regra de
negócio escape para a camada errada. Identificadores em inglês seguem o idioma das linguagens,
frameworks e ferramentas usados, evitando a mistura de idiomas no código; o texto voltado a
pessoas (usuários, equipe e logs) permanece em português do Brasil, idioma de quem lê.

### VII. Simplicidade e Foco

- Novas integrações, abstrações ou camadas NÃO DEVEM ser criadas sem uma spec que as exija.
- Itens de roadmap (ex.: geração semestral automática, WhatsApp, financeiro, app mobile nativo)
  NÃO DEVEM entrar em desenvolvimento sem justificativa registrada e aprovação do dono do produto.
- Entre soluções que atendem à spec, DEVE ser escolhida a mais simples; complexidade adicional
  DEVE ser justificada no plano da feature.

**Justificativa**: o valor do produto está em o professor economizar tempo; complexidade prematura
atrasa entregas sem aumentar esse valor.

### VIII. Fluxo de Trabalho

- Antes de iniciar uma feature ou spec, a `main` local DEVE ser atualizada com a remota.
- Cada feature ou spec DEVE ter uma branch dedicada criada a partir da `main` atualizada; código de
  implementação NÃO DEVE ser commitado diretamente na `main` nem na branch de outra feature.
- Ao concluir as alterações de uma feature, spec ou emenda, elas DEVEM ser commitadas na branch
  dedicada e um Pull Request para a `main` DEVE ser aberto em seguida, sem esperar outra
  solicitação.
- Toda integração na `main` DEVE ocorrer por Pull Request com testes e revisão (ver "Fluxo de
  Desenvolvimento e Quality Gates").
- Nenhum Pull Request DEVE ser mesclado antes de ser revisado e aprovado por um revisor humano.
  Quem produziu as alterações, incluindo agentes de IA, NÃO DEVE mesclar o próprio Pull Request
  nem habilitar merge automático.
- Artefatos de automação (workflows de orquestração, pipelines de CI, scripts de infraestrutura)
  DEVEM ser versionados no repositório.

**Justificativa**: commitar e abrir o PR logo após as alterações deixa o trabalho visível e pronto
para revisão; a revisão humana obrigatória antes do merge mantém a `main` íntegra e rastreável,
inclusive nas automações.

### IX. Identidade Visual Consistente e Acessível

- Interfaces Web, documentos gerados (PDFs) e mensagens do bot DEVEM seguir a identidade visual
  do Instituto Musical Felipe Blaft.
- As interfaces DEVEM atender a WCAG 2.1 nível AA.
- As interfaces DEVEM ser projetadas mobile-first; todo fluxo DEVE funcionar em tela de celular.

**Justificativa**: o professor usa o sistema no dia a dia, muitas vezes pelo celular logo após a
aula; uma experiência coerente, acessível e com a marca da escola sustenta a confiança e o uso
recorrente.

## Restrições Adicionais

- **Stack agnóstica**: esta constituição NÃO DEVE fixar linguagem, framework, banco de dados ou
  fornecedor de IA. As escolhas de stack DEVEM ser registradas e justificadas no plano
  (`/speckit.plan`) e DEVEM respeitar os Princípios I a IX. A única exceção é a hospedagem de
  produção, definida em "Containerização e implantação".
- **Provedores de IA plugáveis**: transcrição e LLM DEVEM ser selecionáveis por configuração, por
  meio de adaptadores das respectivas portas (Princípio I).
- **Decisões de produto vinculantes** (seção 0 do handoff):
  - O design visual é totalmente novo; a UI visual do MVP NÃO DEVE ser reaproveitada, somente o
    comportamento funcional.
  - Web e Telegram são canais de entrada equivalentes para o Professor (Princípio I): registro de
    aula, relatório semestral, histórico e o cadastro e a manutenção dos próprios alunos DEVEM
    estar disponíveis nos dois canais.
  - A administração do sistema pelo Administrador é **somente Web**; o Administrador não tem vínculo
    nem acesso pelo Telegram (Princípio I).
  - Os dois canais DEVEM aceitar áudio: no Telegram, por mensagem de voz ou envio de arquivo; na
    Web, por gravação no navegador ou envio de arquivo.
  - Os dois canais DEVEM compartilhar a mesma identidade de professor e o mesmo ciclo de vida de
    relatório.
- **Containerização e implantação**:
  - Todo serviço do sistema (aplicação, frontend, banco de dados, orquestrador de canal,
    armazenamento, proxy reverso) DEVE rodar em contêiner, tanto no ambiente local e de testes
    quanto em produção.
  - O ambiente local completo DEVE subir com um único comando documentado, e os testes (locais e
    de CI) DEVEM rodar em contêineres. A máquina do desenvolvedor NÃO DEVE precisar de nenhuma
    dependência além do runtime de contêineres.
  - Local e produção DEVEM usar as mesmas definições de imagem, variando apenas a configuração
    (variáveis de ambiente e segredos). Segredos NÃO DEVEM ser embutidos nas imagens.
  - A implantação definitiva DEVE ser feita em uma VPS Linux genérica, de qualquer provedor, com
    exigências neutras de provedor:
    - Linux com Docker Engine 27+ e Docker Compose v2 como únicas dependências do servidor;
    - os contêineres DEVEM subir a partir das definições versionadas no repositório;
    - apenas o ponto de entrada HTTPS DEVE ser exposto publicamente (Princípio IV), além do SSH
      para administração, que DEVE ser restrito;
    - nenhum recurso exclusivo de um provedor (painel, snapshot proprietário, DNS ou rede
      específicos) NÃO DEVE ser requisito para implantar ou operar o sistema.
  - O ambiente local e a produção DEVEM incluir o serviço n8n em contêiner, com a versão da imagem
    fixada (sem tag flutuante como `latest`), subido pelo mesmo comando único dos demais serviços.
  - As definições de contêiner e os scripts de implantação são artefatos de automação e DEVEM ser
    versionados (Princípio VIII).
- **Orquestrador de canal (n8n)**: restrito ao papel de transporte do Telegram (Princípio II).
  - Os workflows DEVEM ser versionados como código no repositório (Princípio VIII); o que estiver
    na instância em execução DEVE ser reproduzível a partir deles.
  - Os JSON dos workflows NÃO DEVEM conter segredos nem IDs de credencial; credenciais e segredos
    DEVEM vir do ambiente ou de um cofre de segredos (Princípio IV).
- **Referência do MVP**: o repositório `mvp-relatorios` é somente leitura e serve apenas como
  referência de comportamento; nenhuma alteração DEVE ser feita nele.

## Fluxo de Desenvolvimento e Quality Gates

- Toda implementação DEVE partir de uma spec aprovada no fluxo Spec Kit
  (specify → clarify → plan → tasks → implement).
- O plano de cada feature DEVE passar pelo "Constitution Check" antes da implementação; desvios
  DEVEM ser registrados com justificativa na seção de complexidade do plano.
- Concluídas as alterações, o autor DEVE commitá-las e abrir o Pull Request; o merge só DEVE
  acontecer após revisão e aprovação humanas (Princípio VIII).
- Todo Pull Request DEVE:
  - respeitar as camadas e a regra de dependência (Princípio I) e a nomenclatura (Princípio VI);
  - manter a paridade funcional entre Web e Telegram quando adicionar ou alterar funcionalidade
    do Professor em um dos canais (Princípio I); entregar em um canal só é desvio que DEVE ser
    justificado no plano e registrar a entrega pendente no outro canal. Funções do Administrador
    são somente Web por regra do Princípio I e não são desvio;
  - incluir os testes das mudanças e manter a cobertura mínima (Princípio V), com o CI como fonte
    de verdade;
  - ser revisado quanto a segurança e privacidade quando tocar dados de aluno, áudio, autenticação
    ou integrações de IA (Princípio IV);
  - ser revisado quanto à separação de responsabilidades quando alterar workflows de orquestração
    (Princípio II);
  - ser revisado quanto à aprovação explícita e ao versionamento quando alterar o ciclo de vida de
    relatórios (Princípio III);
  - ser revisado quanto à identidade visual e acessibilidade quando alterar interfaces, PDFs ou
    mensagens do bot (Princípio IX).
- Funcionalidades fora do escopo vigente exigem justificativa e aprovação antes de entrar em
  desenvolvimento (Princípio VII).

## Governance

Esta constituição prevalece sobre qualquer prática, convenção informal ou preferência individual.
Em conflito entre este documento e o handoff do MVP ou outra documentação do projeto, a
constituição tem precedência em arquitetura, segurança, qualidade e fluxo de trabalho; regras de
negócio continuam definidas nas specs.

**Processo de emenda**: toda alteração DEVE (1) ser proposta explicitamente, com a mudança e sua
motivação, (2) ser aprovada pelo dono do produto, (3) atualizar o texto, o Sync Impact Report e o
número de versão conforme a política abaixo, e (4) verificar se `spec-template.md`,
`plan-template.md` e `tasks-template.md` continuam consistentes com os princípios revisados.

**Política de versionamento semântico**:
- **MAJOR**: remoção ou redefinição incompatível de um princípio (ex.: permitir relatório oficial
  sem aprovação, reduzir a cobertura mínima abaixo de 90%, trocar o idioma do código).
- **MINOR**: novo princípio ou seção, ou expansão material de uma regra existente.
- **PATCH**: esclarecimentos, correções de redação e ajustes não semânticos.

**Revisão de conformidade**: todo Pull Request DEVE ser avaliado contra esta constituição durante a
revisão; violações não justificadas bloqueiam o merge. Exceções DEVEM ser registradas na descrição
do PR e aprovadas por outro revisor.

**Version**: 2.2.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-10-20
