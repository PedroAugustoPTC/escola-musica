# Feature Specification: API interna de canal do Telegram para orquestrador externo

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

**Feature Branch**: `002-api-interna-canal-telegram`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "Criar a API interna de canal do Telegram no backend, para que um orquestrador externo (n8n) passe a ser o único transporte do Telegram sem alterar nada do que o professor vê hoje." (descrição completa preservada no pedido de `/speckit-specify`; resumida nas seções abaixo)

**Materiais de referência**:

- Constituição v1.4.0, Princípio II ("Fonte Única da Verdade"): o n8n é admitido SOMENTE como transporte do canal Telegram; recebe o update, o repassa ao backend e executa as ações que o backend devolver. Esta feature reverte a decisão R-04 de `specs/001-fluxo-relatorios/research.md` ("sem n8n; webhook direto no backend").
- `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` e `contracts/mensagens.md`: fonte de verdade do comportamento da conversa (10 estados, botões `v1:<acao>:<arg>`, palavras-chave, retomada, semestral, histórico e avisos entre canais). **Nada disso muda nesta feature.**
- `specs/001-fluxo-relatorios/spec.md` (FR-015, FR-018, FR-021, FR-036 a FR-040): regras de áudio, avisos entre canais e conversa, que continuam valendo.

**Escopo desta feature**: somente a **API interna do backend** que permite a um orquestrador externo fazer o transporte do Telegram. Fora de escopo (feature `004`): criar os workflows do n8n, alterar a composição de contêineres e o proxy, trocar o webhook registrado no Telegram, remover o caminho antigo de envio direto ao Telegram. O webhook atual continua funcionando integralmente durante e depois desta feature.

## Clarifications

### Session 2026-10-02

- Q: Quando o n8n repete um update já processado, como evitar que o professor receba as mesmas mensagens duas vezes no chat? → A: Toda ação de resposta também tem id estável (derivado de `update_id` + posição) e confirmação de execução ao backend; o update repetido devolve só as ações ainda não confirmadas (lista vazia se todas confirmadas).
- Q: Quando uma ação da lista falha ao ser executada no Telegram, o que o orquestrador faz com as ações seguintes? → A: Continua com as seguintes, em ordem, sem parar; informa cada falha ao backend na confirmação, com código de motivo (ex.: `MENSAGEM_NAO_EDITAVEL`); o backend decide a compensação (ex.: nova mensagem no lugar da edição).
- Q: Por quanto tempo e com quais intervalos o backend tenta entregar uma ação proativa antes de marcá-la como falha definitiva? → A: Intervalo inicial de 30 s, dobrando a cada tentativa até o teto de 15 min, com prazo total de 24 h (valores padrão, configuráveis por ambiente); depois disso a ação vira falha definitiva.
- Q: Na rotação do token de serviço, o backend aceita dois tokens válidos ao mesmo tempo durante a troca? → A: Sim, até dois tokens simultâneos (atual e próximo), cada um comparado em tempo constante, para trocar sem janela de indisponibilidade.
- Q: Dentro do formato v1, o que pode mudar sem nova versão, e o que o orquestrador faz ao receber um tipo de ação desconhecido? → A: Mudanças aditivas (campo opcional novo, tipo de ação novo) ficam na v1; o orquestrador ignora campos desconhecidos e, diante de ação desconhecida, a pula, informa `ACAO_NAO_SUPORTADA` na confirmação e continua; mudança incompatível cria nova versão, e a anterior é mantida durante a migração.

### Session 2026-10-08

- Q: Como ler FR-029, já que a própria ação carrega o texto que o professor verá (nome do aluno, trechos do relatório)? → A: Logs, métricas e mensagens de erro nunca contêm dado de aluno; o payload da ação pode conter o texto que a mensagem exige, só enquanto necessário, e é zerado após a retenção; ação entregue e nunca confirmada também expira no prazo de entrega e é zerada. A alternativa de guardar só referências e renderizar o texto no despacho foi rejeitada (reabriria o estado da conversa já gravado).
- Q: O que acontece quando o orquestrador não consegue obter o PDF (`DOCUMENTO_INDISPONIVEL`)? → A: O backend emite uma compensação: aviso curto ao professor com o botão para pedir o PDF de novo, com texto novo e aditivo no catálogo de mensagens; o orquestrador continua sem decidir nada.
- Q: A lista de códigos de motivo da confirmação está fechada? → A: Fica a lista atual (`MENSAGEM_NAO_EDITAVEL`, `CHAT_INDISPONIVEL`, `ACAO_NAO_SUPORTADA`, `DOCUMENTO_INDISPONIVEL`, `LIMITE_DE_TAXA`, `ERRO_TELEGRAM`); qualquer outro código recebido é tratado como `ERRO_TELEGRAM`; código novo entra por mudança aditiva da v1.

