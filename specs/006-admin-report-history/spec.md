# Feature Specification: Administração — visão da escola (histórico de relatórios aprovados, somente leitura, somente Web)

**Feature Branch**: `feat/006-admin-report-history`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "Visão da escola para o administrador: consultar o histórico de relatórios aprovados de qualquer aluno, de todos os professores, e abrir ou baixar os PDFs, na Web e no Telegram com paridade. Contexto: o professor já consulta o histórico dos seus alunos, com os relatórios de sua autoria (spec 001, FR-047). O administrador (feature 005) ainda não consegue ver nada do que foi produzido. Referências: HANDOFF 5.7 (RN-33 e RN-34), 7.9 e 12.2; spec 001 FR-047 e FR-048. Regras: o administrador vê o histórico de qualquer aluno cadastrado, com os relatórios de aula e semestrais APROVADOS de todos os professores, em ordem cronológica (aula pela data da aula; semestral pelo início do período), com professor autor, data e hora da aprovação e versão aprovada; pode filtrar por aluno, professor, tipo e período; abre e baixa PDFs. Relatórios em rascunho, aguardando respostas, em conferência, com falha ou cancelados NÃO aparecem. O administrador é somente leitura: não aprova, não altera, não cancela e não gera relatório (Princípio III). O download de PDF exige administrador autenticado e autorizado para aquele relatório (FR-048 estendido ao papel administrativo), nunca apenas o conhecimento do endereço. Cada consulta e cada download ficam registrados com autoria para a trilha da feature 007, sem conteúdo do relatório. Os semestrais continuam por professor (decisão D1): o administrador lê todos, sem consolidação entre professores. Privacidade: o histórico contém dados de menores; nunca registrar conteúdo em log; mensagens de erro sem detalhe interno. Fora de escopo: qualquer escrita sobre relatórios; métricas e painel analítico; exportação em lote; envio às famílias. Critérios: o administrador encontra o histórico de um aluno em até 3 interações, em cada canal; 100% dos relatórios listados são aprovados; zero download de PDF sem autorização; paridade total entre canais."

**Materiais de referência**:

- Constituição v2.2.0: Princípio I (funções do Administrador **somente na Web**, inclusive "as consultas administrativas"; o Telegram é canal do Professor e dá negativa neutra a pedido administrativo), II (o núcleo decide autorização), III (só o professor aprova; aprovado é oficial e imutável), IV (privacidade de menores, logs sem dado sensível), V (testes e cobertura), VI (idioma) e IX (identidade visual e WCAG 2.1 AA).
- `specs/001-fluxo-relatorios/spec.md`: FR-045 e FR-046 (ciclo e unicidade do semestral por aluno, professor e período), FR-047 (histórico do professor), FR-048 (entrega de PDF só ao dono autorizado) e FR-049 (PDF de versão não aprovada marcado como "em conferência"). A spec 001 é **histórico** e não é reescrita.
- `specs/005-admin-registrations-access/spec.md`: FR-002 e FR-003 (negação por padrão, credenciais e sessões separadas por papel), FR-005 e FR-046 (sessão do administrador), FR-008 e FR-037 (nada administrativo no Telegram; negativa neutra), FR-039 (dados de aluno fora de logs), FR-040 (registro de autoria), FR-048 e a **Matriz de permissões**, FR-049 (classes de mensagens de erro) e a premissa de que "o que o aluno inativo significa para a consulta de histórico é decisão da feature 006".
- `HANDOFF_MVP_Relatorios_Escola_Musica.md`: 5.7 (RN-33, RN-34), 7.9, 12.2 ("Histórico" da secretaria) e 17.2 (item 2, semestral com vários professores); `PLANO_EXECUCAO_SPECKIT.md`: seção 4.2 (decisões D1, D9 e D10) e seção 9.

**Escopo desta feature**: o **Administrador**, pela **Web**, consulta os relatórios **aprovados** de toda a escola — o histórico de qualquer aluno e a lista filtrada por aluno, professor, tipo e período — e abre ou baixa o PDF da versão aprovada, sempre com autorização decidida no núcleo e com registro de autoria de cada consulta e de cada entrega de PDF. Nada é gravado sobre relatórios. O professor e o Telegram não mudam.

