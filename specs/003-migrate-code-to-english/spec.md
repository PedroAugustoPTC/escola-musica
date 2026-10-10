# Feature Specification: Migração do código para inglês

**Feature Branch**: `spec/003-migrate-code-to-english`

**Created**: 2026-10-10

**Status**: Draft

**Input**: User description: "Migrar todo o código existente do sistema do português para o inglês, sem alterar nenhum comportamento observável, conforme a constituição 2.0.0 (Princípio VI)."

## Contexto e valor

O sistema foi construído com nomes de código em português. A constituição 2.0.0 (Princípio VI) passou
a exigir nomes de código em inglês e manteve em português apenas o que pessoas leem: textos mostrados
ao usuário (telas, bot, PDFs), mensagens de log, documentação, specs, planos, tasks, comentários,
commits e Pull Requests. Enquanto o código existente não for migrado, ele é **dívida registrada** e a
regra de transição **bloqueia qualquer feature nova** (orquestrador n8n, secretaria, privacidade).

Esta feature quita essa dívida. É uma **refatoração pura**: o sistema deve se comportar exatamente
como antes. O valor é destravar as próximas features, que passam a nascer no idioma correto, e deixar
o código legível por qualquer desenvolvedor, sem mistura de idiomas.

## Clarifications

### Session 2026-10-10

Nenhuma ainda. As decisões M1 a M10 do plano de execução estão registradas em *Assumptions* como
padrões adotados e devem ser confirmadas em `/speckit-clarify`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - O sistema continua se comportando exatamente igual (Priority: P1)

Quem usa o sistema (professor, administrador) e quem o mantém precisam ter certeza de que a migração
não mudou nada observável: telas, mensagens do bot, PDFs, fluxos, regras de negócio, autorização e
desempenho. Toda a suíte de verificação existente (unidade, contrato, paridade entre canais, e2e,
acessibilidade) deve passar **sem que nenhuma expectativa de texto seja alterada**.

**Why this priority**: é a garantia central de uma refatoração. Sem ela, a migração é uma mudança de
risco, não uma quitação de dívida. Todo o resto só vale se o comportamento for preservado.

**Independent Test**: executar a suíte completa existente sobre o código migrado e comparar os
catálogos de mensagens visíveis ao usuário, antes e depois, procurando qualquer diferença de texto.

**Acceptance Scenarios**:

1. **Given** o código migrado, **When** a suíte completa (unidade, contrato, paridade entre canais,
   e2e e acessibilidade) é executada, **Then** 100% dos testes passam e nenhuma expectativa de texto
   foi modificada para isso.
2. **Given** os catálogos de mensagens antes e depois da migração, **When** os textos mostrados ao
   usuário são comparados, **Then** não há nenhuma diferença nos textos (somente as chaves mudam).
3. **Given** um professor que registra uma aula, revisa, aprova e baixa o PDF, em cada canal (Web e
   Telegram), **When** o fluxo termina, **Then** telas, mensagens, botões, rótulos, nome do arquivo
   baixado e conteúdo do PDF são idênticos aos de antes da migração.
4. **Given** qualquer teste existente que precise de ajuste só por causa do novo nome de um
   identificador, **When** o ajuste é feito, **Then** ele é exclusivamente de nome (identificador), nunca
   de expectativa de texto, de valor de negócio ou de resultado esperado. Se uma expectativa de texto
   precisar mudar, o trabalho para e o fato é reportado, pois indica que algo além do nome mudou.

---

### User Story 2 - Nenhum nome em português volta ao código (Priority: P1)

Quem mantém o projeto precisa de uma barreira automática que impeça a reintrodução de nomes em
português. Um teste guardião examina o código e falha quando encontra identificador com termo do
glossário em português fora da lista de exceções documentada.

**Why this priority**: sem a barreira, a dívida volta aos poucos. É o que torna a quitação durável e
verificável.

**Independent Test**: introduzir de propósito, em uma branch descartável, um identificador com termo do
glossário em português (em cada tipo de artefato coberto) e verificar que o teste guardião falha
apontando o arquivo e o termo; removê-lo e verificar que volta a passar.

**Acceptance Scenarios**:

1. **Given** o código migrado, **When** o teste guardião roda, **Then** ele passa com zero
   identificadores em português fora da lista de exceções.
2. **Given** um novo identificador com termo do glossário em português (classe, método, variável,
   arquivo, pasta, tabela, coluna, rota, campo de contrato, código de erro, chave de catálogo, variável
   de ambiente, classe CSS, script ou workflow), **When** o teste guardião roda, **Then** ele falha e
   informa onde está o termo e qual é o equivalente em inglês do glossário.