## Quem usa esta API

- **Orquestrador de canal (n8n)**: consumidor técnico direto. Só transporta: entrega ao backend o que chegou do Telegram e executa no Telegram o que o backend mandar. Não decide o que responder.
- **Professor**: usuário final. Não deve perceber nenhuma diferença: mesmos textos, botões, etapas, PDFs e avisos de hoje.
- **Mantenedor da plataforma**: precisa ter certeza de que o novo acesso interno não abre brechas (token do serviço, sessão do portal, dados de aluno em logs) e de que nenhuma mensagem se perde quando o orquestrador está fora do ar.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conversa completa do professor pela nova porta, sem diferença visível (Priority: P1)

O orquestrador recebe um update do Telegram (mensagem de texto, mensagem de voz, arquivo de áudio ou toque em botão), entrega-o sem alterações ao backend e recebe de volta a lista ordenada de ações a executar no chat (enviar mensagem, editar mensagem, enviar documento, confirmar o toque no botão, enviar/editar mensagem de etapa). O professor vê exatamente o que vê hoje.

**Why this priority**: é o coração da troca de transporte. Sem esta porta, o n8n não consegue operar o bot, e qualquer divergência de texto, botão ou ordem de mensagens quebra a paridade que o produto promete.

**Independent Test**: reexecutar, pela nova porta, todo o conjunto existente de testes de conversa e de roteiro de paridade (conferência, registro de aula, retomada, semestral, histórico, vinculação, início/busca e roteiros de paridade entre canais), com as mesmas entradas e as mesmas expectativas de texto, botões e ordem; todos passam sem alterar nenhuma expectativa.

**Acceptance Scenarios**:

1. **Given** um professor não vinculado, **When** o orquestrador entrega o update com um texto qualquer, **Then** a resposta traz a ação de mensagem `CODIGO_NECESSARIO`, com o mesmo texto de hoje, na mesma ordem.
2. **Given** um professor vinculado com relatório aguardando conferência, **When** o orquestrador entrega o toque no botão "Aprovar relatório", **Then** a resposta traz, em ordem, a confirmação do toque (para tirar o indicador de carregamento) e a mensagem `CONFIRMAR_APROVACAO` com os botões esperados.
3. **Given** um professor que confirmou o aluno de um áudio, **When** a resposta do backend inclui uma mensagem de etapa, **Then** a ação identifica o trabalho a que a etapa pertence e, se for uma etapa seguinte, a mensagem anterior a editar no lugar.
4. **Given** um relatório pronto para conferência, **When** o backend monta a apresentação, **Then** a resposta traz as mensagens do texto das seções (divididas se passarem do limite do chat), a ação de enviar o PDF como documento e os botões de aprovar, alterar e cancelar, na ordem de hoje.
5. **Given** o mesmo update (mesmo identificador de update) entregue duas vezes, **When** a segunda entrega chega, **Then** o estado da conversa não é processado de novo e a resposta traz apenas as ações da primeira entrega que o orquestrador ainda não confirmou como executadas (lista vazia se todas já foram confirmadas), para que o professor nunca fique sem resposta caso a primeira resposta tenha se perdido no caminho, e nunca receba a mesma mensagem duas vezes.

---

### User Story 2 - Acesso interno protegido por token de serviço (Priority: P1)

Somente o orquestrador, identificado por um token de serviço, consegue usar a API interna. O token vale apenas para a área interna; a sessão do portal vale apenas para a área do portal; um não abre o que é do outro.

**Why this priority**: a API interna entrega dados de conversa e PDFs de relatórios de alunos menores de idade. Abrir essa porta sem isolamento forte é um risco de privacidade (Princípio IV) e bloqueia a entrega das demais histórias.

**Independent Test**: chamar cada rota interna sem credencial, com token errado, com token correto e com sessão de portal; chamar rotas do portal com o token de serviço; verificar que apenas "token correto na área interna" é aceito.

**Acceptance Scenarios**:

1. **Given** uma chamada à área interna sem o cabeçalho do token, **When** ela chega, **Then** é recusada como não autenticada, sem corpo e sem detalhes.
2. **Given** uma chamada à área interna com token incorreto, **When** ela chega, **Then** é recusada da mesma forma, e a comparação do token não revela, pelo tempo de resposta, o quanto ele se aproxima do correto.
3. **Given** um professor com sessão válida no portal, **When** o navegador dele chama a área interna só com o cookie de sessão, **Then** é recusado.
4. **Given** o token de serviço correto, **When** ele é usado numa rota do portal, **Then** é recusado como não autenticado.
5. **Given** o token de serviço não configurado no ambiente, **When** qualquer chamada chega à área interna, **Then** todas são recusadas (nunca "aberto por omissão").
6. **Given** o update bruto do Telegram, **When** ele é entregue à rota de updates, **Then** além do token do serviço, o segredo do webhook do Telegram também é validado em tempo constante; se não bater, a chamada é recusada sem corpo.

---

### User Story 3 - Entrega proativa ao orquestrador sem perder mensagens (Priority: P2)

Quando algo acontece sem o professor ter escrito nada (o processamento de um áudio termina; um trabalho pedido pela Web termina e o professor tem Telegram vinculado; uma etapa do processamento muda), o backend entrega a ação correspondente ao orquestrador, que a executa no chat. Se o orquestrador estiver fora do ar, o backend tenta de novo até conseguir, e uma mesma ação nunca é executada duas vezes no chat.

**Why this priority**: hoje o backend fala direto com o Telegram; sem esta entrega proativa o professor deixaria de receber o resultado do processamento e os avisos entre canais (FR-021), o que quebraria a promessa de paridade.

**Independent Test**: com o orquestrador simulado indisponível, concluir um processamento e confirmar que a ação fica pendente e é reentregue quando ele volta; com ele respondendo lento ou duplicado, confirmar que a ação é executada uma única vez.

**Acceptance Scenarios**:

1. **Given** um processamento de áudio pedido no Telegram que acaba de terminar, **When** o backend o conclui, **Then** a apresentação do resultado (perguntas, conferência com PDF ou falha) é entregue ao orquestrador na mesma forma e ordem de ações da User Story 1, e o relatório entra em foco como hoje.
2. **Given** um trabalho pedido pela Web que acaba de terminar e um professor com Telegram vinculado, **When** o backend o conclui, **Then** é entregue o aviso curto com o botão "Continuar no chat", sem mudar o foco da conversa até o professor tocar no botão.
3. **Given** o orquestrador fora do ar no momento da entrega, **When** o backend tenta entregar, **Then** a ação permanece pendente e é reenviada automaticamente em intervalos crescentes até ser aceita; o processamento do relatório em si não é afetado nem bloqueado pela falha de entrega.
4. **Given** uma ação entregue que o orquestrador aceitou mas cuja confirmação não chegou ao backend, **When** o backend reenvia a mesma ação, **Then** ela leva o mesmo identificador de ação, e o contrato obriga quem a recebe a tratar a repetição como a mesma ação (idempotência).
5. **Given** a entrega de uma mensagem de etapa, **When** o orquestrador a executa no Telegram, **Then** ele informa ao backend o identificador da mensagem criada, para que as próximas etapas a editem no lugar e a mensagem não se acumule no chat.
6. **Given** uma ação que continua sem ser aceita depois do prazo máximo de tentativas, **When** o prazo vence, **Then** a ação é marcada como falha definitiva, o evento é registrado sem dados de aluno e fica visível ao mantenedor; o professor ainda encontra o resultado ao consultar o relatório por qualquer canal.

---

### User Story 4 - Obter o PDF por identificador (Priority: P2)

Quando uma ação pede o envio de um documento (PDF de conferência ou PDF oficial do histórico), ela traz um identificador opaco do documento, e o orquestrador busca os bytes do PDF no backend por esse identificador para anexá-lo ao envio.

**Why this priority**: sem isso o PDF, parte central da conferência e do histórico, não chega ao professor pelo novo transporte. É P2 porque depende da porta de ações (User Story 1) para ser exercitada de ponta a ponta.

**Independent Test**: gerar um relatório com PDF, obter o identificador a partir da ação de documento e recuperar os bytes; comparar com o PDF que o portal entrega; tentar identificadores inexistentes, vencidos e URLs.

**Acceptance Scenarios**:

1. **Given** uma ação de documento com identificador válido, **When** o orquestrador pede os bytes por esse identificador, **Then** recebe o PDF correto, com o tipo de conteúdo e o nome de arquivo corretos, idêntico ao que o professor receberia hoje.
2. **Given** um identificador desconhecido, vencido ou malformado, **When** ele é pedido, **Then** a resposta é "não encontrado", sem revelar se o identificador existiu.
3. **Given** um pedido em que o "identificador" é uma URL ou um caminho, **When** ele chega, **Then** é recusado; o backend nunca acessa endereço vindo do payload.
4. **Given** uma entrega repetida (nova tentativa de uma ação de documento), **When** o orquestrador pede o PDF novamente, **Then** o identificador ainda é válido pelo tempo coberto pelas tentativas de entrega.