**Divergência com o pedido (canal)**: o pedido fala em "Web e Telegram com paridade". A constituição v2.2.0 (Princípio I, exceção do Administrador) diz que as funções do Administrador, **inclusive as consultas administrativas**, existem somente na Web, e que o Telegram não vincula nem oferece nada ao Administrador. Como a constituição prevalece, esta spec é **somente Web**; os critérios "em cada canal" e "paridade total entre canais" do pedido foram reescritos para a Web e para a ausência de qualquer acesso administrativo pelo Telegram (SC-001, SC-007). Levar esta consulta ao Telegram exige emenda da constituição (ver Assumptions). Confirmado pelo dono do produto no `/speckit-clarify` de 2026-11-03.

## Clarifications

### Session 2026-11-03

- Q: Em qual canal o administrador consulta o histórico e baixa os PDFs (o pedido falava em paridade Web + Telegram)? → A: **Somente Web**, conforme a constituição v2.2.0 (Princípio I) e a D2 da feature 005; o Telegram dá a negativa neutra e o histórico do professor no bot não muda. Sem emenda; a paginação no Telegram deixa de ser questão desta feature (FR-001, SC-001, SC-007).
- Q: O semestral continua um por aluno, professor e período, com o administrador só lendo os de todos os professores (D1)? → A: **Sim, por professor.** Cada semestral aprovado é item próprio, com o seu autor; sem consolidação, sem escolha de um "oficial", sem soma e sem agrupamento especial na tela (FR-008).
- Q: O administrador é estritamente somente leitura, vendo só o PDF da versão aprovada (D10)? → A: **Sim, estrito.** Só a lista e o PDF da versão aprovada; nenhuma escrita (inclusive "cancelamento administrativo"), nenhuma versão anterior e nenhuma transcrição (FR-004, FR-016, FR-018).
- Q: O que gera registro de autoria nas consultas e nas entregas de PDF do administrador, e o que acontece se o registro falhar (D9)? → A: **Cada resposta que entrega dados** gera um registro: abrir o histórico de um aluno, aplicar filtros, trocar de página, abrir e baixar PDF; as negativas também. **Sem registro, nada é entregue** (FR-020 a FR-023).
- Q: Aluno inativo continua consultável na visão da escola, e aparece na busca normal? → A: **Sim, consultável e na busca normal**, junto com os ativos, marcado como "inativo"; o histórico aprovado é registro da escola e não some com a inativação (FR-009, FR-010).

## Quem usa esta feature

- **Administrador**: pessoa da escola (direção ou secretaria), autenticada na área administrativa da Web (feature 005). Passa a **ler** o que a escola produziu: relatórios aprovados de qualquer aluno e de qualquer professor. Não aprova, não altera, não cancela, não gera e não envia relatório.
- **Professor**: **não muda**. Continua vendo, nos dois canais, só o histórico dos seus alunos com os relatórios de sua autoria (spec 001, FR-047), e continua o único que recebe PDF de versão não aprovada dos próprios relatórios (spec 001, FR-048).
- **Aluno e responsável: não são atores** (feature 005, FR-001). São sujeitos dos dados consultados.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Histórico de um aluno, de todos os professores (Priority: P1)

O administrador entra na área administrativa, abre "Histórico", procura o aluno pelo nome e vê a lista de todos os relatórios aprovados dele — de aula e semestrais, de todos os professores que já o atenderam —, do mais recente ao mais antigo, cada um com o tipo e a data (ou o período), o professor autor, a data e hora da aprovação e a versão aprovada.

**Why this priority**: é o que a escola não consegue fazer hoje (RN-33, RN-34): ver o que foi produzido sobre um aluno sem pedir a cada professor. Sozinha, já entrega a "visão da escola" pedida.

**Independent Test**: com uma base de teste com um aluno atendido por dois professores, relatórios aprovados de aula e semestrais de ambos e relatórios em todos os estados não aprovados, o administrador procura o aluno e confere que a lista traz exatamente os aprovados, na ordem certa e com os dados exigidos.

**Acceptance Scenarios**:

1. **Given** um aluno com relatórios de aula aprovados dos professores A e B e um semestral aprovado de cada um para o mesmo período, **When** o administrador abre o histórico do aluno, **Then** vê todos esses relatórios, cada semestral como item separado com o seu professor autor (sem consolidação entre professores, D1), ordenados do mais recente ao mais antigo (aula pela data da aula; semestral pelo início do período).
2. **Given** o mesmo aluno também tem relatórios em processamento, com falha no processamento, aguardando respostas, aguardando conferência e cancelados, **When** o administrador abre o histórico, **Then** nenhum deles aparece, nem em contagem, nem em indicação de existência.
3. **Given** um relatório que teve as versões 1, 2 e 3 e foi aprovado na versão 3, **When** ele aparece no histórico, **Then** mostra "versão 3" como versão aprovada e a data e hora da aprovação.
4. **Given** um aluno sem nenhum relatório aprovado, **When** o administrador abre o histórico dele, **Then** vê a mensagem "Nenhum relatório aprovado encontrado para este aluno."
5. **Given** o administrador na tela inicial da área administrativa, **When** ele vai ao histórico, procura o aluno e o escolhe, **Then** a lista aparece em no máximo 3 interações.
6. **Given** um aluno inativo, ou um aluno cujo professor foi inativado ou desassociado, **When** o administrador o procura, **Then** o aluno aparece (identificado como inativo, quando for o caso) e o histórico traz também os relatórios aprovados dos professores inativos ou desassociados, com o nome do professor autor.

