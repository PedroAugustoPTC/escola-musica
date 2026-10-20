# Feature Specification: Administração — acesso e cadastros (administrador na Web; professor na Web e no Telegram)

**Feature Branch**: `spec/005-admin-registrations-access`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "Área de administração para cadastros e acesso, disponível na Web e no Telegram com paridade (constituição, Princípio I e restrições de produto), de modo que a escola deixe de depender do procedimento operacional de carga inicial. Somente dois papéis têm função administrativa: o Administrador do sistema e o Professor (este apenas sobre os seus próprios alunos). Aluno e responsável não têm nenhuma função administrativa nem qualquer acesso ao sistema." (descrição completa preservada no pedido de `/speckit-specify` e em `PLANO_EXECUCAO_SPECKIT.md`, seção 8; resumida nas seções abaixo). **Ajuste de 2026-10-20 (Clarifications):** o pedido original previa o administrador também no Telegram; por decisão do dono do produto, o administrador opera **somente pela Web** e o Telegram é **somente do professor**.

**Materiais de referência**:

- Constituição v2.1.0: Princípio I (paridade entre Web e Telegram; mesmos casos de uso; **a decisão "administrador só na Web" diverge dele e das "Decisões de produto vinculantes"** — ver Assumptions), II (o backend decide autorização e validação; o n8n só transporta), III (só o professor aprova relatório), IV (CPF cifrado; logs sem dado sensível; o professor acessa somente os próprios alunos, verificado no backend), VI (código em inglês; textos e logs em português) e IX (identidade visual, WCAG 2.1 AA, mobile-first).
- `specs/001-fluxo-relatorios/spec.md`: FR-004 a FR-011 (acesso do professor, código de acesso, bloqueio por tentativas, escopo por aluno, carga inicial) e a seção Assumptions (funções da "secretaria" adiadas para esta feature). A spec 001 é **histórico** e não é reescrita; esta spec registra o que ela substitui (FR-042 a FR-044).
- `specs/004-n8n-telegram-transport/`: o transporte do Telegram sobre o qual esta feature opera. Nenhuma regra da 004 muda aqui.
- `HANDOFF_MVP_Relatorios_Escola_Musica.md`, seções 2.4, 4.2, 5.1 (RN-01 a RN-04), 5.2 (RN-05, RN-06, RN-11), 5.3 (RN-12, RN-14), 7.1, 12.2 e 17; e `PLANO_EXECUCAO_SPECKIT.md`, seções 4.2 (decisões D2 a D5 e D9) e 8.

**Escopo desta feature**: o ator **Administrador** e as operações de cadastro e de acesso — professores, alunos, associações professor–aluno, código de acesso, desvinculação do Telegram de professor e administradores — todas **somente pela Web**, pelo administrador — mais o cadastro dos **próprios alunos** pelo **Professor**, na Web e no Telegram (com paridade entre os dois), e o procedimento seguro para criar o primeiro administrador. **Fora de escopo**: histórico de qualquer aluno e visão da escola (feature 006); retenção, exclusão a pedido, consentimento do responsável e trilha de auditoria completa (feature 007); qualquer conta, login, tela, rota, comando ou aviso para aluno ou responsável; professor gerenciar professores, administradores, códigos de acesso ou associações; edição ou aprovação de relatório de professor pelo administrador (Princípio III: só o professor aprova); evoluções de produto.

## Clarifications

### Session 2026-10-20

- Q: Como o administrador prova quem é ao vincular a sua conta do Telegram (D2)? → A: **Não vincula.** O administrador opera **somente pela Web**; o Telegram é somente do professor. Não existe vínculo de Telegram, código de vínculo, comando nem menu administrativo para o administrador. O professor continua com o vínculo que já tem e confirma os dados antes de gravar. Efeito: toda operação administrativa é só Web; a paridade entre Web e Telegram vale para as operações do professor. **Isso diverge do Princípio I e das "Decisões de produto vinculantes" da constituição v2.1.0** (administração nos dois canais); o tratamento da divergência está em Assumptions.

## Quem usa esta feature

- **Administrador**: pessoa da escola (direção ou secretaria) que opera a escola inteira **somente pela Web**: cadastra e mantém professores, alunos, associações e outros administradores, emite códigos de acesso e desvincula o Telegram de professores. Não usa o Telegram para nada disto.
- **Professor**: já entra por código de acesso, na Web e no Telegram (o Telegram é só dele). Passa a cadastrar e manter **somente os alunos associados a ele**, nos dois canais; nada além disso.
- **Aluno e responsável: não são atores.** São sujeitos dos dados. Não têm conta, login, vínculo de Telegram, tela, rota, comando nem aviso, nem de leitura. O responsável existe apenas como **nome no cadastro do aluno menor de idade**.
- **Mantenedor da plataforma**: executa o procedimento operacional que cria o primeiro administrador e que recupera o acesso se nenhum administrador conseguir entrar.

O conjunto de papéis que se autenticam é **fechado em dois**: Administrador e Professor.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Acesso do administrador, somente pela Web (Priority: P1)

O administrador entra no portal com e-mail e senha e chega a uma área administrativa separada da área do professor. A sessão dura 2 horas fixas, sem renovação por uso. Errar a senha 5 vezes seguidas, da mesma origem, bloqueia novas tentativas por 15 minutos, inclusive com a senha correta, e a tela informa o tempo restante. As ações destrutivas pedem confirmação explícita antes de valer. O administrador **não tem acesso pelo Telegram**: o bot não o reconhece como administrador nem oferece operação administrativa. Uma credencial de professor nunca abre uma operação administrativa, nem a de administrador abre uma operação do professor.

**Why this priority**: sem o ator Administrador nada mais desta feature existe, e o acesso é a parte que protege a escola inteira: um administrador comprometido enxerga todos os alunos. Manter o acesso só na Web, onde há senha e sessão curta, também reduz a superfície de ataque.

**Independent Test**: com um administrador existente (criado pelo procedimento da Story 8), entrar na Web com a senha certa e com a errada, esgotar as tentativas, esperar a sessão vencer, tentar uma operação administrativa com uma credencial de professor e uma do professor com a de administrador, e conferir que nenhuma conta do Telegram consegue executar operação administrativa.

**Acceptance Scenarios**:

1. **Given** um administrador ativo, **When** ele informa e-mail e senha corretos na Web, **Then** entra na área administrativa e a sessão expira 2 horas depois do login, mesmo que ele continue usando o sistema.
2. **Given** a sessão expirada ou encerrada ("Sair"), **When** o administrador tenta qualquer operação administrativa, **Then** é levado à tela de entrada e nada é executado; "Sair" encerra a sessão no servidor.
3. **Given** e-mail inexistente, senha errada ou conta inativa, **When** a entrada é tentada, **Then** a mensagem é a mesma nos três casos e não revela qual deles ocorreu.
4. **Given** 5 tentativas incorretas consecutivas da mesma origem, **When** a sexta tentativa chega, mesmo com a senha correta, **Then** é recusada, com o tempo restante do bloqueio; ao fim dos 15 minutos a contagem recomeça do zero, e um acesso bem-sucedido também zera a contagem.
5. **Given** uma credencial de professor (sessão Web ou vínculo do Telegram), **When** ela é usada em qualquer rota ou comando administrativo, **Then** a operação é negada e nada é revelado; e uma credencial de administrador usada em rota do professor também é negada.
6. **Given** qualquer conta do Telegram (vinculada a professor ou não), **When** ela envia um comando ou texto que tente uma operação administrativa, **Then** recebe uma negativa neutra, sem revelar que a operação existe na Web; o bot nunca oferece menu administrativo.
7. **Given** o administrador na Web, **When** ele inicia uma ação destrutiva (por exemplo, inativar um professor), **Then** a tela pede confirmação explícita nomeando a ação e o alvo; cancelar ou fechar o diálogo não grava nada.

---

### User Story 2 - Professores, pelo administrador (Priority: P1)

O administrador cadastra um professor informando nome e e-mail. O professor nasce ativo e com um código de acesso de 8 caracteres, válido por 7 dias, mostrado **uma única vez** para o administrador repassar. O administrador lista os professores (vendo se cada um tem Telegram vinculado e se está ativo), edita nome e e-mail, inativa e reativa. E-mail repetido é recusado com mensagem clara. O professor não gerencia professores.

**Why this priority**: é a primeira operação que a escola faz sem a carga inicial, e sem professor cadastrado não há aluno para associar nem relatório para produzir.

**Independent Test**: na Web, cadastrar um professor, anotar o código exibido, usar o código para entrar como professor (na Web e no Telegram), listar, editar, tentar cadastrar o mesmo e-mail, inativar (conferindo que o acesso do professor cai nos dois canais) e reativar.

**Acceptance Scenarios**:

1. **Given** nome e e-mail inéditos, **When** o administrador cadastra o professor, **Then** ele nasce ativo, recebe um código de 8 caracteres (letras maiúsculas e dígitos, sem caracteres ambíguos) válido por 7 dias, e o código é mostrado uma única vez, com aviso de que não poderá ser exibido de novo.
2. **Given** o código exibido, **When** o professor o informa na Web ou o envia ao bot, **Then** acessa normalmente, como definido na spec 001 (FR-004 a FR-008).
3. **Given** um e-mail já usado por outro professor (inclusive inativo; sem diferenciar maiúsculas de minúsculas), **When** o administrador tenta cadastrar ou editar para esse e-mail, **Then** a operação é recusada com mensagem clara e nada é gravado.
4. **Given** professores cadastrados, **When** o administrador abre a lista, **Then** vê, para cada um, nome, e-mail, situação (ativo ou inativo) e se há Telegram vinculado; nunca vê o código de acesso.
5. **Given** um professor ativo sem relatórios em aberto, **When** o administrador o inativa, **Then** o professor perde na hora todo acesso (sessões Web e vínculo do Telegram encerrados, código invalidado) e passa a constar como inativo; ao reativar, volta a ser ativo com um código novo, exibido uma única vez.
6. **Given** um professor com relatórios em aberto, **When** o administrador tenta inativá-lo, **Then** a inativação é bloqueada, com aviso que lista quais relatórios estão em aberto (aluno, etapa) e orienta o professor a concluí-los ou cancelá-los; nada muda.
7. **Given** uma credencial de professor, **When** tenta listar, cadastrar, editar, inativar ou reativar professores, **Then** é negada.

---

### User Story 3 - Alunos, pelo administrador (Priority: P1)

O administrador cadastra alunos informando nome, data de nascimento, CPF, nome do responsável (obrigatório se o aluno tem menos de 18 anos no cadastro), instrumento (opcional) e os professores associados (zero ou mais). Lista com busca, edita, inativa e reativa **qualquer** aluno. O CPF é validado, guardado cifrado, usado só para impedir duplicidade e nunca é exibido nem devolvido. Data inexistente (31/02) é recusada.

**Why this priority**: o aluno é a unidade em torno da qual todo relatório existe; sem cadastro de aluno a escola continua dependente da carga.

**Independent Test**: na Web, cadastrar um aluno adulto e um menor (com e sem responsável), tentar CPF inválido, CPF repetido e 31/02, associar professores, buscar, editar, inativar e reativar, e conferir que o CPF não aparece em nenhuma resposta, tela ou registro de operação.

**Acceptance Scenarios**:

1. **Given** dados válidos de um aluno maior de 18 anos, **When** o administrador o cadastra, **Then** o aluno nasce ativo, associado aos professores escolhidos (ou a nenhum), e o CPF não é mostrado de volta.
2. **Given** um aluno com menos de 18 anos na data do cadastro e sem nome de responsável, **When** o administrador tenta cadastrar, **Then** é recusado pedindo o responsável.
3. **Given** um CPF com dígitos verificadores inválidos, ou uma data inexistente (31/02) ou futura, **When** o administrador tenta cadastrar, **Then** é recusado com mensagem específica, em português, sem detalhe interno.
4. **Given** um CPF que já pertence a um aluno (ativo ou inativo), **When** o administrador tenta cadastrar outro aluno com ele, **Then** é recusado com mensagem clara que indica o aluno já cadastrado pelo nome, sem exibir o CPF.
5. **Given** vários alunos, **When** o administrador busca por parte do nome ou pelo instrumento, **Then** a lista traz os que casam (sem diferenciar maiúsculas, minúsculas e acentos), de todos os professores, com situação e professores associados.
6. **Given** um aluno cadastrado, **When** o administrador edita nome, data, responsável, instrumento ou CPF (deixar o CPF em branco mantém o atual), **Then** as mesmas validações valem e o aluno é atualizado.
7. **Given** um aluno sem relatórios em aberto, **When** o administrador o inativa, **Then** ele deixa de ser oferecido para novos relatórios e de aparecer nas listas padrão dos professores; nada é apagado; reativar o devolve às listas.
8. **Given** um aluno com relatório em aberto, **When** o administrador tenta inativá-lo, **Then** é bloqueado, com aviso que lista os relatórios em aberto e quem os conduz.