---

### User Story 5 - Áudio e mensagens antigas continuam como hoje (Priority: P3)

O áudio nunca passa pelo orquestrador: o update traz apenas a referência do arquivo (`file_id`), e o backend baixa o arquivo, como hoje, pelo servidor local de API de bots (volume compartilhado, limite de 50 MB). O caminho antigo (webhook atual falando direto com o Telegram) segue funcionando, e a escolha de qual caminho entrega as mensagens proativas é configurável para não haver envio duplicado.

**Why this priority**: protege privacidade (Princípio IV: áudio não retido, não circula por terceiros) e permite a troca gradual de transporte na feature `004` sem corte brusco.

**Independent Test**: enviar um update com mensagem de voz pela nova porta e confirmar que o backend resolve o arquivo sozinho e que nada com conteúdo de áudio aparece em ações, filas de entrega ou logs; alternar a configuração e confirmar que as mensagens proativas saem por um caminho só.

**Acceptance Scenarios**:

1. **Given** um update com mensagem de voz dentro do limite, **When** o orquestrador o entrega, **Then** a resposta é `AUDIO_RECEBIDO_CONFIRMAR_ALUNO` (ou o estado correspondente) e nenhum byte de áudio, caminho de arquivo ou endereço de download aparece na resposta.
2. **Given** um áudio acima do limite, **When** o update chega, **Then** a resposta é `AUDIO_MUITO_GRANDE`, sem baixar o arquivo.
3. **Given** o caminho antigo ativo para mensagens proativas, **When** um processamento termina, **Then** a mensagem sai só pelo caminho antigo, nada é enfileirado para o orquestrador.
4. **Given** o caminho do orquestrador ativo para mensagens proativas, **When** um processamento termina, **Then** a mensagem é entregue só ao orquestrador, e nada sai direto ao Telegram.
5. **Given** o webhook atual, **When** um update chega por ele durante esta feature, **Then** continua sendo interpretado e respondido exatamente como antes.

---

### Edge Cases

- **Update de tipo não suportado ou mal formado**: o comportamento é o de hoje (tipos ignorados resultam em lista de ações vazia; grupos recebem a mensagem de que o bot só atende em conversa privada; corpo ilegível não gera erro para o Telegram).
- **Erro inesperado ao interpretar o update**: a resposta ainda traz `ERRO_INTERNO` ao chat (e a confirmação do botão, se for um toque), para que toda mensagem receba resposta (FR-039).
- **Mesmo `update_id` chegando em paralelo**: apenas um processamento ocorre; a outra chamada recebe as ações do primeiro que ainda não foram confirmadas como executadas.
- **Orquestrador executa só parte das ações e cai**: o backend registra a confirmação de cada ação executada (FR-007a); a repetição do update, ou a reentrega proativa, devolve só as não confirmadas, sem repetir as já executadas.
- **Botão de mensagem antiga**: devolve `ACAO_EXPIRADA` e reapresenta a etapa, como hoje.
- **Mensagem de etapa cuja mensagem anterior foi apagada pelo professor**: o orquestrador informa a falha de edição; o backend passa a enviar uma nova mensagem de etapa em vez de ficar sem informar o avanço.
- **Edição que falha numa lista de ações (mensagem antiga demais, apagada ou sem alteração)**: o orquestrador continua com as ações seguintes, informa a falha com código de motivo (FR-007b) e o backend emite como compensação uma nova ação de mensagem com o mesmo conteúdo; a compensação recebe identificador próprio e segue as regras de confirmação e idempotência.
- **Várias ações proativas para o mesmo chat ao mesmo tempo**: a ordem de execução no chat preserva a ordem de criação.
- **Reentrega de ação de uma conversa cujo vínculo foi desfeito entretanto**: a ação é descartada com registro, sem enviar mensagem.
- **Rotação do token de serviço**: com dois tokens aceitos ao mesmo tempo (FR-026a), a troca ocorre sem janela de recusas; nenhuma entrega pendente se perde por causa disso. Depois de removido o token antigo, chamadas com ele são recusadas.
- **PDF muito grande ou indisponível no momento da busca**: o orquestrador recebe um erro claro e informa `DOCUMENTO_INDISPONIVEL` na confirmação; o backend nunca devolve conteúdo parcial e emite a compensação do FR-007c (aviso com o botão para pedir o PDF de novo).
- **Ação entregue que nunca é confirmada**: ao vencer o prazo de entrega, ela chega a um estado final com código `SEM_CONFIRMACAO`, sem alterar o relatório nem contar como falha de entrega, e o payload é zerado como o das demais ações finalizadas (FR-029).
- **Chamada à API interna vinda de fora da rede de serviços**: a decisão de exposição pública é da infraestrutura (feature `004`), mas a API recusa tudo que não traga o token, mesmo se alcançada.

