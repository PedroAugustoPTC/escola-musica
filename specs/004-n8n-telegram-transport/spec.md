# Feature Specification: n8n como único transporte do canal Telegram

**Feature Branch**: `spec/004-n8n-telegram-transport`

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "Colocar o n8n como único transporte do canal Telegram, sem mudar nada do que o professor vê hoje. Esta é a feature que a spec 002 deixou como 'fora de escopo'." (descrição completa preservada no pedido de `/speckit-specify`; resumida nas seções abaixo)

**Materiais de referência**:

- Constituição v2.1.0, Princípio II ("Fonte Única da Verdade") e "Restrições Adicionais" (serviço n8n em contêiner, imagem com versão fixada, workflows como código, sem segredo nem ID de credencial nos JSON): o n8n é admitido SOMENTE como transporte do canal Telegram — recebe o update, o repassa ao backend e executa as ações devolvidas; nunca decide o que responder, nunca grava dados de domínio e nunca chama a IA.
- `specs/002-api-interna-canal-telegram/` (`spec.md`, `contracts/canal-telegram-interno.md`, `contracts/telegram-channel-internal.openapi.yaml`, `contracts/actions-v1.schema.json`): a **API interna de canal** que esta feature consome. A seção "Obrigações do orquestrador" do contrato é a especificação do que os workflows devem fazer. **Nenhuma regra da 002 muda aqui**; se o plano descobrir uma lacuna, ela entra como mudança aditiva da v1.
- `specs/001-fluxo-relatorios/contracts/telegram-conversa.md` e `mensagens.md`: fonte de verdade do comportamento da conversa (estados, botões, palavras-chave, textos). **Nada disso muda nesta feature.**
- `specs/001-fluxo-relatorios/research.md` R-04, `plan.md`, `quickstart.md` e `tasks.md`: assumem "sem n8n" e passam a refletir o transporte pelo n8n (FR-032).

**Escopo desta feature**: tudo o que falta para o n8n operar o bot — o serviço em contêiner, os workflows versionados, o fluxo único de entrada, a troca controlada e reversível do webhook do Telegram, a prova de paridade e, só depois dela, a remoção do caminho antigo (backend falando direto com o Telegram). **Fora de escopo**: qualquer regra de negócio ou autorização no n8n; novas funcionalidades do bot; mudança de textos; administração pelo Telegram.

## Clarifications

### Session 2026-10-15

- Q: Quando o período de observação em produção, antes de remover o caminho antigo, pode ser considerado "sem divergência"? → A: Quando, durante 7 dias corridos, os sinais medidos ficam em zero (updates sem resposta, respostas duplicadas, ações proativas com falha definitiva por causa do transporte e confirmações de toque perdidas) **e** nenhum professor relatou diferença visível, registrado pelo dono do produto.
- Q: Quando o contêiner do n8n sobe, os workflows do repositório devem sempre substituir os que já estão na instância, ou só ser instalados na primeira criação? → A: Sempre substituem: a cada subida, os workflows do repositório são importados e prevalecem; edição manual na instância é descartada no próximo reinício, e a verificação de divergência detecta a edição manual entre dois reinícios.
- Q: Quanto tempo o n8n pode ficar sem receber updates numa reinicialização planejada, sem que isso conte como falha? → A: Até 60 segundos por reinício planejado, com as mensagens do professor chegando com atraso, sem perda e sem duplicidade, e o estado da conversa intacto; não há segunda instância do n8n em paralelo.
- Q: Em produção, como o mantenedor acessa a interface de edição do n8n? → A: Não acessa: a interface de edição fica desativada em produção e ligada só no ambiente local e de testes; qualquer mudança de workflow entra por PR e nova subida do contêiner.
- Q: Como a idempotência deve ser comprovada de ponta a ponta pelo n8n? → A: A suíte de paridade roda uma segunda vez entregando cada update em duplicidade e reentregando cada lote proativo, exigindo a mesma transcrição do chat, sem mensagem a mais; além disso, cenários derrubam o n8n no meio de uma lista de ações e exigem que as ações restantes cheguem uma única vez. Tudo com o Telegram simulado, no CI.

## Quem usa esta feature

- **Professor**: usuário final. Não deve perceber nenhuma diferença: mesmos textos, botões, etapas, PDFs e avisos de hoje, no mesmo ritmo.
- **Mantenedor da plataforma**: sobe o ambiente, troca o transporte, reverte se algo sair errado, opera o dia a dia e precisa ter certeza de que o novo serviço não guarda segredo nem dado de aluno.
- **Dono do produto**: aprova a remoção do caminho antigo depois de ver a paridade provada.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - O n8n sobe com o ambiente e seus workflows vêm do repositório (Priority: P1)

O mantenedor roda o mesmo comando único dos demais serviços, local ou em produção, e o n8n sobe junto, em contêiner, com a versão da imagem fixada e com o fluxo de entrada do bot e o fluxo de entrega proativa já instalados, sem nenhum passo manual na interface do n8n. Os workflows existem no repositório como código; o que roda na instância pode ser recriado do zero a partir deles e das variáveis de ambiente, e nenhum arquivo contém segredo nem identificador de credencial.