---

### User Story 4 - Alunos, pelo professor (somente os seus) (Priority: P1)

O professor, na Web ou no Telegram, cadastra um aluno com os mesmos campos e as mesmas validações; o aluno já nasce associado a ele. Lista e busca **somente** os seus, edita e inativa/reativa **somente** os seus. Nunca vê nem altera aluno de outro professor, e nenhuma resposta revela a existência ou os dados de aluno que não seja seu. Se o CPF informado já pertence a um aluno que não é dele, o cadastro é recusado com mensagem neutra, que manda procurar o administrador, sem nome nem professor. Um professor sem alunos pode cadastrar o primeiro. No Telegram, o professor usa o vínculo que já tem e **confirma os dados antes de gravar**.

**Why this priority**: tira da secretaria o gargalo do dia a dia (o professor cadastra o aluno que acabou de receber) sem abrir mão do isolamento entre professores.

**Independent Test**: com dois professores, A e B, cada um com alunos: A cadastra, lista, busca, edita e inativa os seus; tenta abrir, editar, inativar ou buscar um aluno de B (por identificador manipulado na Web, por nome ou escolha no Telegram) e tenta cadastrar com o CPF de um aluno de B; confere que tudo isso é negado ou tratado como inexistente sem vazamento, em cada canal.

**Acceptance Scenarios**:

1. **Given** um professor ativo, **When** ele cadastra um aluno com dados válidos, **Then** o aluno nasce ativo e associado só a ele, sem que o professor escolha outro professor.
2. **Given** um professor, **When** ele lista ou busca alunos, **Then** vê apenas os associados a ele, incluindo os inativos quando filtrar por situação.
3. **Given** um aluno de outro professor, **When** o professor tenta abri-lo, editá-lo, inativá-lo ou reativá-lo (por identificador manipulado na Web ou por nome no Telegram), **Then** a resposta é indistinguível da de um aluno que não existe, e nada é alterado.
4. **Given** um CPF que já pertence a um aluno que não é do professor, **When** ele tenta cadastrar, **Then** o cadastro é recusado com mensagem neutra que orienta procurar o administrador, sem nome do aluno, nem de professor, nem qualquer outro dado.
5. **Given** um aluno associado a mais de um professor, **When** um deles edita os dados cadastrais, **Then** a edição vale; **And** quando tenta inativá-lo, a ação é negada com orientação de procurar o administrador, porque só o professor **único** associado pode inativar.
6. **Given** um aluno cujo único professor é o próprio ator, **When** ele o inativa (sem relatórios em aberto), **Then** o aluno fica inativo; e, ao reativá-lo, volta a ficar ativo.
7. **Given** um professor sem nenhum aluno, **When** ele entra por qualquer canal, **Then** vê a orientação de cadastrar o primeiro aluno (e não de "falar com a secretaria").
8. **Given** o professor no Telegram, **When** ele conclui o preenchimento dos dados de um aluno, **Then** o bot mostra o resumo (sem o CPF) e só grava depois da confirmação explícita; cancelar, ou deixar a confirmação vencer, não grava nada e descarta os dados informados.
9. **Given** qualquer tentativa do professor de incluir ou retirar professores de um aluno, cadastrar aluno para outro professor, ou usar operações de professor, administrador, código ou associação, **Then** é negada.

---

### User Story 5 - Associações professor–aluno, pelo administrador (Priority: P2)

O administrador inclui e retira professores de um aluno a qualquer momento. Retirar um professor que tem relatório em aberto daquele aluno não apaga nada: o relatório fica acessível ao professor, somente para concluir ou cancelar, até acabar; os já aprovados permanecem preservados.

**Why this priority**: o cadastro funciona só com associação definida na criação; ajustar depois é necessário na prática, mas pode vir logo após os P1.

**Independent Test**: associar e desassociar professores de um aluno com e sem relatório em aberto; conferir, na Web e no Telegram, o que cada professor passa a ver.

**Acceptance Scenarios**:

1. **Given** um aluno e um professor ativo ainda não associado, **When** o administrador os associa, **Then** o professor passa a ver o aluno e a poder registrar aulas para ele, na Web e no Telegram.
2. **Given** um aluno associado a um professor sem relatório em aberto dele, **When** o administrador retira a associação, **Then** o aluno some das listas do professor e qualquer acesso dele àquele aluno passa a ser negado como inexistente.
3. **Given** um professor com relatório em aberto de um aluno, **When** o administrador retira a associação, **Then** o relatório continua visível ao professor e ele só pode concluí-lo ou cancelá-lo; **And** ele não consegue iniciar novo relatório para o aluno; **And** terminado o relatório em aberto, o acesso ao aluno deixa de existir.
4. **Given** um aluno cuja última associação foi retirada, **When** o administrador consulta, **Then** o aluno continua existindo com zero professores, visível só ao administrador.
5. **Given** um professor inativo, **When** o administrador tenta associá-lo a um aluno, **Then** é recusado.

---

### User Story 6 - Código de acesso e vínculo do Telegram do professor, pelo administrador (Priority: P2)

O administrador reemite o código de acesso de qualquer professor (o anterior deixa de valer, o novo vale 7 dias e aparece uma única vez), o que encerra as sessões Web daquele professor e, se o administrador quiser, também o vínculo do Telegram. O administrador também pode desvincular o Telegram de um professor.

**Why this priority**: cobre o dia em que o professor perde o acesso ou troca de celular; sem isso a escola voltaria ao procedimento operacional.

**Independent Test**: reemitir o código de um professor com sessão Web e Telegram ativos, com e sem a opção de encerrar o Telegram; usar o código antigo e o novo; desvincular o Telegram e conferir que o professor precisa enviar o código de novo. As operações são feitas pelo administrador na Web; os efeitos são conferidos na Web e no Telegram do professor.

**Acceptance Scenarios**:

1. **Given** um professor com código, **When** o administrador reemite, **Then** o código antigo deixa de valer na hora, o novo é mostrado uma única vez e vale 7 dias.
2. **Given** a reemissão, **When** ela é concluída, **Then** todas as sessões Web do professor são encerradas; **And** o vínculo do Telegram só é encerrado se o administrador tiver escolhido isso na confirmação.
3. **Given** um professor com Telegram vinculado, **When** o administrador o desvincula, **Then** aquela conta deixa de ser aceita como o professor, ações pendentes de envio ao chat são descartadas e o professor precisa enviar um código válido para vincular de novo.
4. **Given** um professor inativo, **When** o administrador tenta reemitir o código, **Then** é recusado orientando reativar antes.
5. **Given** uma credencial de professor, **When** tenta reemitir código ou desvincular Telegram (de si ou de outro), **Then** é negada.
6. **Given** o administrador na Web, **When** reemite um código ou desvincula, **Then** a tela pede confirmação explícita nomeando a ação e o professor antes de executar; na reemissão, a confirmação oferece a opção de encerrar também o vínculo do Telegram.

---

### User Story 7 - Administradores (Priority: P2)

O administrador cria outros administradores (nome e e-mail) e inativa administradores. O novo administrador define a **própria** senha com um código de ativação de uso único; ninguém conhece a senha de outro. O sistema nunca fica sem pelo menos um administrador ativo: ninguém inativa a si mesmo, nem a outro, se for o último.

**Why this priority**: a escola precisa de mais de uma pessoa com a chave e de poder desligar quem sai; mas isso vem depois do que o dia a dia exige.

**Independent Test**: criar um administrador, ativá-lo, inativar o primeiro, tentar inativar o último, e tentar duas inativações simultâneas que deixariam zero ativos; tudo na Web.

**Acceptance Scenarios**:

1. **Given** nome e e-mail inéditos entre administradores, **When** o administrador cria outro, **Then** o novo nasce ativo e recebe um código de ativação de uso único, exibido uma única vez, com o qual define a própria senha na Web; antes disso, ele não consegue entrar.
2. **Given** dois ou mais administradores ativos, **When** um inativa outro (ou a si mesmo), **Then** o inativado perde na hora todas as sessões Web.
3. **Given** um único administrador ativo, **When** ele (ou qualquer um) tenta inativá-lo, **Then** é recusado com mensagem clara.
4. **Given** dois administradores ativos que tentam, ao mesmo tempo, inativar um ao outro, **When** as duas operações são processadas, **Then** no máximo uma vale e sobra pelo menos um administrador ativo.
5. **Given** um administrador inativo, **When** outro o reativa, **Then** ele volta a ser ativo e precisa definir a senha de novo por um código de ativação novo.
6. **Given** uma credencial de professor, **When** tenta criar, listar ou inativar administradores, **Then** é negada.

---

### User Story 8 - Primeiro administrador (bootstrap) (Priority: P3)

O mantenedor executa um procedimento operacional que cria o primeiro administrador (e que serve de recuperação quando nenhum administrador consegue entrar). Não existe senha padrão nem segredo no repositório: o procedimento entrega um código de ativação de uso único, exibido apenas a quem o executa, e o administrador define a própria senha.

**Why this priority**: o valor é liberar a escola, mas é uma ação rara e de operação; **na prática é o primeiro passo técnico das demais histórias** e deve ser entregue junto com a Story 1, pois sem ele nenhum administrador existe para testá-las.

**Independent Test**: em um ambiente sem administradores, rodar o procedimento, entrar com o código de ativação, definir a senha e confirmar que não há credencial padrão em nenhum arquivo versionado nem em registro de operação.

**Acceptance Scenarios**:

1. **Given** um ambiente sem administradores, **When** o mantenedor executa o procedimento informando nome e e-mail, **Then** o primeiro administrador nasce e o código de ativação é mostrado só no terminal de quem executa, uma única vez.
2. **Given** o código de ativação, **When** o administrador o usa na Web, **Then** define a própria senha e entra; o código não funciona uma segunda vez e vence em 7 dias.
3. **Given** que nenhum administrador consegue entrar (senha perdida, por exemplo), **When** o mantenedor executa o procedimento para o e-mail de um administrador existente, **Then** é emitido novo código de ativação para ele redefinir a senha, sem criar administrador duplicado.
4. **Given** os arquivos versionados e os registros de operação, **When** são examinados, **Then** não há senha, código de ativação ou segredo do procedimento em nenhum deles.

---

### Edge Cases

- **Aluno e responsável tentam algo**: não existe como tentar: não há papel, credencial, rota, comando ou tela. Qualquer requisição que diga ser deles é tratada como não autenticada e negada. Uma mensagem ao bot de uma conta sem vínculo recebe a orientação de entrada já existente, sem menção a "aluno" ou "responsável" como usuários.
- **Mesma pessoa é professor e administrador**: são duas contas com credenciais separadas (RN-14). O Telegram só vincula a conta de **professor**: a conta de administrador não se vincula ao Telegram, e a mesma pessoa usa o Telegram apenas como professor.
- **CPF digitado no Telegram**: o CPF trafega na conversa do chat. O sistema o guarda só cifrado e só enquanto o cadastro está em andamento; é descartado ao confirmar, cancelar ou vencer o prazo (30 minutos por padrão, configurável), e nunca aparece em mensagem do bot, resumo, log ou registro de operação.
- **Cadastro do professor interrompido no meio (Telegram)**: o professor pode cancelar a qualquer momento; mensagens de outro assunto não gravam nada; o estado em andamento sobrevive ao reinício do transporte (Princípio II).
- **Dois cadastros simultâneos com o mesmo CPF ou o mesmo e-mail**: apenas um vale; o outro recebe a recusa de duplicidade. Dois professores cadastrando o mesmo aluno ao mesmo tempo: um é aceito, o outro recebe a recusa neutra.
- **Professor inativado durante uma conversa no Telegram ou sessão Web**: a próxima ação é negada; nada é gravado; a mensagem orienta procurar o administrador.
- **Código de acesso vencido ou substituído**: o acesso com ele é recusado com mensagem própria, orientando procurar o administrador; vale também para conta já vinculada a outro professor.
- **Reemitir código de professor com relatório em andamento no Telegram**: o relatório continua salvo; o professor retoma depois de voltar a acessar.
- **Data de nascimento limítrofe**: o aluno que faz 18 anos exatamente na data do cadastro é adulto (responsável opcional); a idade é calculada pela data no fuso da escola, não pela do servidor.
- **Edição que torna o aluno menor**: se, ao editar, a data de nascimento passar a indicar menos de 18 anos na data da edição, o responsável passa a ser obrigatório.
- **Tentar sondar CPFs (professor)**: cada recusa por CPF de aluno que não é do professor deixa registro de autoria. A resposta, por necessidade, indica que o CPF não pode ser usado; o risco residual é aceito e documentado (Assumptions).
- **Lista longa de alunos no Telegram**: a lista de alunos do professor é paginada por botões, com o mesmo conteúdo e a mesma ordem da Web.
- **Falha no meio de uma operação com várias partes** (cadastrar aluno e associar professores): ou tudo vale, ou nada é gravado; o usuário é informado e pode repetir.
- **Código de acesso perdido antes de repassar**: não há como recuperá-lo (só existe de forma irreversível); o administrador reemite.
- **Sessão do administrador vence no meio de um formulário**: nada é gravado; ele volta à tela de entrada e os dados digitados não são mantidos.