---

### User Story 2 - Abrir e baixar o PDF aprovado, só com autorização (Priority: P1)

Na lista, o administrador abre o PDF de um relatório para ler na tela ou o baixa. Recebe sempre o PDF da versão aprovada. Quem não é administrador autenticado, ou pede o PDF de algo que não é um relatório aprovado, não recebe nada, mesmo conhecendo o endereço.

**Why this priority**: o PDF é o documento oficial; sem ele a lista só diz que o relatório existe. A autorização por relatório é a principal proteção de dado de menor desta feature.

**Independent Test**: com a mesma base, o administrador abre e baixa o PDF de um relatório aprovado; em seguida, testes de acesso cruzado pedem o mesmo PDF sem sessão, com sessão de professor, com sessão de administrador expirada e, com sessão de administrador, pedem PDFs de versões não aprovadas e de relatórios não aprovados.

**Acceptance Scenarios**:

1. **Given** um relatório aprovado na versão 3, **When** o administrador escolhe "Abrir" ou "Baixar", **Then** recebe o PDF da versão 3, idêntico ao que o professor autor recebe, sem marca de "em conferência".
2. **Given** o endereço do PDF de um relatório aprovado, **When** ele é pedido sem sessão, ou com sessão de administrador expirada ou encerrada, **Then** nada é entregue e o pedido recebe "não autenticado" (na tela, leva à entrada do administrador).
3. **Given** o mesmo endereço, **When** ele é pedido com credencial de professor (inclusive do professor autor) pela rota administrativa, **Then** recebe a mesma resposta de uma rota que não existe (feature 005, FR-003).
4. **Given** um administrador autenticado, **When** ele pede o PDF de uma versão não aprovada (1 ou 2 do exemplo), de um relatório em qualquer estado não aprovado ou de um relatório que não existe, **Then** recebe a mesma resposta neutra de "não encontrado", indistinguível entre os casos.
5. **Given** um administrador autenticado, **When** ele pede um PDF pela rota de documentos do professor, **Then** recebe a mesma resposta de uma rota que não existe (credencial de um papel na área do outro).

---

### User Story 3 - Filtros: aluno, professor, tipo e período (Priority: P2)

O administrador refina a consulta: escolhe um professor para ver tudo o que ele aprovou, restringe ao tipo (aula ou semestral) e a um intervalo de datas, combinando os filtros com ou sem aluno escolhido. Sem aluno escolhido, a lista mostra os relatórios aprovados de toda a escola que atendem aos filtros, com o nome do aluno em cada item.

**Why this priority**: torna a visão da escola útil para acompanhar um professor ou um período (ex.: semestrais do último semestre), mas o histórico por aluno (Story 1) já entrega o valor central.

**Independent Test**: com uma base de vários alunos e professores, aplicar cada filtro sozinho e combinado e conferir que o resultado é exatamente o subconjunto esperado dos aprovados, na mesma ordem.

**Acceptance Scenarios**:

1. **Given** o filtro professor A, **When** a lista é carregada, **Then** traz só os relatórios aprovados de autoria de A, de todos os alunos dele (atuais ou antigos), cada item com o nome do aluno.
2. **Given** o filtro tipo "Semestral", **When** a lista é carregada, **Then** traz só relatórios semestrais aprovados; com "Aula", só os de aula; sem filtro de tipo, os dois.
3. **Given** o período de 01/02/2026 a 30/06/2026, **When** a lista é carregada, **Then** traz os relatórios de aula com data da aula nesse intervalo e os semestrais cujo início do período está nesse intervalo (datas inclusive).
4. **Given** uma data inicial posterior à final, ou uma data inválida, **When** o administrador aplica o filtro, **Then** recebe erro de validação que diz o campo e o motivo, e nada é consultado.
5. **Given** aluno X, professor A, tipo "Aula" e um período, **When** a lista é carregada, **Then** traz só os relatórios que atendem a todos os filtros ao mesmo tempo.
6. **Given** filtros que não casam com nenhum relatório, **When** a lista é carregada, **Then** o administrador vê "Nenhum relatório aprovado encontrado para estes filtros." e pode limpar os filtros.
7. **Given** mais relatórios do que cabem numa página, **When** a lista é carregada, **Then** aparece paginada, com o total de resultados, mantendo a ordem e os filtros entre as páginas.