**Why this priority**: sem o serviço e os workflows reproduzíveis nada mais é possível, e é aqui que a constituição impõe suas garantias (versão fixada, workflows como código, sem segredos).

**Independent Test**: a partir de um ambiente vazio (sem volumes), subir tudo com o comando único e conferir que o n8n está saudável e com exatamente os fluxos do repositório ativos; destruir a instância e recriá-la e conferir que o comportamento é o mesmo; varrer os arquivos versionados atrás de segredo, ID de credencial e dado de aluno.

**Acceptance Scenarios**:

1. **Given** um ambiente local sem nenhum volume, **When** o mantenedor sobe a pilha pelo comando único, **Then** o n8n sobe junto, saudável, com a imagem de versão fixada e com os fluxos do repositório ativos, sem que ninguém abra a interface do n8n.
2. **Given** a instância em execução, **When** ela é destruída junto com seus dados e recriada a partir do repositório e das variáveis de ambiente, **Then** o conjunto de workflows ativos e o comportamento do bot são os mesmos de antes.
3. **Given** a instância em execução, **When** alguém altera um workflow pela interface sem passar pelo repositório, **Then** a verificação de divergência entre a instância e o repositório aponta a diferença e reprova, e na subida seguinte do contêiner a alteração é descartada e o workflow do repositório volta a valer.
4. **Given** os arquivos de workflow versionados, **When** a verificação automática os examina, **Then** nenhum contém segredo, token, ID de credencial, endereço com segredo no caminho nem dado de aluno.
5. **Given** o conjunto de workflows, **When** a verificação automática o examina, **Then** existe exatamente um fluxo que recebe os updates do Telegram, e nenhum workflow escreve em banco, chama provedor de IA ou contém ramificação por regra de negócio.

---

### User Story 2 - A conversa completa do professor passa pelo n8n sem diferença visível (Priority: P1)

O Telegram entrega o update (texto, mensagem de voz, arquivo de áudio ou toque em botão) ao n8n; o n8n o repassa ao backend sem alterações, recebe a lista ordenada de ações e as executa no chat, em ordem: mensagem, edição, documento (PDF), confirmação do toque e mensagem de etapa. Toda mensagem recebe resposta; todo toque em botão é confirmado. O professor vê exatamente o que vê hoje.

**Why this priority**: é o coração da troca de transporte. Qualquer divergência de texto, botão ou ordem quebra a paridade que o produto promete.

**Independent Test**: reexecutar, passando pelo n8n, todo o conjunto existente de testes de conversa e de roteiro de paridade (conferência, registro de aula, retomada, semestral, histórico, vinculação, início/busca e roteiros de paridade entre canais), com as mesmas entradas e as mesmas expectativas de texto, botões e ordem; todos passam sem alterar nenhuma expectativa.

**Acceptance Scenarios**:

1. **Given** um professor não vinculado, **When** ele escreve qualquer texto, **Then** recebe a mensagem de código necessário, com o mesmo texto de hoje.
2. **Given** um professor vinculado com relatório aguardando conferência, **When** ele toca em "Aprovar relatório", **Then** o toque é confirmado (indicador de carregamento some) e a mensagem de confirmação de aprovação chega com os botões esperados, nessa ordem.
3. **Given** um relatório pronto para conferência, **When** o backend manda apresentá-lo, **Then** o chat recebe as mensagens do texto das seções, o PDF como documento e os botões de aprovar, alterar e cancelar, na ordem de hoje, com o mesmo PDF que o portal entrega.
4. **Given** uma mensagem de voz ou um arquivo de áudio dentro do limite, **When** o update chega, **Then** o n8n repassa apenas a referência do arquivo, nunca o áudio, e a resposta é a de hoje.
5. **Given** o mesmo update entregue duas vezes ao n8n, **When** a segunda entrega chega, **Then** o professor não recebe nenhuma mensagem em duplicidade e não fica sem resposta caso a primeira tenha se perdido.
6. **Given** um update de tipo não suportado, **When** ele chega, **Then** nada é enviado ao chat e nenhum erro é devolvido ao Telegram, como hoje; e um update de grupo recebe a resposta de atendimento apenas em conversa privada.

---

### User Story 3 - Mensagens proativas chegam ao professor pelo n8n, uma única vez (Priority: P1)

Quando algo acontece sem o professor ter escrito nada (processamento de áudio terminou, trabalho pedido pela Web terminou, etapa do processamento mudou, aviso entre canais), o backend entrega as ações ao n8n, que as executa no chat, em ordem, informa o resultado de cada uma e devolve ao backend o identificador da mensagem de etapa criada, para que as próximas etapas editem a mesma mensagem em vez de acumular mensagens no chat. Uma ação repetida pelo backend nunca é executada duas vezes.

**Why this priority**: sem isso o professor deixaria de receber o resultado do áudio e os avisos entre canais, o que quebraria a paridade entre Web e Telegram.

**Independent Test**: concluir um processamento de áudio pedido pelo Telegram e um trabalho pedido pela Web com professor vinculado, e conferir o que chega ao chat (textos, botões, PDF, ordem, mensagem de etapa editada no lugar); repetir a entrega da mesma ação e conferir que o chat não recebe nada a mais.