## Requirements *(mandatory)*

### Functional Requirements

**Recebimento de updates e devolução de ações**

- **FR-001**: O backend DEVE oferecer uma rota interna que recebe o update bruto do Telegram, exatamente como o Telegram o produziu, junto do cabeçalho de segredo do webhook, e devolve uma lista ordenada de ações a executar no chat.
- **FR-002**: As ações devolvidas DEVEM espelhar as saídas que o interpretador de conversa produz hoje: mensagem, edição de mensagem, documento, resposta a toque em botão e mensagem de etapa. Cada ação DEVE carregar o necessário para ser executada sem lógica adicional no orquestrador (destino, texto já escapado para o formato do Telegram, botões com seus rótulos e dados, referência de documento, mensagem a editar).
- **FR-003**: O formato da resposta DEVE ser versionado (v1) e identificar a versão no próprio corpo. Mudanças aditivas (campo opcional novo, tipo de ação novo) PODEM permanecer na v1; remover ou mudar o significado de um campo, ou tornar obrigatório um campo existente, é mudança incompatível e DEVE criar uma nova versão. O backend DEVE continuar servindo a versão anterior enquanto o orquestrador não migrar.
- **FR-003a**: O orquestrador DEVE ignorar campos desconhecidos de uma ação. Diante de um tipo de ação desconhecido, DEVE pulá-la, informar `ACAO_NAO_SUPORTADA` na confirmação de execução (FR-007b) e continuar com as ações seguintes, sem recusar a lista inteira.
- **FR-004**: A ordem das ações na lista DEVE ser a ordem em que devem ser executadas no chat, igual à ordem em que o caminho atual as envia.
- **FR-005**: O comportamento conversacional (estados, botões `v1:<acao>:<argumento>`, palavras-chave, retomada, semestral, histórico, avisos entre canais, textos e rótulos) DEVE ser idêntico ao descrito em `telegram-conversa.md` e `mensagens.md`; a nova rota NÃO DEVE conter regra de conversa própria, apenas reutilizar a mesma interpretação do caminho atual.
- **FR-006**: O backend DEVE validar o segredo do webhook do Telegram em tempo constante, e recusar a chamada, sem corpo, se ele faltar ou não conferir.
- **FR-007**: O backend DEVE deduplicar updates pelo `update_id`: um update repetido NÃO DEVE reprocessar o estado da conversa, e DEVE devolver apenas as ações produzidas pela primeira entrega que ainda não foram confirmadas como executadas (FR-007a), para que uma resposta perdida no caminho não deixe o professor sem resposta, sem duplicar mensagens já enviadas. Se todas já foram confirmadas, a lista devolvida é vazia.
- **FR-007a**: Toda ação de resposta a um update DEVE ter um identificador estável, derivado de forma determinística do `update_id` e da posição da ação na lista, igual em todas as entregas do mesmo update. O backend DEVE oferecer uma rota interna de confirmação de execução, a mesma usada para as ações proativas (FR-017 e Assumptions), por meio da qual o orquestrador informa por identificador de ação o que já executou; o backend registra essa confirmação e a usa para filtrar as ações na repetição do update.
- **FR-007b**: As ações de uma lista são independentes entre si; só a ordem de execução é garantida (FR-004). O orquestrador DEVE executar todas as ações em ordem, sem interromper a lista quando uma falhar, e DEVE informar ao backend, na confirmação de execução, o resultado de cada ação: sucesso ou falha com um código de motivo (por exemplo `MENSAGEM_NAO_EDITAVEL`, `CHAT_INDISPONIVEL`), sem texto de aluno. Os códigos de motivo são `MENSAGEM_NAO_EDITAVEL`, `CHAT_INDISPONIVEL`, `ACAO_NAO_SUPORTADA`, `DOCUMENTO_INDISPONIVEL`, `LIMITE_DE_TAXA` e `ERRO_TELEGRAM`; o backend DEVE tratar qualquer outro código recebido como `ERRO_TELEGRAM`, sem recusar as demais confirmações do lote, e um código novo só entra por mudança aditiva da v1 (FR-003). O orquestrador NÃO DEVE decidir compensações por conta própria; a compensação (por exemplo, enviar uma nova mensagem no lugar de uma edição que falhou) é decidida e emitida pelo backend como nova ação. A confirmação do toque em botão é executada independentemente do resultado das demais ações.
- **FR-007c**: Quando o orquestrador informar `DOCUMENTO_INDISPONIVEL` para uma ação de documento, o backend DEVE emitir uma compensação (nova ação, id próprio, mesmas regras de confirmação e idempotência) com um aviso curto ao professor de que o PDF não pôde ser enviado e com o botão para pedi-lo de novo, de modo que o professor nunca fique sem o PDF e sem aviso. O texto do aviso é novo e aditivo no catálogo de mensagens (`mensagens.md`); nenhum texto existente muda. Uma compensação que também falha NÃO DEVE gerar outra.
- **FR-008**: Toda mensagem recebida DEVE receber ao menos uma ação de resposta, inclusive em erro inesperado (`ERRO_INTERNO`), e todo toque em botão DEVE receber a ação de confirmação do toque (FR-039 da feature 001).
- **FR-009**: Tipos de update não suportados DEVEM resultar em lista de ações vazia, e updates de grupo DEVEM receber a resposta de atendimento apenas em conversa privada, como hoje.
- **FR-010**: O backend NÃO DEVE enviar nenhuma mensagem ao Telegram para responder a um update recebido por esta rota; apenas devolver ações. O estado da conversa DEVE estar gravado antes de a resposta ser devolvida.