---

### User Story 4 - Registro de autoria das consultas e das entregas de PDF (Priority: P2)

Cada consulta do administrador ao histórico e cada entrega de PDF (abrir ou baixar) deixa um registro de autoria — quem, quando, o quê, sobre qual alvo, com que resultado —, sem nenhum conteúdo de relatório, para a trilha da feature 007. As negativas também ficam registradas.

**Why this priority**: a escola precisa saber quem leu dados de menores (D9; a feature 005 deixou explicitamente as leituras para depois). Não muda o que o administrador vê, por isso vem depois das Stories 1 a 3, mas é obrigatória para a entrega.

**Independent Test**: executar uma sequência de consultas, filtros, aberturas, downloads e pedidos negados e conferir que cada um gerou exatamente um registro com os campos exigidos, e que nenhum registro, log ou mensagem contém nome do aluno, CPF, dado do responsável ou conteúdo do relatório.

**Acceptance Scenarios**:

1. **Given** o administrador abre o histórico de um aluno, **When** a lista é entregue (e a cada nova página ou novo filtro), **Then** existe um registro com o administrador, o papel, o canal (Web), o momento, a operação "consulta do histórico", o identificador do aluno, os filtros aplicados (por identificadores, tipo e datas) e o resultado.
2. **Given** o administrador abre ou baixa um PDF, **When** o PDF é entregue, **Then** existe um registro com a operação (abertura ou download), o identificador do relatório e da versão entregue e o resultado.
3. **Given** um pedido de PDF negado (não aprovado, inexistente, credencial de outro papel), **When** a resposta é dada, **Then** existe um registro de negativa, sem distinguir no registro visível ao usuário qual foi o motivo.
4. **Given** o registro de autoria não pode ser gravado, **When** a consulta ou a entrega é pedida, **Then** nada é entregue e o administrador vê a mensagem de falha interna com código de referência.

---

### Edge Cases

- **Semestrais de vários professores para o mesmo aluno e período**: aparecem todos, cada um com o seu autor, lado a lado na ordem; o sistema não consolida, não escolhe um "oficial" nem soma (D1).
- **Empate de data** (duas aulas no mesmo dia, ou aula e semestral na mesma data): a ordem segue a data e hora de aprovação, da mais recente para a mais antiga, e depois um critério estável, para que a lista seja a mesma a cada consulta e entre páginas.
- **Relatório aprovado enquanto o administrador pagina**: a lista é recalculada a cada página; um item novo pode deslocar os demais, mas nenhum relatório não aprovado aparece e nenhum item é entregue fora dos filtros.
- **Aluno sem professor associado** (associações retiradas): continua encontrável e o histórico mostra os aprovados de quando era atendido (D5: aprovados são preservados).
- **Professor inativo**: continua no filtro de professores, identificado como inativo, e os relatórios aprovados dele continuam visíveis.
- **Homônimos**: a busca de aluno mostra, para cada resultado, informação suficiente para distinguir homônimos (situação e professores associados), nunca o CPF.
- **Endereço de PDF compartilhado ou guardado**: abrir o endereço depois que a sessão expirou, em outro navegador ou por outra pessoa não entrega o PDF; o endereço não carrega credencial.
- **PDF da versão aprovada indisponível** (arquivo ausente ou ilegível): o administrador recebe falha interna com código de referência; o sistema não gera, não regenera e não entrega outra versão no lugar.
- **Sessão do administrador expira durante a consulta**: a próxima ação leva à entrada sem entregar dado; ao entrar de novo, a consulta é refeita.
- **Mesma pessoa com sessão de professor e de administrador no mesmo navegador**: cada área usa só a sua credencial (feature 005, FR-003); o histórico do professor continua mostrando só os relatórios de sua autoria, e a visão da escola, tudo o que foi aprovado.
- **Pedido de "histórico da escola" pelo Telegram**, por professor vinculado ou conta sem vínculo: negativa neutra (feature 005, FR-037); o histórico do professor no bot não muda.
- **Tentativa de escrita pela área administrativa** (aprovar, alterar, cancelar, gerar, reenviar) por qualquer meio, inclusive chamada direta: não existe operação; a resposta é a de rota inexistente e nada é gravado sobre o relatório.
- **Filtro com identificador manipulado** (aluno ou professor inexistente): lista vazia, sem erro que revele existência; o pedido gera registro de autoria normal.