**Acceptance Scenarios**:

1. **Given** um processamento de áudio pedido pelo Telegram que acaba de terminar, **When** o backend entrega o resultado, **Then** o chat recebe as perguntas, a conferência com PDF ou a falha, na mesma forma e ordem de hoje.
2. **Given** um trabalho pedido pela Web que acaba de terminar e um professor com Telegram vinculado, **When** o backend o conclui, **Then** o chat recebe o aviso curto com o botão "Continuar no chat", sem mudar o foco da conversa até o professor tocar no botão.
3. **Given** uma mensagem de etapa executada pela primeira vez, **When** o n8n a envia, **Then** informa ao backend o identificador da mensagem criada, e as etapas seguintes editam essa mesma mensagem no chat.
4. **Given** uma ação proativa já executada, **When** o backend a entrega de novo com o mesmo identificador, **Then** o n8n não a executa outra vez e o chat não recebe mensagem duplicada.
5. **Given** uma entrega que não traz a credencial de entrega correta, **When** ela chega ao n8n, **Then** é recusada sem executar nada.

---

### User Story 4 - Falha ao executar uma ação é informada ao backend e a lista continua (Priority: P2)

Quando uma ação não pode ser executada no Telegram (mensagem antiga demais para editar, chat indisponível, limite de taxa, PDF indisponível, tipo de ação desconhecido), o n8n não para: informa ao backend a falha com um código de motivo, sem texto de aluno, e segue executando as ações seguintes na ordem. Quem decide a compensação é o backend; o n8n nunca decide por conta própria.

**Why this priority**: garante que o professor nunca fique sem resposta ou sem o PDF por causa de uma falha pontual de uma ação, que é o comportamento prometido pela 002 (FR-007b e FR-007c).

**Independent Test**: simular cada falha de execução no Telegram em uma lista com várias ações e conferir que as ações seguintes foram executadas em ordem, que cada resultado foi informado com o código esperado e que a compensação (quando houver) chegou ao chat por ação nova emitida pelo backend.

**Acceptance Scenarios**:

1. **Given** uma lista cuja segunda ação é uma edição de mensagem que o Telegram recusa, **When** o n8n a executa, **Then** informa `MESSAGE_NOT_EDITABLE`, executa a terceira ação normalmente, e a nova mensagem de compensação emitida pelo backend chega ao chat.
2. **Given** uma lista com confirmação de toque, mensagem e documento, **When** o envio da mensagem falha, **Then** a confirmação do toque ainda é executada e o documento ainda é tentado.
3. **Given** uma ação de documento cujo PDF o backend não entrega (não encontrado ou erro), **When** o n8n faz a única tentativa de busca, **Then** informa `DOCUMENT_UNAVAILABLE` e o professor recebe o aviso curto com o botão para pedir o PDF de novo, emitido pelo backend.
4. **Given** uma ação de tipo que o n8n não conhece, **When** ele a encontra na lista, **Then** a pula, informa `ACTION_NOT_SUPPORTED` e continua com as demais, sem recusar a lista inteira.
5. **Given** uma falha do Telegram que não corresponde a nenhum código conhecido, **When** ela é informada, **Then** o código enviado é `TELEGRAM_ERROR` e nenhum texto de aluno acompanha o resultado.

---

### User Story 5 - Troca controlada e reversível do webhook do Telegram (Priority: P2)

O mantenedor troca o destino dos updates do bot do backend para o n8n, e passa a entrega proativa para o n8n, em um único procedimento documentado e verificável; a qualquer momento pode reverter para o caminho antigo com um procedimento igualmente simples. Em nenhum instante há dois caminhos entregando a mesma mensagem, e nenhuma mensagem se perde na passagem.

**Why this priority**: o webhook é a chave que liga o n8n ao professor real. Uma troca sem verificação ou sem volta é o maior risco operacional da feature.

**Independent Test**: ensaiar, em ambiente de teste, a troca e a reversão com o bot recebendo mensagens durante a passagem; conferir o estado do webhook e da entrega proativa antes, depois e após reverter; conferir que cada mensagem enviada ao bot teve exatamente uma resposta.

**Acceptance Scenarios**:

1. **Given** o n8n no ar com os workflows ativos e o webhook ainda no backend, **When** o mantenedor executa o procedimento de troca, **Then** o webhook do bot passa a apontar para o n8n, a entrega proativa passa a ser a do n8n, e um passo de verificação confirma os dois estados e uma ida e volta de mensagem de teste.
2. **Given** a troca concluída, **When** o mantenedor executa o procedimento de reversão, **Then** o webhook volta ao backend e a entrega proativa volta ao envio direto, e o bot continua respondendo sem perda de estado de conversa.
3. **Given** mensagens chegando ao bot durante a troca (ou durante a reversão), **When** a troca termina, **Then** cada mensagem foi respondida exatamente uma vez, e cada ação proativa foi entregue exatamente uma vez.
4. **Given** o n8n fora do ar ou com os workflows inativos, **When** o mantenedor tenta executar a troca, **Then** o procedimento recusa a troca e explica o que falta, sem alterar o webhook.
5. **Given** o webhook apontando para o n8n, **When** o passo de verificação é executado, **Then** ele reprova se o webhook, a entrega proativa ou a ida e volta de teste estiverem em estado diferente do esperado.