3. **Given** uma sigla consagrada ou nome de terceiros na lista de exceções, **When** o teste guardião
   roda, **Then** ele não acusa falso positivo, e cada exceção da lista tem o motivo registrado.
4. **Given** o teste guardião, **When** o pipeline de integração contínua roda, **Then** ele faz
   parte das verificações obrigatórias e bloqueia a integração em caso de falha.

---

### User Story 3 - Um desenvolvedor lê e navega o código em um único idioma (Priority: P2)

Quem chega ao projeto (ou quem vai escrever as próximas features) deve conseguir ler o código inteiro
com um vocabulário único e consistente: cada conceito do domínio tem um só nome em inglês, conforme o
glossário da constituição, e as diretrizes, READMEs e scripts descrevem a estrutura real.

**Why this priority**: é o benefício direto da feature, mas só é colhido depois que o comportamento
está preservado (US1) e a barreira existe (US2).

**Independent Test**: escolher uma amostra de conceitos do domínio (ex.: aluno, aula, relatório de aula,
vínculo do Telegram, código de acesso) e verificar que cada um aparece com um único nome em inglês em
todo o repositório (código, banco, contratos, frontend, infra, CI), e que os `CLAUDE.md`, READMEs e
scripts mencionam apenas nomes que existem.

**Acceptance Scenarios**:

1. **Given** o glossário pt→en da constituição, **When** se busca por qualquer termo canônico em inglês,
   **Then** ele aparece consistentemente em todas as camadas, sem sinônimos para o mesmo conceito.
2. **Given** os `CLAUDE.md` de cada estrutura, os READMEs e os scripts, **When** são lidos após a
   migração, **Then** descrevem pacotes, pastas, comandos e variáveis com os novos nomes, sem referência
   a nomes que deixaram de existir (exceto no histórico das specs antigas, sinalizado).
3. **Given** as specs 001 e 002 e a constituição, **When** mencionam "feature 003" como sendo o n8n,
   **Then** passam a mencionar "feature 004"; as demais referências à feature 003 (esta migração)
   permanecem corretas.
4. **Given** as specs 001 e 002, que citam nomes antigos de classe e arquivo, **When** são abertas,
   **Then** têm no topo uma nota dizendo que são histórico e apontando para o glossário, sem terem sido
   reescritas.

---

### User Story 4 - O ambiente sobe e o fluxo ponta a ponta funciona do zero (Priority: P2)

Quem opera o projeto precisa subir o ambiente local completo com o comando único documentado, sobre um
banco novo, e percorrer o fluxo principal sem erro, com os novos nomes de variáveis de ambiente,
scripts e workflows.

**Why this priority**: a migração toca banco, configuração, scripts e CI; é preciso provar que o
conjunto sobe e funciona integrado, não apenas que cada parte compila.

**Independent Test**: em uma máquina sem estado prévio, seguir o README/quickstart: gerar a
configuração local, subir o ambiente com o comando único e percorrer o fluxo de aula até o PDF nos dois
canais.

**Acceptance Scenarios**:

1. **Given** um ambiente limpo, **When** o comando único documentado é executado, **Then** todos os
   serviços sobem saudáveis e a carga inicial é aplicada.
2. **Given** o banco migrado, **When** a aplicação é iniciada sobre um banco criado do zero e também
   sobre um banco que já tinha o esquema antigo, **Then** em ambos os casos o esquema final é o mesmo,
   em inglês, e a aplicação inicia sem erro.
3. **Given** o pipeline de integração contínua, **When** roda sobre a branch da feature, **Then** todos
   os workflows e jobs, já com nomes em inglês, passam.
4. **Given** quem já tinha um arquivo de configuração local com as variáveis antigas, **When** tenta
   subir o ambiente, **Then** recebe uma orientação clara de que precisa regerá-lo a partir do exemplo
   atualizado (sem período de compatibilidade).

---

### Edge Cases

- **Palavras que o professor digita no bot** ("aprovar", "cancelar", "início"): são entrada do usuário,
  não identificador; continuam em português e continuam sendo reconhecidas exatamente como antes.
- **Texto mostrado ao usuário e mensagens de log**: permanecem em português acentuado; só as chaves dos
  catálogos mudam para inglês. Nenhum texto pode ser alterado nem acidentalmente traduzido.
- **Valores de enumeração persistidos** (ex.: situação de um relatório): passam a inglês no banco e nos
  contratos, mas a interface continua exibindo o rótulo em português pelo catálogo.