## Requirements *(mandatory)*

### Functional Requirements

**Acesso e canal**

- **FR-001**: A visão da escola DEVE existir **somente na Web**, na área administrativa da feature 005, e somente para o papel Administrador. NÃO DEVE existir no Telegram menu, comando, botão ou mensagem que ofereça, mencione ou execute esta consulta; pedido equivalente pelo Telegram DEVE receber a negativa neutra da feature 005 (FR-037).
- **FR-002**: Toda operação desta feature DEVE ser negada por padrão e só executada para administrador autenticado e ativo, com a identidade e o papel vindos da sessão do administrador, validada em toda requisição (feature 005, FR-005, FR-046 e FR-047). A autorização DEVE ser decidida no núcleo, nunca apenas na interface.
- **FR-003**: Credencial de professor nas rotas desta feature, e credencial de administrador nas rotas de histórico e de documentos do professor, DEVEM receber a mesma resposta de uma rota que não existe; requisição sem credencial recebe "não autenticado" (feature 005, FR-003).
- **FR-004**: A feature DEVE ser **somente leitura** (Princípio III, D10): NÃO DEVE existir, para o administrador, operação que crie, gere, altere, aprove, cancele, reprocesse, reenvie ou apague relatório, versão ou PDF, em nenhuma rota. A consulta NÃO DEVE alterar estado, versão, data de aprovação nem qualquer dado do relatório.

**Conteúdo da lista**

- **FR-005**: A lista DEVE conter **somente** relatórios de aula e semestrais **aprovados**, de qualquer professor e de qualquer canal de origem (RN-33). Relatórios em processamento, com falha no processamento, aguardando respostas, aguardando conferência ou cancelados NÃO DEVEM aparecer, nem ser contados, nem ter a existência revelada.
- **FR-006**: Cada item DEVE mostrar: o tipo e a referência temporal ("Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)"), o aluno (quando a lista não estiver filtrada por um único aluno), o professor autor, a data e hora da aprovação e o número da versão aprovada, além das ações "Abrir PDF" e "Baixar PDF".
- **FR-007**: A ordem DEVE ser a do histórico do professor (spec 001, FR-047): do mais recente ao mais antigo, pela data da aula (aula) ou pelo início do período (semestral); em empate, pela data e hora de aprovação, da mais recente para a mais antiga, e depois por um critério estável, de modo que a mesma consulta devolva sempre a mesma ordem.
- **FR-008**: Os relatórios semestrais DEVEM continuar sendo por aluno, professor e período (spec 001, FR-046; D1): o administrador vê cada semestral aprovado como item próprio, com o seu professor autor, sem consolidação, escolha ou soma entre professores.
- **FR-009**: O histórico DEVE incluir os relatórios aprovados de **qualquer aluno cadastrado**, ativo ou inativo, com ou sem professor associado no momento, e de qualquer professor, ativo, inativo ou já desassociado do aluno (RN-34). Aluno e professor inativos DEVEM ser identificados como tal na tela.

**Busca e filtros**

- **FR-010**: O administrador DEVE poder procurar aluno pelo nome, sem diferenciar maiúsculas, minúsculas e acentos e aceitando parte do nome, entre todos os alunos cadastrados (ativos e inativos). Cada resultado DEVE mostrar o nome, a situação e os professores associados, para distinguir homônimos, e NUNCA o CPF.
- **FR-011**: O administrador DEVE poder filtrar a lista por **aluno**, **professor** (entre todos os professores, ativos e inativos), **tipo** (aula, semestral ou ambos) e **período** (data inicial e data final, inclusive), sozinhos ou combinados; os filtros DEVEM ser aplicados juntos (todos ao mesmo tempo). Sem filtro de aluno, a lista DEVE trazer os relatórios aprovados de toda a escola que atendem aos demais filtros.
- **FR-012**: O filtro de período DEVE usar a mesma data da ordenação: a data da aula, para relatório de aula, e o início do período, para semestral. Data inválida ou data inicial posterior à final DEVE ser recusada como erro de validação (feature 005, FR-049, classe a), sem executar a consulta.
- **FR-013**: A lista DEVE ser paginada, informar o total de resultados e manter os filtros e a ordem entre as páginas; o tamanho da página é definido no plano.
- **FR-014**: Filtro com aluno ou professor que não existe DEVE devolver lista vazia, igual à de filtros sem resultado, sem revelar se o identificador existe.
- **FR-015**: Os filtros da lista NÃO DEVEM levar nome de aluno, CPF ou qualquer dado pessoal para o endereço da página ou para parâmetros de consulta; aluno e professor são referidos por identificador opaco. A busca de aluno pelo nome é a da feature 005 e segue a regra dela (o termo de busca nunca pode ter forma de CPF).