---

### User Story 6 - Com o n8n fora do ar ou reiniciado, nada se perde (Priority: P2)

Se o n8n cair, for reiniciado ou ficar indisponível por um tempo, nenhuma mensagem do professor nem nenhuma ação proativa se perde: as ações proativas pendentes são reentregues pelo backend quando o n8n volta, sem duplicar no chat, e o estado de qualquer conversa em andamento continua exatamente onde estava, porque ele vive só no backend.

**Why this priority**: separar o transporte da aplicação só vale se reiniciar o orquestrador for seguro; é a razão de ser do Princípio II.

**Independent Test**: com uma conversa no meio de um fluxo e ações proativas pendentes, derrubar e subir o n8n (inclusive no meio da execução de uma lista de ações) e conferir que a conversa continua da mesma etapa, que toda ação proativa chegou uma única vez e que nenhuma mensagem do professor ficou sem resposta.

**Acceptance Scenarios**:

1. **Given** um professor no meio do registro de uma aula, **When** o n8n é reiniciado, **Then** a próxima mensagem dele continua da mesma etapa, sem que nenhum dado de conversa tenha sido perdido.
2. **Given** o n8n indisponível quando um processamento termina, **When** o n8n volta dentro do prazo de entrega, **Then** o backend reentrega a ação e o professor recebe a mensagem uma única vez.
3. **Given** uma mensagem do professor enviada com o n8n fora do ar (inclusive durante um reinício planejado, que DEVE durar no máximo 60 segundos), **When** o n8n volta, **Then** o update que o Telegram reteve e reenviou é respondido uma única vez, com atraso mas sem perda, e nenhum texto é inventado pelo n8n no intervalo.
4. **Given** o n8n que cai no meio da execução de uma lista de ações, **When** ele volta e o mesmo update ou a mesma ação proativa é reentregue, **Then** só as ações ainda não confirmadas são executadas, sem repetir as que o professor já recebeu.
5. **Given** o backend indisponível quando um update chega ao n8n, **When** o n8n não consegue obter as ações, **Then** não responde com texto próprio, devolve falha ao Telegram para que o update seja reenviado e o professor recebe a resposta quando o backend voltar.

---

### User Story 7 - O caminho antigo sai somente depois de provada a paridade (Priority: P3)

Depois de a paridade estar provada — suíte completa passando pelo n8n, período de operação em produção sem divergência e aprovação do dono do produto —, o caminho antigo é removido: o backend deixa de interpretar o webhook direto do Telegram, deixa de enviar respostas e mensagens proativas direto ao Telegram, e as chaves de configuração que só existiam para ele desaparecem. O que o backend faz com o servidor local de bots para baixar áudio permanece.

**Why this priority**: manter dois caminhos indefinidamente é dívida e risco de duplicidade; mas removê-lo antes da prova tiraria a única rota de volta.

**Independent Test**: com a evidência de paridade anexada, remover o caminho antigo e rodar a suíte completa (backend, frontend, conversa e paridade, guardiões de idioma e de workflows); procurar no código e na configuração qualquer resto do caminho antigo.

**Acceptance Scenarios**:

1. **Given** a evidência de paridade incompleta (suíte vermelha pelo n8n, algum sinal medido diferente de zero ou relato de diferença visível dentro dos 7 dias, período de observação não cumprido ou sem aprovação registrada), **When** a remoção é proposta, **Then** ela não é feita e o que falta é listado.
2. **Given** a paridade provada e aprovada, **When** o caminho antigo é removido, **Then** todos os testes de conversa e de paridade continuam passando pelo n8n, sem alterar nenhuma expectativa.
3. **Given** o caminho antigo removido, **When** o código e a configuração são examinados, **Then** não restam rota de webhook direto, envio direto de resposta ou mensagem proativa, escolha de caminho proativo nem chave de ambiente que só servia a ele.
4. **Given** o caminho antigo removido, **When** chega um áudio, **Then** o backend continua baixando o arquivo pelo servidor local de bots, com o mesmo limite e as mesmas regras de descarte.

---

### Edge Cases