## Requirements *(mandatory)*

### Functional Requirements

**Papéis e acesso**

- **FR-001**: O sistema DEVE ter exatamente dois papéis autenticáveis: Administrador e Professor. NÃO DEVE existir conta, credencial, login, vínculo de Telegram, tela, rota, comando ou aviso para aluno ou responsável, nem acesso de leitura a eles; o responsável existe apenas como dado do cadastro do aluno.
- **FR-002**: Toda operação desta feature DEVE ser negada por padrão e só executada quando o papel do ator estiver explicitamente autorizado a ela. A identidade e o papel do ator DEVEM vir sempre da sessão (Web) ou do vínculo (Telegram), e a autorização DEVE ser decidida no núcleo, nunca apenas na interface nem no transporte.
- **FR-003**: Credencial de professor NUNCA DEVE autenticar operação administrativa, e credencial de administrador NUNCA DEVE autenticar operação do professor (RN-14), em nenhum dos canais. As áreas administrativa e do professor DEVEM ser separadas.
- **FR-004**: O administrador DEVE entrar na Web com e-mail e senha. A senha DEVE ser armazenada somente com hash forte, e o sistema NÃO DEVE conter senha ou credencial padrão. A recusa de entrada DEVE ter a mesma mensagem para e-mail inexistente, senha errada e conta inativa.
- **FR-005**: A sessão Web do administrador DEVE expirar 2 horas após o login, sem renovação por uso (validade configurável, padrão 2 horas). "Sair" DEVE encerrar a sessão no servidor. Sessão expirada DEVE levar à tela de entrada sem executar a operação pedida.
- **FR-006**: Após 5 tentativas incorretas consecutivas da mesma origem, o sistema DEVE recusar novas tentativas de entrada do administrador por 15 minutos, inclusive com a senha correta, informando o tempo restante. Acesso bem-sucedido e fim do bloqueio DEVEM zerar a contagem. Limite, duração e regras de zeragem DEVEM ser os mesmos das demais autenticações (spec 001, FR-007), configuráveis, reaproveitando o mesmo serviço de tentativas de acesso; a contagem do administrador é separada da do professor.
- **FR-007**: O sistema NÃO DEVE permitir que nenhuma operação, inclusive concorrente, deixe a escola sem pelo menos um administrador ativo.
- **FR-008**: O administrador DEVE operar **somente pela Web**. NÃO DEVE existir vínculo de Telegram para administrador, código de vínculo, menu, comando ou botão administrativo no bot; o identificador de uma conta do Telegram NUNCA DEVE autorizar operação administrativa (RN-11, Princípio II). Qualquer tentativa de operação administrativa pelo Telegram, por conta vinculada ou não, DEVE receber negativa neutra. Uma conta do Telegram DEVE continuar com no máximo um vínculo, sempre de professor.
- **FR-009**: Na Web, as ações destrutivas e de acesso do administrador (inativar, retirar associação, reemitir código, desvincular Telegram, inativar administrador) DEVEM pedir confirmação explícita que nomeie a ação e o alvo antes de executar; sem confirmação, nada é gravado. No Telegram, toda **gravação** do professor (cadastrar, editar, inativar, reativar aluno) DEVE ser precedida de um resumo dos dados (sem o CPF) e de confirmação explícita; sem confirmação, nada é gravado.

**Professores (administrador)**

- **FR-010**: O administrador DEVE poder cadastrar professor com nome e e-mail. O e-mail DEVE ser único entre professores (sem diferenciar maiúsculas, minúsculas e espaços nas pontas), inclusive entre inativos; duplicidade DEVE ser recusada com mensagem clara. O professor DEVE nascer ativo e com código de acesso de 8 caracteres (letras maiúsculas e dígitos, sem caracteres ambíguos), válido por 7 dias.
- **FR-011**: O código de acesso DEVE ser mostrado **uma única vez** ao administrador, na tela da Web onde a operação foi feita, com aviso de que não poderá ser exibido de novo; o sistema DEVE guardá-lo somente de forma não reversível. Código perdido só se resolve reemitindo (FR-030).
- **FR-012**: O administrador DEVE poder listar professores (com busca por nome ou e-mail), vendo nome, e-mail, situação (ativo/inativo) e se há Telegram vinculado. A lista NUNCA DEVE mostrar código de acesso.
- **FR-013**: O administrador DEVE poder editar nome e e-mail do professor, com a mesma regra de unicidade.
- **FR-014**: O administrador DEVE poder inativar e reativar professor. Inativar DEVE encerrar na hora as sessões Web, o vínculo do Telegram e o código de acesso do professor, e recusar novos acessos com mensagem que orienta procurar o administrador. Reativar DEVE devolver o professor à situação ativa **com um código de acesso novo**, mostrado uma única vez; as associações com alunos são preservadas.
- **FR-015**: Inativar professor com **relatório em aberto** (em processamento, com falha no processamento, aguardando respostas ou aguardando conferência) DEVE ser **bloqueado**, com aviso que lista os relatórios em aberto (aluno e etapa) e orienta o professor a concluí-los ou cancelá-los. O administrador NÃO DEVE concluir, aprovar, alterar nem cancelar relatório de professor (Princípio III).
- **FR-016**: O professor NÃO DEVE poder listar, cadastrar, editar, inativar ou reativar professores, nem administradores, códigos de acesso ou associações.

**Alunos (regras comuns ao administrador e ao professor)**