**PDF**

- **FR-016**: O administrador DEVE poder abrir (exibir no navegador) e baixar o PDF de qualquer relatório que aparece na lista. O PDF entregue DEVE ser sempre o da **versão aprovada**, o mesmo documento que o professor autor recebe (spec 001, FR-049), com o mesmo nome sugerido de arquivo do portal do professor. O administrador NÃO DEVE ter acesso a versões anteriores, à transcrição, às perguntas pendentes nem às instruções de alteração do relatório.
- **FR-017**: A entrega de PDF ao administrador DEVE exigir, a cada pedido, administrador autenticado, ativo e autorizado para aquele relatório (spec 001, FR-048, estendido ao papel administrativo): o relatório existe, está aprovado e a versão pedida é a aprovada. Conhecer o endereço, o nome do arquivo ou o identificador NUNCA DEVE bastar. O endereço NÃO DEVE conter credencial nem valer fora da sessão.
- **FR-018**: Pedido de PDF de versão não aprovada, de relatório não aprovado (em qualquer estado) ou de relatório inexistente DEVE receber a mesma resposta neutra de "não encontrado" (mesmo código, corpo e cabeçalhos relevantes, e tempo de resposta na mesma ordem de grandeza), sem revelar qual dos casos ocorreu.
- **FR-019**: O PDF entregue ao administrador NÃO DEVE ser guardado em cache compartilhado nem reaproveitado pelo navegador para outra sessão; a resposta DEVE instruir o navegador a não armazená-lo.

**Registro de autoria**

- **FR-020**: Cada consulta do administrador (abertura do histórico de um aluno, aplicação de filtros, troca de página) e cada entrega de PDF (abertura ou download) DEVE deixar um **registro de autoria**, no mesmo formato da feature 005 (FR-040), para a trilha da feature 007: quem (identificador e papel), canal (Web), quando, operação (consulta do histórico, abertura de PDF, download de PDF), alvo (identificador do aluno e filtros aplicados, por identificadores, tipo e datas; ou identificador do relatório e da versão entregue) e resultado. Toda resposta que entrega relatórios ou PDF gera um registro, inclusive cada página da lista. A busca de aluno pelo nome (FR-010) é a mesma leitura de alunos da feature 005 e segue a regra dela (sem registro até a feature 007).
- **FR-021**: Também DEVEM gerar registro as negativas desta feature: credencial de outro papel nas rotas desta feature e pedido de PDF negado (não aprovado, versão não aprovada ou inexistente, sem distinguir os casos para o usuário).
- **FR-022**: O registro de autoria NÃO DEVE conter conteúdo de relatório, transcrição, nome do aluno, CPF, data de nascimento nem dado do responsável; usa identificadores e códigos.
- **FR-023**: Consulta ou entrega de PDF e o seu registro de autoria DEVEM valer juntos: se o registro não puder ser gravado, nada DEVE ser entregue e o administrador DEVE ver a mensagem de falha interna com código de referência.

**Privacidade e mensagens**

- **FR-024**: Os registros de operação (logs), mensagens de erro, rastros de exceção e dados de monitoramento NÃO DEVEM conter conteúdo de relatório, nome do aluno, CPF, data de nascimento nem dado do responsável (feature 005, FR-039); usam identificadores e códigos de erro.
- **FR-025**: As mensagens de erro DEVEM seguir as classes da feature 005 (FR-049), em português, sem detalhe interno: validação de filtro, sessão expirada ou não autenticado, negado ou inexistente (neutra), falha interna com código de referência.

**Não regressão**

- **FR-026**: O histórico do professor (spec 001, FR-047), a entrega de PDF ao professor (spec 001, FR-048), o ciclo de vida do relatório e o bot do Telegram NÃO DEVEM mudar: o professor continua vendo só os relatórios de sua autoria dos seus alunos, nos dois canais.

**Interface**