- **Nome do arquivo PDF mostrado ao usuário** (download e documento enviado no Telegram): continua em
  português; o nome interno de armazenamento passa a inglês.
- **Botões do Telegram antigos**: mensagens já enviadas com botões no formato antigo deixam de funcionar
  (aceito, pois não há produção nem usuários reais); o sistema não pode quebrar ao receber um deles, e
  deve responder de forma segura.
- **Rotas antigas da API e da SPA**: deixam de existir; uma requisição a elas recebe a resposta padrão
  de rota inexistente, sem comportamento especial.
- **Nomes impostos por terceiros ou por ferramentas** (palavras reservadas, APIs de terceiros, nomes de
  arquivos exigidos por frameworks, siglas consagradas como PDF, LLM, HTTP, DTO, CPF): ficam fora da
  regra e entram na lista de exceções documentada, cada uma com o motivo.
- **Um mesmo conceito com nomes diferentes em camadas diferentes** (ex.: o banco usa um termo e o código
  outro): é defeito da migração; o glossário exige um único nome.
- **Defeito de comportamento descoberto durante a migração**: não é corrigido nesta feature; é anotado e
  tratado em trabalho separado.
- **Termos do domínio que o glossário da constituição não cobre** (ex.: substantivos de menor uso):
  precisam ser decididos e registrados no glossário antes de serem usados, para não nascerem sinônimos.

## Requirements *(mandatory)*

### Functional Requirements

**Preservação do comportamento**

- **FR-001**: O sistema DEVE se comportar exatamente como antes da migração, em todos os fluxos, canais
  e regras de negócio, sem nenhuma mudança observável por usuários ou por sistemas integrados, exceto
  os nomes técnicos expostos nas bordas (rotas, campos, códigos de erro, ações de botões), que mudam por
  exigência desta feature.
- **FR-002**: Nenhum texto mostrado ao usuário (telas, mensagens e botões do bot, PDFs) DEVE ser
  alterado; nenhuma mensagem de log DEVE ser alterada.
- **FR-003**: Toda a suíte existente (unidade, contrato, paridade entre canais, e2e e acessibilidade)
  DEVE passar sem alteração de nenhuma expectativa de texto. Os testes DEVEM ter seus próprios
  identificadores migrados para inglês.
- **FR-004**: A cobertura de testes mínima exigida pelo projeto DEVE ser mantida; a migração não pode
  reduzi-la.
- **FR-005**: Esta feature NÃO DEVE incluir mudança de regra de negócio, de layout, de comportamento do
  bot, de desempenho, de contrato semântico, correção de bugs nem reorganização de camadas.

**Escopo da migração (tudo que é código)**

- **FR-006**: DEVEM ser migrados para inglês: pacotes, módulos, classes, interfaces, métodos, variáveis,
  constantes, arquivos e pastas do código de aplicação e dos testes, no backend e no frontend.
- **FR-007**: DEVEM ser migrados para inglês, no banco de dados: tabelas, colunas, índices, constraints
  e valores de enumeração persistidos.
- **FR-008**: DEVEM ser migrados para inglês, nas APIs e nos contratos: rotas, campos, códigos de erro e
  valores de enumeração, tanto no contrato do Portal quanto no da API interna do canal Telegram (incluindo
  o esquema das ações).
- **FR-009**: DEVEM ser migrados para inglês: as ações e os dados dos botões do Telegram e as chaves dos
  catálogos de mensagens (somente as chaves; os textos não mudam).
- **FR-010**: DEVEM ser migrados para inglês: variáveis de ambiente, classes e variáveis CSS, componentes,
  páginas e rotas do frontend, nomes de scripts e de workflows de integração contínua e de implantação,
  e nomes de arquivos e pastas de infraestrutura.
- **FR-011**: As palavras-chave que o professor digita no bot ("aprovar", "cancelar", "início") DEVEM
  continuar em português, por serem entrada do usuário e não identificador.
- **FR-012**: Os contratos DEVEM continuar sendo a fonte de verdade: a mudança de nomes começa pelos
  contratos, e o backend e o código gerado a partir deles DEVEM refletir os novos nomes.

**Banco de dados**

- **FR-013**: A migração do esquema DEVE ocorrer por **nova migração versionada**, sem editar as
  migrações já existentes, e DEVE preservar os dados existentes dos ambientes de desenvolvimento e de
  teste (renomeação, não recriação).
- **FR-014**: O esquema final DEVE ser idêntico, em estrutura e em regras (chaves, unicidades,
  restrições, índices), ao esquema anterior, mudando somente os nomes.