**Entrega proativa ao orquestrador**

- **FR-011**: O backend DEVE entregar ao orquestrador, por uma interface interna definida neste contrato, as ações que nascem sem um update do professor: resultado do processamento de áudio (perguntas, conferência com PDF, falhas), mensagens de etapa e avisos entre canais (FR-021 da feature 001).
- **FR-012**: A entrega proativa DEVE ter garantia de entrega: se o orquestrador estiver indisponível ou recusar, o backend DEVE manter a ação pendente e reenviá-la automaticamente, com intervalos crescentes, até ser aceita ou esgotar o prazo máximo configurável.
- **FR-013**: Cada ação proativa DEVE ter um identificador estável e único, o mesmo em todas as tentativas, e o contrato DEVE obrigar o receptor a tratar repetições do mesmo identificador como uma única ação (idempotência).
- **FR-014**: O backend NÃO DEVE criar duas ações proativas para o mesmo evento de negócio (por exemplo, o mesmo processamento concluído), mesmo se o evento for processado duas vezes.
- **FR-015**: A falha de entrega NÃO DEVE interromper nem reverter o processamento do relatório, nem alterar o estado do relatório.
- **FR-016**: O backend DEVE preservar a ordem de execução das ações proativas de um mesmo chat conforme a ordem em que foram criadas.
- **FR-017**: O backend DEVE receber do orquestrador o identificador da mensagem criada no Telegram quando uma ação for de mensagem de etapa, para que as etapas seguintes a editem no lugar, e DEVE tratar a falha de edição enviando uma nova mensagem de etapa.
- **FR-018**: O prazo máximo de tentativas, os intervalos e o endereço do orquestrador DEVEM ser configuráveis por ambiente. Os valores padrão são: intervalo inicial de 30 segundos, dobrando a cada nova tentativa até o teto de 15 minutos, e prazo total de 24 horas contado da criação da ação. Ao esgotar o prazo, a ação DEVE ficar marcada como falha definitiva e DEVE ser registrada sem dados de aluno.
- **FR-019**: A escolha de qual caminho entrega as mensagens proativas (envio direto ao Telegram, como hoje, ou entrega ao orquestrador) DEVE ser configurável, com o caminho atual como padrão, e DEVE haver exatamente um caminho ativo por vez, sem envio duplicado nem perda.

**PDF por identificador**

- **FR-020**: Toda ação de documento DEVE referenciar o PDF por um identificador opaco emitido pelo backend; o conteúdo do PDF NÃO DEVE trafegar dentro da lista de ações nem da entrega proativa.
- **FR-021**: O backend DEVE oferecer uma rota interna que, dado o identificador, devolve os bytes do PDF com tipo de conteúdo e nome de arquivo, idênticos ao que o professor recebe hoje.
- **FR-022**: O identificador DEVE ser imprevisível, restrito a um único documento e válido por um tempo suficiente para cobrir todas as tentativas de entrega; desconhecido, vencido ou malformado resulta em "não encontrado", sem distinguir os casos.
- **FR-023**: O backend NUNCA DEVE buscar conteúdo a partir de URL, caminho ou endereço vindo do payload do orquestrador; só aceita o identificador opaco.