- **FR-027**: A tela nova DEVE seguir a identidade do Instituto e o design system do projeto (`portal-do-professor-design-system`), funcionar a partir de 390 px, atender a WCAG 2.1 AA nos modos claro e escuro e tratar os estados de carregamento, lista vazia ("Nenhum relatório aprovado encontrado para este aluno." / "Nenhum relatório aprovado encontrado para estes filtros."), busca sem resultado, falha e sessão expirada. Ação que falhar NÃO DEVE parecer bem-sucedida.
- **FR-028**: O administrador DEVE chegar ao histórico de um aluno, a partir da tela inicial da área administrativa, em no máximo 3 interações (ex.: abrir "Histórico", procurar o aluno, escolher o resultado). A área de alunos da feature 005 PODE oferecer um atalho "Histórico" na ficha do aluno, que leva à mesma lista já filtrada.

### Matriz de permissões

Complementa a matriz da feature 005 (FR-048), que segue sendo a fonte única; as linhas abaixo entram nela. O que não consta é negado.

| Operação | Administrador (Web) | Professor (Web) | Professor (Telegram) | Sem credencial |
|---|---|---|---|---|
| Consultar relatórios aprovados da escola (histórico de qualquer aluno; filtros por aluno, professor, tipo e período) | sim, somente leitura | não (continua com o próprio histórico, spec 001 FR-047) | não (idem) | não |
| Abrir e baixar PDF da versão aprovada de qualquer relatório aprovado | sim | não por esta rota (continua só com os próprios, spec 001 FR-048) | não por esta rota (idem) | não |
| Abrir PDF de versão não aprovada ou de relatório não aprovado de outro professor | não | não | não | não |
| Criar, gerar, alterar, aprovar, cancelar ou reenviar relatório | não (Princípio III) | só os seus (spec 001) | só os seus (spec 001) | não |

### Key Entities *(include if feature involves data)*

- **Relatório de Aula aprovado**: já existe (spec 001). Aqui é lido: aluno, professor autor, data da aula, versão aprovada, data e hora da aprovação, PDF da versão aprovada.
- **Relatório Semestral aprovado**: já existe (spec 001). Aqui é lido: aluno, professor autor, período (início e fim), versão aprovada, data e hora da aprovação, PDF da versão aprovada. Um por aluno, professor e período (D1).
- **Item do Histórico**: visão de leitura que une os dois tipos aprovados com os dados do FR-006 e a chave de ordenação do FR-007; não é guardado.
- **Filtro da Consulta**: aluno, professor, tipo e período, todos opcionais e combináveis; referidos por identificador opaco.
- **Registro de Autoria**: já existe (feature 005, FR-040). Ganha as operações "consulta do histórico", "abertura de PDF" e "download de PDF", com os alvos do FR-020; sem conteúdo nem dado pessoal.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O administrador encontra o histórico de um aluno em até 3 interações a partir da tela inicial da área administrativa, na Web, em 100% dos roteiros de teste (inclusive com homônimos e aluno inativo).
- **SC-002**: 100% dos relatórios listados, em todas as combinações de filtros testadas, estão aprovados; zero relatório em processamento, com falha, aguardando respostas, aguardando conferência ou cancelado aparece, verificado com uma base que tem relatórios em todos os estados.
- **SC-003**: Zero entrega de PDF sem autorização: em teste de acesso cruzado, 100% dos pedidos sem sessão, com sessão expirada ou encerrada, com credencial de professor, de versão não aprovada, de relatório não aprovado e de relatório inexistente são negados, e as respostas de "não encontrado" são indistinguíveis entre si (FR-018).
- **SC-004**: Zero operação de escrita sobre relatório alcançável pelo administrador: o teste gerado da Matriz de permissões confirma que nenhuma rota administrativa cria, altera, aprova, cancela ou gera relatório, e que nenhum relatório muda de estado, versão ou data por causa de uma consulta.
- **SC-005**: Em 100% das consultas, entregas de PDF e negativas testadas existe exatamente um registro de autoria com operação, alvo e resultado corretos; e zero ocorrência de conteúdo de relatório, nome do aluno, CPF, data de nascimento ou dado do responsável em registros de autoria, logs, mensagens de erro e rastros de exceção, verificado por varredura automatizada no conjunto de testes.
- **SC-006**: O histórico do professor continua idêntico ao de antes da feature (mesmos itens, mesma ordem) nos dois canais, verificado pelos testes existentes da spec 001.
- **SC-007**: Zero acesso administrativo pelo Telegram: nenhum comando, botão ou texto do bot dá acesso à visão da escola, e os pedidos equivalentes recebem a negativa neutra.
- **SC-008**: A primeira página da lista aparece em até 2 segundos em 95% das consultas, com uma base de pelo menos 10.000 relatórios aprovados.
- **SC-009**: A tela nova passa em auditoria WCAG 2.1 AA, nos modos claro e escuro, em 390 px e 1440 px de largura.
- **SC-010**: Para um mesmo aluno, o conjunto de relatórios que o administrador vê é exatamente a união dos históricos que cada professor autor vê dele (spec 001, FR-047), somados os de professores hoje inativos ou desassociados.