- **Dois fluxos tentando receber os updates do mesmo bot**: o Telegram aceita um webhook só por bot; a verificação automática reprova qualquer conjunto de workflows com mais de um receptor de updates do Telegram.
- **Update de tipo não suportado ou corpo ilegível**: o comportamento é o de hoje — lista de ações vazia, nada enviado ao chat, nenhum erro ao Telegram.
- **Segredo do webhook do Telegram ausente ou errado**: o backend recusa; o n8n não decide nada sobre isso, apenas repassa o cabeçalho recebido.
- **Token de serviço do n8n rotacionado**: com dois tokens aceitos ao mesmo tempo (002, FR-026a), a troca no n8n não gera janela de recusas; o n8n lê o token do ambiente, não de um arquivo versionado.
- **Backend recusa ou não responde a uma confirmação**: o n8n não repete indefinidamente; a ação não confirmada é tratada pelas regras de reentrega, de idempotência e de expiração do backend (002).
- **n8n aceita uma entrega proativa e cai antes de executá-la**: nenhuma mensagem pode se perder nem duplicar; o plano define se a aceitação só ocorre depois da execução ou se o backend reentrega ações entregues e não confirmadas (ver Assumptions).
- **Lista de ações grande ou PDF grande**: a ordem é preservada; o PDF é obtido por identificador opaco numa única tentativa, e a falha vira `DOCUMENT_UNAVAILABLE`.
- **Limite de taxa do Telegram**: o n8n informa `RATE_LIMITED` e não decide reenvio por conta própria; a decisão é do backend.
- **Histórico de execuções do n8n**: a instância não pode reter texto de mensagem, nome de aluno, trecho de relatório nem bytes de PDF do que passou por ela; o que ficar disponível para depuração se limita a identificadores técnicos e códigos de situação.
- **Exportação dos workflows feita a partir da instância**: não carrega segredo, ID de credencial nem dado de aluno.
- **Troca de webhook com mensagens em voo**: a deduplicação por `update_id` do backend e a entrega proativa com identificador estável garantem uma resposta por mensagem; o procedimento de troca não depende de pausar o bot.
- **Reversão depois da remoção do caminho antigo**: a reversão deixa de existir como procedimento de configuração; passa a ser a reimplantação da versão anterior (registrado como consequência aceita no plano).
- **Chamada ao n8n vinda de fora da rede de serviços**: o n8n e o seu editor não são publicados; só a entrada HTTPS do sistema é pública, como já é hoje.

## Requirements *(mandatory)*

### Functional Requirements

**Serviço e ambiente**

- **FR-001**: O ambiente local e a produção DEVEM incluir o serviço n8n em contêiner, com a versão da imagem fixada (nunca uma tag flutuante como `latest`), subido pelo mesmo comando único dos demais serviços, com as mesmas definições de imagem nos dois ambientes e variando apenas a configuração.
- **FR-002**: O n8n e a sua interface de edição NÃO DEVEM ser expostos publicamente (Princípio IV): o Telegram, o backend e o n8n conversam pela rede interna; a interface de edição do n8n DEVE estar desativada em produção e ligada apenas no ambiente local e de testes; em produção, workflows só mudam por PR e nova subida do contêiner (FR-008). O mantenedor opera a produção pelos meios que não exigem a interface: verificação de saúde (FR-006), procedimentos de troca e reversão (FR-024 a FR-026) e logs técnicos (FR-031).
- **FR-003**: Uma reinicialização planejada do n8n (por exemplo, atualização da versão da imagem) DEVE voltar a receber updates em até 60 segundos, com uma única instância ativa (sem instância paralela). Reiniciar ou recriar o contêiner do n8n NÃO DEVE exigir nenhum passo manual na interface do n8n nem perder configuração operacional; nenhum estado de conversa, dado de domínio ou segredo versionado DEVE viver no n8n.
- **FR-004**: Todo segredo e toda credencial do n8n (token de serviço do backend, credencial da API de bots do Telegram, segredo do webhook, credencial da entrega proativa, chave de cifra da própria instância) DEVEM vir do ambiente ou de um cofre de segredos; toda variável nova DEVE constar do `.env.example` com explicação e sem valor secreto.
- **FR-005**: O n8n DEVE falar com o mesmo servidor local de API de bots em que o bot está registrado (002, Assumptions), e NÃO DEVE usar um endereço público da API do Telegram para o mesmo bot.
- **FR-006**: O n8n DEVE ter verificação de saúde que o mantenedor consiga consultar (o serviço está de pé e os fluxos esperados estão ativos).

**Workflows como código**

- **FR-007**: Os workflows (fluxo de entrada do Telegram, fluxo de entrega proativa e quaisquer fluxos de apoio estritamente de transporte) DEVEM ser versionados no repositório numa pasta própria, em formato que a instância consiga importar sem intervenção manual; a pasta DEVE nascer com o seu `CLAUDE.md`, e o `CLAUDE.md` da raiz DEVE ganhar a linha correspondente no mapa do projeto.
- **FR-008**: O que estiver na instância em execução DEVE ser reproduzível a partir do repositório e das variáveis de ambiente. A cada subida do contêiner, os workflows do repositório DEVEM ser importados e prevalecer sobre os da instância, sem passo manual; edição feita na instância fora do repositório é descartada na subida seguinte. DEVE existir uma verificação que compare a instância com o repositório e reprove em caso de divergência (inclusive edição manual feita entre duas subidas).
- **FR-009**: Os arquivos de workflow NÃO DEVEM conter segredo, token, ID de credencial, endereço com segredo no caminho nem dado de aluno; uma verificação automática DEVE reprovar o build se qualquer um aparecer, inclusive em exportações geradas a partir da instância.
- **FR-010**: Uma verificação automática (guardião de workflows) DEVE reprovar o build se algum workflow: tiver mais de um receptor de updates do Telegram; escrever em banco de dados; chamar provedor de IA; falar com qualquer destino diferente do backend e do servidor local de bots; ou tiver ramificação por conteúdo da conversa ou regra de negócio (as únicas ramificações admitidas são por tipo de ação e por resultado técnico de execução).
- **FR-011**: Nomes de workflows, de passos, de arquivos e de variáveis de ambiente introduzidos DEVEM seguir o Princípio VI (inglês, sufixos e glossário) e passar pelo guardião de idioma; o n8n NÃO DEVE conter nenhum texto mostrado ao professor — todo texto vem das ações do backend.