**Autenticação serviço a serviço**

- **FR-024**: Toda rota sob a área interna (`/interno/**`) DEVE exigir o cabeçalho `X-N8N-Service-Token` com o token do serviço, comparado em tempo constante; sem o cabeçalho ou com valor incorreto, a chamada DEVE ser recusada como não autenticada, sem corpo.
- **FR-025**: A área interna NÃO DEVE aceitar a sessão por cookie do portal como credencial, e o token de serviço NÃO DEVE autenticar nenhuma rota do portal (`/api/v1/**`) nem qualquer outra área.
- **FR-026**: O token de serviço DEVE vir do ambiente ou de um cofre de segredos, nunca do código versionado; se nenhum token estiver configurado, toda a área interna DEVE recusar todas as chamadas.
- **FR-026a**: O backend DEVE aceitar até dois tokens de serviço válidos ao mesmo tempo (atual e próximo), para permitir a rotação sem janela de indisponibilidade: adicionar o novo, reconfigurar o orquestrador, remover o antigo. A comparação com cada token configurado DEVE ser em tempo constante, e a presença de um segundo token NÃO DEVE alterar o tempo de resposta de modo que revele qual token conferiu. Nenhum token DEVE aparecer em log, ação ou mensagem de erro.
- **FR-027**: Os mecanismos de proteção do portal que valem para sessão de navegador (por exemplo, proteção contra requisições forjadas entre sites) NÃO DEVEM impedir o uso legítimo da área interna com token, e a área interna NÃO DEVE reduzir essas proteções nas rotas do portal.

**Áudio**

- **FR-028**: O áudio DEVE continuar sendo obtido exclusivamente pelo backend, a partir do `file_id` presente no update, pelo servidor local de API de bots, com o mesmo limite de tamanho e as mesmas regras de descarte de hoje (FR-015 e FR-018 da feature 001); o orquestrador NUNCA DEVE receber, baixar nem repassar áudio.

**Qualidade, privacidade e convenções**

- **FR-029**: Nenhum log, métrica, registro de falha ou mensagem de erro da área interna DEVE conter CPF, nome de aluno, texto de relatório, transcrição ou áudio, nem a mensagem ou o rastro de uma exceção que possa carregá-los; esses registros DEVEM se limitar a identificadores técnicos (por exemplo, `update_id`, identificador de ação), tipos de exceção e códigos de situação (Princípio IV). O CPF e o áudio NUNCA DEVEM constar de ação nem de entrega pendente. O payload de uma ação e de sua compensação PODE conter o texto que a mensagem ao professor exige (inclusive nome do aluno e trechos do relatório), apenas enquanto necessário: DEVE ser zerado depois do prazo de retenção contado do estado final da ação, e uma ação entregue que nunca foi confirmada DEVE chegar a um estado final no prazo máximo de entrega (FR-018), para que o payload também seja zerado. Guardar só referências e montar o texto no despacho foi rejeitado.
- **FR-030**: Todo o conjunto de testes de conversa e de paridade existente (conferência, registro de aula, retomada, semestral, histórico, vinculação, início/busca e roteiros de paridade) DEVE passar também pela nova rota, com as mesmas entradas e as mesmas expectativas de texto, botões e ordem, sem alterar nenhuma expectativa existente.
- **FR-031**: A área interna DEVE ter testes de contrato que cubram: formato versionado das ações, validação de segredo e token, deduplicação, entrega proativa com retentativa e idempotência, busca de PDF e isolamento entre credenciais.
- **FR-032**: O núcleo da feature (regra de negócio e casos de uso envolvidos) DEVE manter cobertura mínima de 90% de linhas e de ramos (Princípio V).
- **FR-033**: Nomes de código, rotas, campos e arquivos introduzidos DEVEM seguir o Princípio VI (português do Brasil, sem acentos, sufixos de camada padronizados); a nova entrada DEVE ser apenas um adaptador de entrada (Princípio I), sem regra de negócio.

### Key Entities *(include if feature involves data)*