## Assumptions

### Decisões fechadas no `/speckit-clarify` e decisões adotadas por padrão

- **Canal — FECHADA em 2026-11-03: somente Web (Princípio I, constituição v2.2.0).** O pedido pedia paridade com o Telegram; a constituição põe as consultas administrativas somente na Web e proíbe vínculo, menu ou comando de Administrador no bot. Para levar a consulta ao Telegram seria preciso emendar a constituição (`/speckit-constitution`) **e** criar a autenticação do administrador no bot, que a feature 005 decidiu não existir (D2). O `PLANO_EXECUCAO_SPECKIT.md` (seção 9) ainda cita "Paridade Web + Telegram"; ele é anterior à v2.2.0, cujo Sync Impact Report já registra "features 006 e 007 só Web para o administrador".
- **D1 — FECHADA em 2026-11-03**: semestral por aluno, professor e período; o administrador lê todos, cada um como item próprio, sem consolidação nem agrupamento (FR-008). Um semestral consolidado da escola, se vier, é feature própria.
- **D10 — FECHADA em 2026-11-03**: o administrador é estritamente somente leitura (FR-004) e recebe só o PDF da versão aprovada (FR-016); correção de relatório aprovado ("retificação" ou cancelamento administrativo, HANDOFF 17.2 item 11) fica fora, para feature própria.
- **D9 para esta feature — FECHADA em 2026-11-03**: cada consulta (inclusive cada página) e cada entrega de PDF gera registro de autoria, sem conteúdo (FR-020 a FR-023). Esta feature encerra, para o administrador, a lacuna "leituras sem rastro até a feature 007" aceita na feature 005; as leituras do professor continuam sem registro até a 007.
- **Registro e entrega valem juntos (FR-023), FECHADA em 2026-11-03 com a D9**: segue a regra de atomicidade da feature 005 para gravações; sem registro, a leitura não é entregue. A alternativa (entregar e só registrar o erro em log) foi descartada porque deixaria leitura de dado de menor sem rastro.
- **Lista da escola sem aluno escolhido (FR-011)**: o pedido lista "aluno" entre os filtros; por isso a consulta aceita filtro só por professor, tipo ou período, trazendo relatórios de toda a escola. O caminho principal continua sendo o histórico de um aluno (Story 1).
- **Aluno inativo (FR-009, FR-010) — FECHADA em 2026-11-03**: continua consultável e aparece na busca normal, com marcação de inativo (sem opção "incluir inativos"); o histórico é registro da escola e não some com a inativação. Resolve a pendência que a feature 005 deixou para esta.
- **Professor inativo ou desassociado (FR-009)**: os aprovados dele continuam visíveis; a autoria não muda.
- **Período (FR-012)**: o filtro usa a mesma data da ordenação; um semestral cujo período começa antes do intervalo e termina dentro dele não entra. Escolhido por ser previsível e igual à ordenação.
- **Ordem (FR-007)**: "ordem cronológica" do pedido segue a do histórico do professor (do mais recente ao mais antigo), para que as duas telas leiam do mesmo jeito.

### Demais premissas

- Depende da feature 005 mesclada: área administrativa, sessão do administrador, Matriz de permissões e registro de autoria.
- O PDF da versão aprovada já existe para todo relatório aprovado (spec 001, FR-045 e FR-049); esta feature não gera nem regenera PDF.
- O volume de referência do SC-008 (10.000 relatórios aprovados) cobre com folga alguns anos de uma escola do porte do Instituto; o plano confirma o número.
- Fora de escopo: qualquer escrita sobre relatórios; métricas, contagens e painel analítico; exportação em lote (vários PDFs ou planilha); envio às famílias; tela de consulta da trilha de autoria e retenção (feature 007); histórico no Telegram para o administrador.
- **Emendas a specs anteriores, a fazer no PR de implementação** (formato `> **Nota (feature 006)**`, specs/CLAUDE.md): na spec 001, FR-048 (a entrega de PDF passa a admitir o administrador para a versão aprovada); na spec 005, a linha "Relatórios de aula e semestrais (spec 001)" da Matriz de permissões e a premissa sobre aluno inativo no histórico.
- Termos novos de código (ex.: histórico da escola, entrega de PDF) entram no `glossary.json` antes do uso (Princípio VI); textos ao usuário e logs em português acentuado.