**Fluxo de entrada (updates do Telegram)**

- **FR-012**: DEVE haver um único fluxo de entrada para o bot, receptor de todos os updates do Telegram (o Telegram aceita um só webhook por bot).
- **FR-013**: O fluxo de entrada DEVE repassar ao backend o update bruto, sem alterar nem interpretar, junto do cabeçalho de segredo do webhook do Telegram e do token de serviço, e DEVE executar as ações devolvidas na ordem da lista, com texto, botões, rótulos, dados de botão, documento e mensagem a editar exatamente como o backend os definiu.
- **FR-014**: Toda mensagem recebida DEVE resultar em ao menos uma ação de resposta executada (inclusive `INTERNAL_ERROR` vinda do backend), e todo toque em botão DEVE ser confirmado (`CALLBACK_REPLY` executado primeiro), independentemente do resultado das demais ações.
- **FR-015**: Quando o backend estiver inacessível ou responder com falha ao repassar um update, o n8n NÃO DEVE responder com texto próprio nem registrar o update como tratado; DEVE devolver falha ao Telegram para que o update seja reenviado depois.
- **FR-016**: O n8n NUNCA DEVE receber, baixar nem repassar áudio: do update só segue a referência do arquivo, e o n8n NÃO DEVE chamar a obtenção de arquivo do Telegram (002, FR-028).

**Execução das ações e confirmação**

- **FR-017**: O n8n DEVE executar todas as ações de uma lista em ordem, sem interromper a lista quando uma falhar, e DEVE informar ao backend, por ação, o resultado: sucesso (com o identificador da mensagem criada, nas mensagens de etapa) ou falha com um dos códigos de motivo `MESSAGE_NOT_EDITABLE`, `CHAT_UNAVAILABLE`, `ACTION_NOT_SUPPORTED`, `DOCUMENT_UNAVAILABLE`, `RATE_LIMITED` e `TELEGRAM_ERROR`, sem texto de aluno.
- **FR-018**: O n8n NÃO DEVE decidir compensações, reenvios ou alternativas por conta própria; qualquer falha é apenas informada, e o backend emite, se for o caso, uma nova ação.
- **FR-019**: Diante de um tipo de ação desconhecido, o n8n DEVE pulá-la, informar `ACTION_NOT_SUPPORTED` e continuar; e DEVE ignorar campos desconhecidos de uma ação conhecida (evolução aditiva da v1).
- **FR-020**: Para uma ação de documento, o n8n DEVE obter os bytes do PDF por identificador opaco, numa única tentativa, e anexá-los ao envio; NUNCA DEVE montar endereço a partir de outro campo da ação; falha na busca DEVE ser informada como `DOCUMENT_UNAVAILABLE`. Os bytes do PDF NÃO DEVEM ser guardados pelo n8n além da execução da própria ação.

**Fluxo de entrega proativa (backend → n8n)**

- **FR-021**: O n8n DEVE oferecer ao backend um ponto de entrega interno para lotes de ações proativas, que valide a credencial de entrega (rejeitando o lote sem executar nada quando faltar ou não conferir) e execute as ações com as mesmas regras de ordem, falha, confirmação e documento dos requisitos anteriores.
- **FR-022**: O n8n DEVE tratar a repetição de uma ação proativa (mesmo identificador de ação) como a mesma ação: antes de executar, DEVE consultar o estado da ação no backend e pular as já executadas ou já falhadas, sem guardar estado próprio de deduplicação.
- **FR-023**: Com o n8n indisponível, as ações proativas NÃO DEVEM se perder: o backend as reentrega (002) e o n8n as executa uma única vez quando volta; e nenhuma ação aceita pelo n8n DEVE se perder se o n8n cair antes de executá-la inteira.

**Troca controlada e reversível**

- **FR-024**: DEVE existir um procedimento documentado e automatizado que, num único passo, aponte o webhook do bot para o n8n e passe a entrega proativa para o n8n, garantindo exatamente um caminho ativo por vez, sem envio duplicado nem perda (002, FR-019).
- **FR-025**: DEVE existir um procedimento de reversão, igualmente documentado e automatizado, que devolva os dois (webhook e entrega proativa) ao caminho antigo sem perda de estado de conversa; ambos os procedimentos DEVEM ser ensaiados em ambiente de teste antes de serem usados em produção.
- **FR-026**: O procedimento de troca DEVE se recusar a trocar quando o n8n não estiver saudável ou os fluxos esperados não estiverem ativos, sem alterar o webhook; e DEVE haver um passo de verificação pós-troca que confirme o destino do webhook, a entrega proativa configurada e uma ida e volta de mensagem de teste.
- **FR-027**: Enquanto o caminho antigo existir, o webhook atual do backend DEVE continuar interpretando e respondendo exatamente como antes; a troca e a reversão NÃO DEVEM exigir reimplantação do backend.

**Paridade e remoção do caminho antigo**