- **FR-017**: O cadastro de aluno DEVE ter: nome (obrigatório), data de nascimento real (obrigatória), CPF (obrigatório), nome do responsável (obrigatório se o aluno tem menos de 18 anos na data do cadastro ou da edição), instrumento (opcional) e professores associados. A idade DEVE ser calculada pela data no fuso da escola.
- **FR-018**: A data de nascimento DEVE existir no calendário (31/02 é recusada), NÃO DEVE ser futura nem anterior a 120 anos atrás. O CPF DEVE ter 11 dígitos com dígitos verificadores corretos, aceitando pontos e traço na digitação, e sequências de dígitos repetidos DEVEM ser recusadas.
- **FR-019**: O CPF DEVE ser único entre todos os alunos, ativos e inativos. A duplicidade DEVE ser detectada sem exibir nem devolver o CPF. Para o administrador, a recusa DEVE indicar o aluno já cadastrado pelo nome. Para o professor, vale FR-024.
- **FR-020**: O CPF DEVE ser armazenado cifrado e NUNCA DEVE ser devolvido por API, tela, mensagem do bot, resumo de confirmação, mensagem de erro ou registro de operação. Na edição, o campo CPF DEVE vir em branco (em branco mantém o atual); informar um valor substitui o CPF, com as mesmas validações.
- **FR-021**: A lista de alunos DEVE ter busca por parte do nome ou instrumento (sem diferenciar maiúsculas, minúsculas e acentos), filtro por situação e paginação, e DEVE mostrar nome, data de nascimento ou idade, instrumento, situação e (para o administrador) os professores associados. Busca por CPF NÃO DEVE existir.
- **FR-022**: Inativar aluno com **relatório em aberto** DEVE ser bloqueado, com aviso listando os relatórios em aberto e quem os conduz. Aluno inativo DEVE deixar de ser oferecido para novos relatórios e de constar das listas padrão dos professores, sem que nada seja apagado; reativar o devolve.
- **FR-023**: O cadastro, a edição e as associações iniciais de um aluno DEVEM valer por inteiro ou não valer: falha no meio NÃO DEVE deixar cadastro parcial.

**Alunos (professor, somente os seus)**

- **FR-024**: O professor DEVE poder cadastrar aluno com os mesmos campos e validações; o aluno DEVE nascer associado ao próprio professor, e só a ele. Se o CPF informado já pertencer a um aluno que não é do professor, o cadastro DEVE ser recusado com mensagem neutra que orienta procurar o administrador, sem nome, professor ou qualquer dado do aluno existente. Se pertencer a aluno do próprio professor, a recusa DEVE informar que o aluno já está entre os seus.
- **FR-025**: O professor DEVE poder listar, buscar, abrir, editar e inativar/reativar **somente** alunos associados a ele. Aluno de outro professor, ou sem professor, DEVE ser tratado como inexistente: a resposta NÃO DEVE diferir da de aluno que não existe, em nenhuma operação e nenhum canal, nem revelar existência ou dados. A verificação DEVE ser feita no núcleo em toda operação (Princípio IV).
- **FR-026**: Se o aluno tem mais de um professor, o professor DEVE poder editar os dados cadastrais, mas só DEVE poder inativá-lo (e reativá-lo) quando for o único professor associado; do contrário a ação é negada, com orientação de procurar o administrador.
- **FR-027**: O professor NÃO DEVE poder incluir ou retirar professores de um aluno, nem cadastrar aluno para outro professor. O professor sem alunos DEVE poder cadastrar o primeiro.

**Associações (administrador)**

- **FR-028**: O administrador DEVE poder incluir e retirar professores de um aluno a qualquer momento. Só professor ativo PODE ser associado. O aluno PODE ficar com zero professores; nesse caso só o administrador o enxerga.
- **FR-029**: Retirar a associação com **relatório em aberto** daquele professor com o aluno NÃO DEVE apagar nem alterar o relatório. O relatório DEVE permanecer acessível ao professor **somente para ser concluído ou cancelado**, sem permitir novo relatório para o aluno, e o acesso do professor ao aluno DEVE terminar quando o relatório sair de aberto. Relatórios já aprovados DEVEM ser preservados sem alteração.

**Código de acesso e vínculo do Telegram (administrador)**

- **FR-030**: O administrador DEVE poder reemitir o código de acesso de qualquer professor ativo: o código anterior deixa de valer na hora, o novo vale 7 dias e é mostrado uma única vez. A reemissão DEVE encerrar as sessões Web do professor e, **por opção do administrador** na confirmação, também o vínculo do Telegram; sem a opção, o vínculo é mantido.
- **FR-031**: O administrador DEVE poder desvincular o Telegram de um professor. A desvinculação DEVE encerrar o vínculo, descartar as ações pendentes de envio ao chat dele e o estado de conversa, e exigir que o professor envie um código de acesso válido para vincular de novo.

**Administradores**

- **FR-032**: O administrador DEVE poder listar administradores (nome, e-mail, situação) e criar outro com nome e e-mail únicos entre administradores. O novo administrador DEVE definir a **própria senha** por um código de ativação de uso único, exibido uma única vez a quem o criou, que vence em 7 dias; ninguém DEVE conhecer ou definir a senha de outro. Sem definir a senha, o administrador novo NÃO DEVE conseguir entrar. A senha DEVE ter no mínimo 12 caracteres.
- **FR-033**: O administrador DEVE poder inativar e reativar administradores. Inativar DEVE encerrar na hora todas as sessões Web do inativado; reativar DEVE exigir nova definição de senha por novo código de ativação. A regra de FR-007 vale para qualquer inativação, inclusive de si mesmo, inclusive sob concorrência.

**Primeiro administrador**

- **FR-034**: DEVE existir um procedimento operacional que cria o primeiro administrador (nome e e-mail) e entrega um código de ativação de uso único, exibido apenas a quem executa e uma única vez. O mesmo procedimento, executado para o e-mail de um administrador existente, DEVE emitir novo código de ativação (recuperação de acesso), sem criar administrador duplicado. Nenhuma senha, código ou segredo do procedimento DEVE estar em arquivo versionado ou em registro de operação.

**Canais e paridade**

- **FR-035**: As operações do **professor** (cadastrar, listar, buscar, editar, inativar e reativar os próprios alunos) DEVEM existir na Web e no Telegram, executando os **mesmos casos de uso**, com as mesmas regras de autorização, validação e resultado; só a forma de interação e o texto se adaptam ao meio (formulário e tabela na Web; conversa guiada, botões e listas paginadas no Telegram). As operações do **administrador** (acesso, professores, alunos, associações, código de acesso, desvinculação do Telegram de professor e administradores) DEVEM existir **somente na Web**, por decisão do dono do produto; é um desvio deliberado do Princípio I, tratado em Assumptions e justificado no plano.
- **FR-036**: Cada cadastro de aluno em andamento no Telegram DEVE ter o estado persistido no backend (Princípio II), poder ser cancelado a qualquer momento por palavra-chave ou botão, e ser descartado ao confirmar, cancelar ou vencer o prazo de inatividade (30 minutos por padrão, configurável). Um cadastro iniciado no Telegram NÃO precisa ser continuado na Web.
- **FR-037**: O bot DEVE oferecer ao professor apenas as opções de aluno (e as de relatório que já tem); NÃO DEVE existir no bot nenhum menu, botão ou comando administrativo. Ao professor vinculado, ou a qualquer conta, um pedido de operação administrativa DEVE receber negativa neutra, sem revelar que a operação existe na Web.