- **FR-015**: A migração DEVE funcionar tanto em banco criado do zero quanto em banco que já estava no
  esquema anterior.

**Vocabulário único**

- **FR-016**: O glossário pt→en da constituição 2.0.0 DEVE ser a fonte de verdade dos nomes; cada
  conceito do domínio DEVE ter um único nome em inglês em todas as camadas, sem sinônimos.
- **FR-017**: Termos do domínio ou técnicos que não constem do glossário DEVEM ser definidos e
  registrados no glossário (com um único equivalente em inglês) antes do uso.
- **FR-018**: Os sufixos padronizados da constituição (`*UseCase`, `*Controller`, `*Service`,
  `*Repository`, `*Port`, `*Adapter`, `*Mapper`, `*Dto`, `*Request`, `*Response`) DEVEM ser aplicados a
  todo o código migrado.

**Teste guardião**

- **FR-019**: DEVE existir um teste automatizado que falhe quando surgir identificador com termo do
  glossário em português, cobrindo o código do backend, o código do frontend e os artefatos de dados e
  configuração (esquema do banco, contratos, infraestrutura e workflows).
- **FR-020**: O teste guardião DEVE usar uma **lista de exceções documentada** (siglas consagradas,
  nomes de terceiros, nomes impostos por ferramentas), na qual cada entrada tem o motivo registrado.
- **FR-021**: A falha do teste guardião DEVE indicar o local do problema, o termo encontrado e o
  equivalente em inglês esperado.
- **FR-022**: O teste guardião DEVE fazer parte das verificações obrigatórias do pipeline de integração
  contínua e DEVE rodar nos contêineres de teste, como os demais testes.
- **FR-023**: O teste guardião NÃO DEVE alcançar textos mostrados ao usuário, mensagens de log,
  documentação, specs, comentários nem as palavras digitadas pelo professor, que permanecem em
  português.

**Fim da compatibilidade e bordas**

- **FR-024**: Não DEVE haver período de compatibilidade: rotas, campos, códigos de erro, ações de botão e
  variáveis de ambiente antigos deixam de existir, sem alias nem redirecionamento.
- **FR-025**: Ao receber uma ação de botão no formato antigo, o sistema NÃO DEVE falhar de modo
  inesperado; DEVE tratá-la como uma ação inválida, do mesmo modo que trata qualquer ação desconhecida.
- **FR-026**: O nome do arquivo PDF mostrado ao usuário (download e documento enviado no chat) DEVE
  permanecer em português; o nome interno de armazenamento DEVE ser em inglês.

**Documentação e operação**

- **FR-027**: Os `CLAUDE.md` de cada estrutura, os READMEs, os scripts, o `.env.example` e os
  quickstarts DEVEM ser atualizados, no mesmo trabalho, para os novos nomes, e as afirmações de que "o
  código ainda está em português" e as regras de transição DEVEM ser removidas ou reescritas.
- **FR-028**: As referências a "feature 003" que significam o orquestrador n8n, nas specs 001 e 002 e na
  constituição, DEVEM passar a "feature 004". A constituição DEVE ser atualizada para registrar a quitação
  da dívida e o fim do bloqueio de novas features, por emenda conforme a governança.
- **FR-029**: As specs 001 e 002 NÃO DEVEM ser reescritas; DEVEM receber, no topo, uma nota de que
  citam nomes anteriores à migração e permanecem como histórico, apontando para o glossário.
- **FR-030**: O ambiente local completo DEVE subir com o comando único documentado, já com os novos
  nomes de script, e a carga inicial DEVE funcionar com os campos renomeados.

**Execução em fatias verificáveis**

- **FR-031**: A migração DEVE ser entregue em fatias que, ao fim de cada uma, compilam e passam em
  todas as suítes aplicáveis, cada uma com commit próprio, na ordem: glossário e teste guardião;
  núcleo; aplicação (incluindo banco e configuração); contratos e tipos gerados do frontend; frontend;
  infraestrutura, CI e scripts; documentação.
- **FR-032**: A renomeação DEVE ser feita por ferramenta de refatoração com análise semântica, e não por
  edição manual arquivo a arquivo, para reduzir o risco de erro e de mudança de comportamento.

### Key Entities

Esta feature não cria entidades novas nem altera o modelo de dados; só os nomes. Os conceitos
envolvidos, com seu nome canônico em inglês pela constituição, são:

- **Teacher (Professor)**: usuário que registra aulas e relatórios.
- **Student (Aluno)**: pessoa atendida pelo professor.
- **Lesson (Aula)**: encontro registrado pelo professor.
- **LessonReport (RelatorioAula)**: relatório de uma aula, com versões e aprovação.
- **SemesterReport (RelatorioSemestral)**: relatório consolidado por semestre.
- **TelegramLink (VinculoTelegram)**: vínculo entre um professor e sua conta do Telegram.
- **Administrator (Administrador)**: usuário que administra cadastros.
- **AccessCode (CodigoDeAcesso)**: código usado para vincular o Telegram.
- **TeacherStudentAssignment (associação professor-aluno)**: quem atende quem.
- **Draft (Rascunho)**, **Version (Versão)**, **Approval (Aprovação)**: estados e marcos do relatório.
- **Glossário e lista de exceções**: artefato novo, documental, que registra o equivalente canônico de
  cada termo e as exceções permitidas, com motivo. É a fonte de dados do teste guardião.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero identificadores com termo do glossário em português no repositório, fora da lista de
  exceções documentada, verificado automaticamente pelo teste guardião a cada integração.
- **SC-002**: 100% da suíte existente (unidade, contrato, paridade entre canais, e2e, acessibilidade)
  passa no código migrado, com zero expectativas de texto alteradas.
- **SC-003**: Zero diferenças entre os catálogos de mensagens (textos mostrados ao usuário) antes e
  depois da migração; somente as chaves mudam.
- **SC-004**: A cobertura de testes após a migração é igual ou maior que a anterior, nunca abaixo do
  mínimo exigido pelo projeto.
- **SC-005**: O ambiente local sobe do zero com o comando único documentado, em um único procedimento
  sem passos manuais além dos documentados, e o fluxo de aula até o PDF funciona nos dois canais.
- **SC-006**: 100% dos conceitos do glossário da constituição têm um único nome em inglês em todas as
  camadas (zero sinônimos).
- **SC-007**: Um desenvolvedor novo, só com os `CLAUDE.md` e READMEs atualizados, localiza qualquer
  estrutura descrita neles sem encontrar nomes que não existem mais (zero referências quebradas).
- **SC-008**: Ao fim da feature, o bloqueio "nenhuma feature nova pode ser iniciada" é suspenso, e as
  features seguintes (a partir da 004) podem ser especificadas sem exceção de transição.

## Assumptions

- **Decisões M1 a M10** do plano de execução são adotadas como padrão e serão confirmadas em
  `/speckit-clarify`:
  - M1: valores de status persistidos e expostos passam para inglês; a interface mostra o rótulo em
    português pelo catálogo.
  - M2: códigos de erro da API passam para inglês; as mensagens ao usuário seguem em português.
  - M3: ações dos botões do Telegram passam para inglês; não há botão antigo em circulação a suportar.
  - M4: palavras digitadas pelo professor no bot continuam em português.
  - M5: nome do PDF mostrado ao usuário continua em português; nome interno de armazenamento em inglês.
  - M6: rotas da SPA passam para inglês.
  - M7: o banco é migrado por uma **nova** migração, sem editar as já aplicadas.
  - M8: variáveis de ambiente passam para inglês; quem tem configuração local antiga precisa regerá-la.
  - M9: o teste guardião cobre código do backend, do frontend e artefatos de dados e configuração.
  - M10: scripts e workflows passam para inglês, com o README atualizado.
- Não há produção nem dados reais; portanto não há período de compatibilidade, e os dados existentes
  nos ambientes de desenvolvimento e teste são preservados pela renomeação, mas não precisam de
  retrocompatibilidade de interface.
- O glossário da constituição 2.0.0 cobre os conceitos centrais do domínio; os termos técnicos e de
  menor uso (nomes de pacotes, camadas, portas e adaptadores, pastas do frontend) serão definidos no
  plano, registrados no glossário e validados por `/speckit-clarify`, mantendo um único equivalente por
  conceito.
- A suíte e as ferramentas de verificação existentes são confiáveis e suficientes para provar a
  preservação do comportamento; este trabalho não os amplia, salvo o teste guardião.
- Os workflows do n8n ainda não existem no repositório (são da feature 004); portanto não há workflow
  de orquestração a migrar aqui, apenas a correção das referências à numeração.
- Os nomes das pastas das specs 001 e 002 permanecem como estão (histórico); a pasta desta feature já
  nasce em inglês.
- Os Pull Requests e commits seguem o Princípio VIII: branch própria a partir da `main` atualizada, PR
  aberto ao concluir, merge somente pelo usuário após revisão.
- Dependência: esta feature é pré-requisito das features 004 (n8n), 005 e 006 (secretaria) e 007
  (privacidade).