- **FR-028**: Todo o conjunto de testes de conversa e de paridade existente DEVE passar também pelo n8n, com as mesmas entradas e as mesmas expectativas de texto, botões e ordem, sem alterar nenhuma expectativa existente; os testes DEVEM rodar em contêineres, com o Telegram substituído por um simulador (Princípio V). A idempotência de ponta a ponta DEVE ser provada no CI por duas execuções adicionais: (a) a suíte de paridade com cada update entregue em duplicidade e cada lote proativo reentregue, exigindo a mesma transcrição do chat, sem mensagem a mais; (b) cenários em que o n8n é derrubado no meio de uma lista de ações, exigindo que as ações restantes cheguem ao chat uma única vez e as já executadas não se repitam.
- **FR-029**: O caminho antigo (o backend interpretando o webhook direto do Telegram e respondendo por ele, o envio direto de mensagens proativas, a escolha entre os dois caminhos e as chaves de configuração que só existiam para isso) DEVE ser removido somente depois de a paridade estar provada — suíte completa verde pelo n8n, período de operação em produção de 7 dias corridos sem divergência (zero updates sem resposta, zero respostas duplicadas, zero ações proativas com falha definitiva por causa do transporte, zero confirmações de toque perdidas, e nenhum relato de diferença visível por parte dos professores, registrado pelo dono do produto) e aprovação registrada do dono do produto — e em entrega separada da que liga o n8n, para que a reversão fique possível até a prova.
- **FR-030**: Na remoção, NÃO DEVE ser removido o que o backend ainda usa do servidor local de bots (download de áudio, 002 FR-028); e depois da remoção todos os testes DEVEM continuar passando, sem rota, classe, configuração ou variável de ambiente órfã.

**Privacidade e qualidade**

- **FR-031**: Nenhum log, histórico de execução, exportação ou mensagem de erro do n8n DEVE conter CPF, nome de aluno, texto de relatório, transcrição, bytes de PDF, token ou segredo; o histórico de execuções da instância NÃO DEVE reter o conteúdo das mensagens que passaram por ela, limitando-se a identificadores técnicos e códigos de situação (Princípio IV). Uma verificação automatizada DEVE provar isso.

**Documentação**

- **FR-032**: Os documentos da feature 001 que assumem "sem n8n" (`research.md` R-04 e "Alternativas consideradas", `plan.md` — Summary, Technical Context, estrutura e Complexity Tracking —, `quickstart.md` e `tasks.md`) DEVEM ser atualizados para refletir o transporte pelo n8n, preservando como registro histórico a decisão original e sem reescrever as demais decisões. Também DEVEM ser atualizados `infra/` (`CLAUDE.md`, `README.md`, `scripts/README.md`, `.env.example`), a tabela de `specs/CLAUDE.md`, o mapa do `CLAUDE.md` da raiz e os comentários que dizem "o compose é da feature 004".
- **FR-033**: A feature DEVE entregar um `quickstart.md` que permita ao mantenedor subir o ambiente, conferir a saúde do n8n, ensaiar a troca e a reversão, executar a verificação pós-troca e rotacionar o token de serviço.

### Key Entities *(include if feature involves data)*