**Privacidade, segurança e autoria**

- **FR-038**: O escopo do professor aos seus alunos DEVE ser imposto no núcleo, em todo caso de uso que lê ou altera dados de aluno, independentemente de interface, canal ou transporte (Princípios II e IV).
- **FR-039**: CPF, nome, data de nascimento e responsável de aluno NUNCA DEVEM aparecer em registros de operação (logs), em mensagens de erro ou em dados de monitoramento; o registro de operação DEVE usar identificadores e códigos de erro. Todas as mensagens de erro ao usuário DEVEM ser em português, sem detalhes internos.
- **FR-040**: Cada operação de gravação desta feature, cada tentativa de CPF recusada por pertencer a aluno de outro professor e cada autenticação de administrador (sucesso ou falha) DEVEM deixar um **registro de autoria**: quem (identificador e papel), canal, momento, operação, tipo e identificador do registro afetado e resultado, sem CPF nem conteúdo de relatório, para a trilha da feature 007. Esta feature não oferece tela de consulta dessa trilha.
- **FR-041**: Duplicidade de e-mail ou de CPF DEVE ser garantida também sob concorrência (duas operações simultâneas): no máximo uma é aceita.

**Mudanças no que já existe**

- **FR-042**: Os textos do professor que citam a "secretaria" DEVEM passar a refletir os papéis novos, em português, nos dois canais: o professor **sem alunos** vê a orientação de cadastrar o primeiro aluno (e não "Fale com a secretaria da escola"); **código vencido, substituído ou conta já vinculada** orientam procurar o administrador. Nenhum texto voltado ao professor deve mencionar "secretaria".
- **FR-043**: O link da tela de entrada do portal DEVE passar a ser "É administrador? Acesse a área administrativa.", levando à entrada do administrador, e DEVE ser exibido agora que a área existe.
- **FR-044**: A carga inicial (FR-011 da spec 001) NÃO DEVE manter regra própria: DEVE passar a usar as mesmas operações desta feature (validações, unicidade, CPF cifrado, código de acesso), com autoria registrada como origem "carga inicial", sem criar papel nem credencial novos. A emissão e a reemissão de código pela carga deixam de ser o caminho normal.

**Interface e identidade visual**

- **FR-045**: As telas novas da Web (área administrativa e "Meus alunos" do professor) DEVEM seguir a identidade do Instituto e o design system do projeto (`portal-do-professor-design-system`), funcionar a partir de 390 px, atender a WCAG 2.1 AA nos modos claro e escuro e tratar estados de carregamento, lista vazia, busca sem resultado, falha e sucesso. As mensagens do bot DEVEM seguir o tom e a terminologia do portal. Ação que falhar NÃO DEVE levar o usuário adiante como se tivesse dado certo.

### Key Entities *(include if feature involves data)*

- **Administrador**: pessoa da escola com função administrativa total. Nome, e-mail (único entre administradores), situação (ativo/inativo), senha (somente em forma não reversível), código de ativação pendente. **Sem vínculo de Telegram**: acesso só pela Web.
- **Professor**: já existe (spec 001). Passa a ter cadastro, edição, inativação e reativação pelo administrador, e a cadastrar alunos. Nome, e-mail (único entre professores), situação, código de acesso com validade, no máximo um vínculo do Telegram.
- **Aluno**: já existe. Nome, data de nascimento, CPF (cifrado, nunca devolvido; usado só para impedir duplicidade), nome do responsável (obrigatório se menor de 18 anos), instrumento (opcional), situação (ativo/inativo). Pode ter zero ou vários professores. **Não é ator.**
- **Responsável**: **não é entidade com conta**; é o nome guardado no cadastro do aluno menor de idade.
- **Associação Professor–Aluno**: já existe; passa a ser mantida pelo administrador (e criada, para o próprio professor, no cadastro do aluno pelo professor). Base de todo o controle de acesso aos alunos.
- **Código de Acesso (professor)**: já existe; 8 caracteres, validade de 7 dias, exibição única, substituído a cada reemissão.
- **Código de Ativação (administrador)**: código de uso único, validade de 7 dias, exibido uma única vez, usado para definir a própria senha.
- **Sessão do Administrador (Web)**: acesso ativo com validade fixa de 2 horas, sem renovação, revogável.
- **Vínculo Telegram**: já existe, **somente para o professor**; o administrador pode desvinculá-lo pela Web. Uma conta do Telegram, um vínculo.
- **Tentativa de Acesso**: contagem de falhas e bloqueio de 15 minutos por origem (Web, para o administrador) ou por conta (Telegram, para o professor); mesmo serviço e mesmas regras, contagens separadas por papel.
- **Cadastro em Andamento (Telegram)**: estado temporário e persistido de um cadastro de aluno conduzido pelo professor no bot; contém o CPF apenas cifrado; descartado ao concluir, cancelar ou vencer.
- **Registro de Autoria**: quem (id e papel), canal, quando, operação, alvo, resultado; sem CPF; alimenta a feature 007.
- **Relatório em Aberto**: relatório de aula ou semestral em processamento, com falha no processamento, aguardando respostas ou aguardando conferência; aparece nesta feature só como condição que bloqueia inativações e que mantém o acesso do professor depois de retirada a associação.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O administrador cadastra um professor e um aluno e entrega o código de acesso ao professor em até 3 minutos, na Web.
- **SC-002**: O professor cadastra um aluno seu em até 3 minutos, em cada canal (Web e Telegram).
- **SC-003**: 100% das operações do administrador estão disponíveis na Web, e 100% das operações do professor estão disponíveis na Web e no Telegram, com o mesmo roteiro de teste produzindo os mesmos resultados de negócio nos dois canais; zero operação administrativa alcançável pelo Telegram.
- **SC-004**: Zero ocorrência de CPF em logs, em respostas de API, em telas, em mensagens do bot e em registros de autoria, verificado por varredura automatizada em todo o conjunto de testes.
- **SC-005**: Zero acesso a operação administrativa com credencial de professor, e zero acesso a operação do professor com credencial de administrador, verificado em teste de acesso cruzado em todas as rotas e comandos.
- **SC-006**: Zero acesso do professor a aluno que não seja seu: em teste de acesso cruzado, 100% das tentativas de ler, editar, inativar, buscar ou cadastrar sobre aluno alheio são negadas ou tratadas como inexistente, e as respostas são indistinguíveis das de aluno inexistente.
- **SC-007**: Zero papel, credencial, conta, rota, comando, tela ou aviso para aluno ou responsável, verificado por teste que enumera todos os papéis e todas as rotas e comandos.
- **SC-008**: Após 5 tentativas incorretas consecutivas, 100% das tentativas seguintes são recusadas por 15 minutos, mesmo com a credencial correta; a sessão do administrador deixa de valer 2 horas após o login em 100% dos casos testados.
- **SC-009**: Em 100% dos casos testados, inclusive sob operações concorrentes, o sistema termina com pelo menos um administrador ativo.
- **SC-010**: Em 100% das reemissões de código, inativações e desvinculações testadas, o acesso anterior deixa de funcionar imediatamente (sessões Web e/ou vínculo, conforme FR-014, FR-030 e FR-031).
- **SC-011**: Depois da entrega, a escola faz o cadastro de professores, alunos e associações sem usar o procedimento de carga inicial (carga convertida para as mesmas operações, sem regra própria).
- **SC-012**: Todas as telas novas da Web passam em auditoria WCAG 2.1 AA, nos modos claro e escuro, em 390 px e 1440 px de largura.