- **Update de entrada**: o evento bruto produzido pelo Telegram (mensagem ou toque em botão), identificado por um `update_id` único. Sua repetição não reprocessa o estado e devolve apenas as ações da primeira vez ainda não confirmadas como executadas.
- **Ação de canal**: uma instrução do backend para o orquestrador executar no chat. Tipos: mensagem, edição, documento, resposta a toque em botão e mensagem de etapa. Tem identificador estável, destino, conteúdo já formatado, botões (rótulo + dados do botão), referência de documento (quando aplicável), ordem de execução, versão do formato (v1) e estado de execução (pendente ou confirmada).
- **Ação proativa pendente**: uma ação criada sem update do professor (resultado de processamento, etapa, aviso entre canais) com identificador estável, estado (pendente, entregue, falha definitiva), número de tentativas e momento da próxima tentativa. Existe para garantir entrega e idempotência.
- **Documento disponível para o orquestrador**: um PDF já gerado, referenciado por identificador opaco e com validade, ligado a uma ação de documento.
- **Credencial de serviço**: o token que identifica o orquestrador (até dois válidos ao mesmo tempo, para rotação); vale somente na área interna.
- **Estado da conversa**: o já existente (vínculo, fluxo, etapa, relatório em foco, áudio pendente), que continua só no backend e permite reiniciar o orquestrador sem perda.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos cenários dos testes de conversa e de paridade existentes passam também pela nova porta, sem nenhuma expectativa de texto, botão ou ordem alterada.
- **SC-002**: Para os professores, nenhuma diferença perceptível: nenhum texto, rótulo de botão, sequência de mensagens, PDF ou aviso entre canais muda em relação ao comportamento de hoje.
- **SC-003**: Em condições normais, 95% dos updates (sem contar o processamento pesado de áudio, que é assíncrono) recebem as ações de resposta em até 2 segundos, mantendo a confirmação ao professor em até 5 segundos (SC-007 da feature 001).
- **SC-004**: Com o orquestrador indisponível por até o prazo máximo de tentativas, 100% das ações proativas pendentes são entregues após a volta dele, e nenhuma é executada em duplicidade no chat.
- **SC-005**: Zero chamadas à área interna aceitas sem o token correto; zero acessos ao portal aceitos com o token de serviço; zero acessos à área interna aceitos só com sessão de portal.
- **SC-006**: Zero ocorrências de CPF, nome de aluno, texto de relatório, transcrição ou áudio em logs, ações e entregas pendentes da área interna, verificado por teste automatizado.
- **SC-007**: 100% dos PDFs obtidos por identificador são idênticos aos que o portal entrega para o mesmo relatório, e 100% dos pedidos com identificador inexistente, vencido ou com URL são recusados.
- **SC-008**: A cobertura de testes do núcleo da feature é de no mínimo 90% de linhas e de ramos, e o conjunto completo de testes continua passando com o webhook atual ativo.

## Assumptions

- O orquestrador é um consumidor confiável da rede interna de serviços, que fala com o backend por HTTP; a exposição pública e o proxy ficam para a feature `004`.
- Um update repetido devolve apenas as ações da primeira vez que o orquestrador ainda não confirmou como executadas. Isso evita que uma resposta perdida deixe o professor sem retorno e, ao mesmo tempo, que mensagens já enviadas sejam repetidas; o orquestrador não guarda estado de deduplicação, só confirma ao backend o que executou. Resta apenas a janela mínima entre executar uma ação no Telegram e a confirmação chegar ao backend.
- O identificador de documento é válido por pelo menos o prazo máximo de tentativas de entrega, e o PDF obtido tem o mesmo conteúdo do que o portal entrega; a autorização de acesso a ele é dada pelo token de serviço, ficando a verificação de dono do relatório na geração do identificador.
- O orquestrador informa o identificador da mensagem criada por meio de uma confirmação de execução enviada ao backend, que também registra a entrega como concluída.
- O prazo máximo de tentativas padrão (24 h, FR-018) cobre um dia de indisponibilidade do orquestrador e é configurável. Se a entrega reaproveita a fila Postgres existente (`trabalho_processamento`) ou uma tabela própria é decisão do plano.
- Por padrão (esta feature), as mensagens proativas continuam saindo pelo caminho atual; a ativação do caminho do orquestrador é uma configuração, a ser ligada pela feature `004` junto da troca do webhook.
- O servidor local de API de bots do Telegram e o volume de arquivos compartilhado continuam como estão; a única mudança em relação a áudio é garantir que o orquestrador não o toque.
- Fora de escopo, por decisão do pedido: criação de workflows do n8n, alteração de `compose`/proxy, troca do webhook registrado no Telegram e remoção do adaptador antigo (feature `004`).