- **Instância do n8n**: o serviço de transporte em contêiner; guarda apenas configuração operacional própria e nada do domínio; pode ser destruída e recriada a partir do repositório.
- **Workflow de transporte**: unidade versionada no repositório que descreve um fluxo do n8n; dois papéis — fluxo de entrada do Telegram (um só) e fluxo de entrega proativa; contém só passos de transporte.
- **Update de entrada**: o evento bruto produzido pelo Telegram, identificado por `update_id`; repassado sem alteração ao backend.
- **Ação de canal**: instrução do backend para executar no chat (`MESSAGE`, `EDIT`, `DOCUMENT`, `CALLBACK_REPLY`, `STEP_MESSAGE`), com identificador estável, ordem e conteúdo já formatado (002).
- **Confirmação de execução**: o resultado que o n8n devolve ao backend por ação — sucesso (com identificador da mensagem de etapa) ou falha com código de motivo.
- **Lote de entrega proativa**: conjunto ordenado de ações que o backend entrega ao n8n, com identificador estável do lote e das ações.
- **Registro do webhook**: o destino que o Telegram usa para entregar os updates do bot; muda entre o backend (caminho antigo) e o n8n.
- **Procedimento de troca e de reversão**: sequência documentada e automatizada que muda o webhook e a entrega proativa de forma conjunta, com verificação.
- **Evidência de paridade**: o conjunto de resultados (suíte pelo n8n, período de observação em produção, aprovação do dono do produto) que libera a remoção do caminho antigo.
- **Credencial de serviço do n8n**: o token (até dois válidos ao mesmo tempo) com o qual o n8n se identifica ao backend; vem do ambiente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% dos cenários dos testes de conversa e de paridade existentes passam também pelo caminho do n8n, com as mesmas entradas e sem nenhuma expectativa de texto, botão ou ordem alterada.
- **SC-002**: Para os professores, nenhuma diferença perceptível: a transcrição do chat de cada cenário de paridade, produzida pelo caminho do n8n, é idêntica à produzida pelo caminho antigo (textos, rótulos de botão, sequência de mensagens, PDFs e avisos entre canais).
- **SC-003**: Em condições normais, 95% dos updates (sem contar o processamento pesado de áudio, que é assíncrono) têm a primeira resposta executada no chat em até 2 segundos contados da chegada do update ao n8n, e a confirmação ao professor permanece dentro de 5 segundos (SC-003 da 002 e SC-007 da 001).
- **SC-004**: A partir de um ambiente vazio, o comando único deixa o n8n saudável e o bot operacional sem nenhum passo manual na interface do n8n; recriar a instância do zero produz o mesmo conjunto de workflows ativos e o mesmo comportamento (verificado pela comparação entre instância e repositório sem divergência).
- **SC-005**: Zero ocorrências de segredo, ID de credencial, CPF, nome de aluno, texto de relatório, transcrição ou bytes de PDF em arquivos de workflow, logs, histórico de execuções e exportações do n8n, verificado por varredura automatizada.
- **SC-006**: Com o n8n indisponível por até o prazo de entrega das ações proativas, 100% das ações pendentes são entregues após a volta dele e 0 são executadas em duplicidade no chat; e reiniciar o n8n no meio de uma conversa perde 0 estado (a conversa continua da mesma etapa); uma reinicialização planejada devolve o n8n a receber updates em até 60 segundos, com as mensagens recebidas nesse intervalo respondidas uma única vez, com atraso e sem perda.
- **SC-007**: A troca do webhook e a reversão se completam, cada uma, em até 5 minutos de trabalho do mantenedor e sem nenhuma mensagem sem resposta nem respondida em duplicidade; ambas foram ensaiadas com sucesso em ambiente de teste antes de a primeira troca em produção.
- **SC-008**: 100% das mensagens recebidas pelo bot recebem resposta e 100% dos toques em botão são confirmados, inclusive quando uma ação intermediária da lista falha; 100% das falhas de execução chegam ao backend com código de motivo e nenhuma interrompe as ações seguintes.
- **SC-009**: Nenhum workflow tem mais de um receptor de updates do Telegram, escreve em banco, chama IA ou contém regra de negócio, verificado a cada build.
- **SC-010**: Depois da remoção do caminho antigo, o conjunto completo de testes passa sem alteração de expectativa e nenhuma rota, classe, configuração ou variável de ambiente do caminho antigo permanece; e nenhum documento da feature 001 afirma, como situação atual, que não há n8n.
- **SC-011**: Em 100% dos cenários de paridade repetidos com updates e lotes proativos em duplicidade, a transcrição do chat é idêntica à da execução sem duplicidade, e em 100% dos cenários com queda do n8n no meio de uma lista nenhuma ação é perdida nem repetida.

## Assumptions

- A API interna de canal da feature 002 está implementada e estável (rotas de update, confirmação, documento e estado de ação; entrega proativa com reentrega e idempotência; token de serviço com rotação); esta feature a consome e não a altera, salvo mudança aditiva da v1 que o plano justifique.
- O bot continua registrado no servidor local de API de bots, que fica na rede interna e entrega os updates por webhook também interno. Por isso o n8n não precisa de endereço público; o endereço do webhook do bot passa a ser o do n8n na rede interna. O n8n fala com esse mesmo servidor local para enviar mensagens, editar, enviar documentos e confirmar toques.
- Quando o endereço do webhook não responde com sucesso, o servidor do Telegram retém e reenvia o update; a deduplicação por `update_id` do backend impede processamento duplo. O n8n não guarda estado de deduplicação.
- O n8n guarda apenas o necessário para operar a si mesmo (configuração e credenciais cifradas da instância); isso não é "dado de domínio" no sentido do Princípio II, desde que nenhum estado de conversa, texto de mensagem ou dado de aluno seja retido (FR-003 e FR-031).
- Credenciais do n8n são provisionadas a partir do ambiente na criação da instância e referenciadas pelos workflows por nome lógico, nunca por ID de credencial versionado; o mecanismo exato é decisão do plano.
- O plano decide como garantir FR-023 quando o n8n aceita uma entrega proativa e cai antes de executar tudo: ou o n8n só aceita (`2xx`) depois de executar e confirmar o lote, dentro do tempo de resposta configurado, ou o backend passa a reentregar ações entregues e ainda não confirmadas. Se for a segunda opção, a mudança no contrato da 002 é aditiva.
- A prova de paridade tem três partes: suíte completa verde pelo n8n com o Telegram simulado, período de operação em produção de 7 dias corridos sem divergência (critério objetivo e relato dos professores, definidos em FR-029) e aprovação registrada do dono do produto. A remoção é entregue numa fatia (e num PR) separada da que liga o n8n; o merge de qualquer PR continua sendo feito pelo usuário após revisão (Princípio VIII).
- Os textos, rótulos de botão, estados e palavras-chave do bot não mudam; qualquer necessidade de texto novo fica fora desta feature.
- O envio de áudio continua exclusivamente entre Telegram e backend; o n8n só repassa a referência do arquivo.
- Fora de escopo: qualquer regra de negócio ou autorização no n8n; novas funcionalidades do bot; mudança de textos; administração pelo Telegram; novos canais.