## Assumptions

### Decisões adotadas por padrão, a confirmar no `/speckit-clarify`

Estas decisões estavam abertas no plano (D2 a D5 e itens do pedido) e foram preenchidas com a proposta do plano para a spec poder avançar. Qualquer mudança no `clarify` altera os requisitos indicados.

- **D2 — FECHADA em 2026-10-20 (FR-008, FR-009)**: o administrador opera só pela Web; o Telegram é só do professor, que usa o vínculo que já tem e confirma os dados antes de gravar. Não há autenticação de administrador no Telegram. Esta decisão **diverge do Princípio I e das "Decisões de produto vinculantes" da constituição v2.1.0**, que exigem administração nos dois canais; a divergência precisa de tratamento formal antes do `plan` (ver abaixo).
- **D3 — Revogação (FR-014, FR-030, FR-031)**: reemitir código encerra as sessões Web e, opcionalmente, o vínculo do Telegram; inativar professor ou administrador encerra tudo. Reativar professor não restaura sessões nem vínculo e emite código novo; reativar administrador exige nova definição de senha. O `clarify` deve confirmar se a reativação restaura algo.
- **D4 — Inativar com relatório em aberto (FR-015, FR-022)**: a inativação é **bloqueada**, com aviso listando os relatórios, até serem concluídos ou cancelados pelo professor. Como o administrador não pode cancelar relatório de professor (Princípio III), cabe ao professor concluí-los; se o professor não puder (por exemplo, saiu da escola), o `clarify` deve decidir se existe uma saída. Vale também quando é o professor quem inativa o seu aluno.
- **D5 — Retirar associação com relatório em aberto (FR-029)**: o relatório continua acessível ao professor, somente para concluir ou cancelar, até acabar; os aprovados permanecem preservados. Isso é uma **exceção controlada** à regra "o professor só acessa alunos associados" (spec 001, FR-009), limitada a esses relatórios.
- **Professor com aluno compartilhado (FR-026)**: edita os dados cadastrais, mas só inativa/reativa quando for o único professor associado. O `clarify` deve confirmar (já sinalizado no pedido), inclusive se a edição de CPF e de data também fica livre ao professor nesse caso.
- **Conta do Telegram (FR-008)**: uma conta do Telegram tem no máximo um vínculo, sempre de professor.
- **Prazo de inatividade de cadastro em andamento no Telegram**: 30 minutos, configurável, igual ao do áudio aguardando confirmação (spec 001).

### Demais premissas

- A feature fica sobre o transporte do Telegram da feature 004: o n8n só recebe e entrega; toda regra, autorização e validação deste documento é do backend (Princípio II).
- O professor continua entrando só por código de acesso (spec 001, FR-004 a FR-008, inclusive a sessão Web de 12 horas por inatividade); a sessão fixa de 2 horas vale **apenas** para o administrador.
- O código de acesso do professor continua valendo 7 dias e renovando com o uso (spec 001, FR-005); aqui ele só é emitido e reemitido pelo administrador.
- Senha do administrador: mínimo de 12 caracteres e sem lista de senhas triviais nesta versão; a política detalhada é do plano. Recuperação de senha por autoatendimento ("esqueci a senha") **não** faz parte; a recuperação é o procedimento operacional da Story 8.
- Unicidade de e-mail é por papel: o mesmo e-mail PODE existir como professor e como administrador, pois são contas e credenciais separadas.
- Aluno inativo permanece no sistema, sem ser apagado; o que ele significa para a consulta de histórico é decisão da feature 006. Nesta feature ele só deixa de ser oferecido para novos relatórios e de constar das listas padrão dos professores.
- O CPF é validado só quanto ao formato e aos dígitos verificadores; não há consulta a base externa.
- A recusa de CPF alheio ao professor revela, por necessidade, que aquele CPF não pode ser usado por ele. Esse sinal residual é aceito e mitigado pelo registro de autoria de cada recusa (FR-040), que permite à feature 007 detectar sondagem; o plano deve avaliar um limite de tentativas.
- Mensagens e telas novas seguem a regra de idioma (Princípio VI): textos mostrados e logs em português do Brasil acentuado; código em inglês; termos novos entram no `glossary.json` antes do uso.
- A trilha de auditoria completa, a retenção e a exclusão a pedido são das features 006 e 007; aqui só se gravam os registros de autoria (FR-040).
- As specs 001 e 002 são histórico e não são reescritas. O que esta spec substitui nelas: o texto "falar com a secretaria" (casos de borda e FR de mensagens), o link "É da secretaria?" da tela de entrada, o FR-011 (carga inicial como único caminho de cadastro) e a nota de Assumptions de que a revogação de acessos ao reemitir código "fica para a feature da secretaria". Os catálogos de mensagens e as telas, que são código, mudam por esta feature.
- A Story 8 tem prioridade P3 por valor, mas é o primeiro passo técnico: o plano deve entregá-la junto com a Story 1.
