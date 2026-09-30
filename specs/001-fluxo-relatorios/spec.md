# Feature Specification: Fluxo de envio e criação de relatórios pedagógicos (Web e Telegram)

**Feature Branch**: `001-fluxo-relatorios`

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "implementar agora todo o fluxo de envio e de criação de relatórios do sistema, use os materiais de apoio HANDOFF_MVP_Relatorios_Escola_Musica.md e o mvp anterior. Use o projeto do Claude Design (Telas.dc.html, assets/fb-monograma-preto.png, support.js) e use também o portal-do-professor-design-system como material para o design."

**Materiais de referência**:

- `HANDOFF_MVP_Relatorios_Escola_Musica.md`: regras de negócio (RN-xx), fluxos, textos e lições aprendidas do MVP.
- `portal-do-professor-design-system/project/`: protótipos navegáveis (`Telas.dc.html`, `Portal do Professor.dc.html`, `Modelo PDF.dc.html`, `Design System.dc.html`) e ativos da marca. O comportamento descrito aqui segue as telas 01 a 12 do Portal do Professor e o Modelo do PDF.
- MVP `mvp-relatorios` (somente leitura), como referência de comportamento.

**Decisões de escopo confirmadas pelo dono do produto (2026-09-30)**:

- O **Telegram e a Web** são os dois canais de entrada e de saída de relatórios de aula nesta feature. Conforme a constituição, o relatório semestral e o histórico ficam somente na Web.
- O **acesso do professor** (entrar com código, sessão, bloqueio por tentativas, vinculação do Telegram) e o **controle de acesso por aluno** fazem parte desta feature. Cadastros e telas da secretaria ficam para uma feature própria; até lá, professores, alunos e associações entram por carga inicial.
- O relatório semestral consolida **somente as aulas aprovadas do professor que gera o relatório**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar uma aula pelo Portal Web até a aprovação (Priority: P1)

A professora Clara termina a aula de Matteo, abre o Portal do Professor no celular, entra com o código de acesso, escolhe o aluno, grava um áudio contando como foi a aula (ou envia um arquivo de áudio gravado antes) e toca em "Enviar áudio e gerar relatório". O sistema mostra o progresso (enviando, transcrevendo, estruturando, pronto para conferência). Se faltar alguma informação, o sistema mostra todas as perguntas de uma vez e ela responde numa única mensagem. Na conferência, ela lê o relatório em texto e no PDF, pede um ajuste com suas palavras ("o Matteo teve mais facilidade em leitura do que consta nas dificuldades"), confere a nova versão e aprova. O relatório passa a ser oficial e entra no histórico do aluno.

**Why this priority**: é a proposta de valor central do produto ("o professor manda um áudio e recebe um relatório pronto para revisar") e o caminho mais usado. Sozinha, esta história já entrega um produto utilizável.

**Independent Test**: com um professor e um aluno associado na carga inicial, entrar no portal com o código, enviar uma das gravações fictícias de `assets/`, responder às perguntas (se houver), pedir uma alteração e aprovar; verificar que o relatório aprovado tem a versão aprovada, o PDF correspondente e aparece no histórico.

**Acceptance Scenarios**:

1. **Given** um professor ativo com código válido, **When** ele informa o código na tela "Entrar", **Then** acessa o Painel com a saudação "Olá, {nome}", o total de alunos associados e a lista dos primeiros alunos.
2. **Given** o professor no Painel, **When** escolhe "Registrar aula" e um aluno sem relatório em aberto, **Then** vê a tela "Novo relatório de aula" com o nome do aluno e a data de hoje no fuso da escola, podendo gravar pelo navegador ou enviar um arquivo de áudio.
3. **Given** uma gravação concluída, **When** o professor a ouve, **Then** pode regravar ou enviar; o botão de envio só fica disponível quando há gravação concluída ou arquivo selecionado.
4. **Given** o navegador sem suporte a gravação ou com o microfone bloqueado, **When** o professor abre "Novo relatório de aula", **Then** vê o aviso "Gravação indisponível neste navegador..." e pode apenas enviar arquivo.
5. **Given** um áudio enviado, **When** o processamento avança, **Then** a tela mostra as etapas "Enviando o áudio", "Transcrevendo", "Estruturando o relatório" e "Pronto para conferência", sem que o professor precise recarregar a página.
6. **Given** um relatório cuja estruturação identificou informações faltantes, **When** o processamento termina, **Then** o professor vê "Faltam algumas informações", com todas as perguntas numeradas e um único campo de resposta.
7. **Given** a resposta às perguntas enviada, **When** o sistema a incorpora ao relatório, **Then** os campos correspondentes são atualizados com base na resposta e o relatório segue para conferência.
8. **Given** um relatório em conferência, **When** o professor abre a tela, **Then** vê "Confira o relatório antes de aprovar", a versão vigente, o cabeçalho (aluno, professor, data da aula), as cinco seções em texto e o PDF da mesma versão (em abas "Texto"/"Ver PDF" no celular; lado a lado no computador).
9. **Given** um relatório em conferência, **When** o professor descreve uma alteração e toca "Solicitar alteração", **Then** vê "Aplicando sua alteração…", e em seguida uma nova versão (N+1) com novo PDF e o aviso "Alteração aplicada. Confira a versão N+1."
10. **Given** um relatório em conferência na versão N, **When** o professor toca "Aprovar relatório · versão N" e confirma no diálogo, **Then** o relatório fica aprovado, a tela "Relatório aprovado" é exibida com a data e hora da aprovação e atalhos para registrar outra aula, ver o histórico ou voltar aos alunos.

---

### User Story 2 - Registrar uma aula pelo Telegram até a aprovação (Priority: P1)

O professor vincula sua conta do Telegram enviando ao bot o código de acesso recebido da secretaria. Depois de cada aula, ele envia o nome do aluno, grava uma nota de voz e recebe no próprio chat as perguntas pendentes (se houver) e depois o PDF para conferência. Ele responde "aprovar" para confirmar, escreve com suas palavras a alteração desejada ou responde "cancelar".

**Why this priority**: o Telegram é o canal de menor atrito logo após a aula e já é usado pelos professores no MVP; é um dos dois canais de entrada e saída definidos pelo dono do produto.

**Independent Test**: com um professor e um aluno associado na carga inicial, vincular uma conta de teste do Telegram com o código, selecionar o aluno por nome, enviar uma gravação fictícia como nota de voz, responder às perguntas, pedir uma alteração, receber o novo PDF e aprovar; verificar que o relatório aprovado aparece no histórico do aluno no Portal Web.

**Acceptance Scenarios**:

1. **Given** uma conta do Telegram não vinculada, **When** o usuário envia qualquer mensagem que não seja um código válido, **Then** o bot responde pedindo o código de acesso fornecido pela secretaria.
2. **Given** uma conta do Telegram não vinculada e um professor ativo sem Telegram vinculado, **When** o usuário envia o código válido desse professor, **Then** a conta é vinculada e o bot responde "Conta vinculada com sucesso, {nome}! Para registrar uma aula, envie o nome do aluno."
3. **Given** um professor vinculado, **When** ele envia um texto que corresponde a exatamente um aluno associado a ele (ignorando maiúsculas, minúsculas e acentos, aceitando parte do nome), **Then** o bot confirma "Aluno {nome} selecionado! Envie o áudio da aula quando quiser."
4. **Given** um texto que corresponde a mais de um aluno ou a nenhum, **When** o professor o envia, **Then** o bot lista os alunos possíveis (ou todos os seus alunos) para que ele escolha.
5. **Given** um aluno selecionado, **When** o professor envia uma nota de voz ou um arquivo de áudio, **Then** o bot confirma o recebimento citando o aluno e, ao fim do processamento, envia todas as perguntas pendentes numa única mensagem ou o PDF para conferência com a legenda de instruções ("aprovar", alteração com exemplo, "cancelar").
6. **Given** um relatório em conferência no Telegram, **When** o professor responde com um texto que não é palavra-chave, **Then** o texto é tratado como pedido de alteração: nova versão, novo PDF enviado no chat.
7. **Given** um relatório em conferência no Telegram, **When** o professor responde "aprovar", **Then** o sistema aprova a versão cujo PDF foi enviado por último no chat e responde "Relatório aprovado e salvo no histórico do aluno."
8. **Given** um relatório em aberto no Telegram, **When** o professor responde "cancelar", **Then** o relatório é cancelado, a seleção de aluno é limpa e o bot orienta a enviar o nome do aluno para começar outro.
9. **Given** qualquer falha (áudio inválido, sem aluno selecionado, aluno não associado, arquivo grande demais, falha de processamento, versão desatualizada), **When** ela ocorre, **Then** o bot sempre responde com uma mensagem específica que diz o que aconteceu e o que fazer; nenhuma mensagem do professor fica sem resposta.

---

### User Story 3 - Continuar um relatório em aberto, em qualquer canal (Priority: P2)

O professor começou um relatório pelo Telegram e não terminou. Mais tarde, no portal, ao escolher o mesmo aluno, o sistema avisa que existe um relatório em aberto iniciado pelo Telegram (com a data da última alteração) e oferece "Continuar de onde parei" ou "Descartar e começar um novo". O mesmo vale no sentido inverso.

**Why this priority**: os dois canais compartilham a mesma identidade e o mesmo ciclo de vida; sem a retomada, rascunhos ficariam presos no canal de origem e poderiam gerar relatórios duplicados.

**Independent Test**: iniciar um relatório pelo Telegram, parar antes da aprovação, abrir o mesmo aluno no portal, escolher "Continuar de onde parei" e concluir a aprovação na Web; repetir escolhendo "Descartar" e verificar que o relatório original fica cancelado e não aparece mais.

**Acceptance Scenarios**:

1. **Given** um relatório de aula em aberto para o aluno, iniciado em qualquer canal, **When** o professor escolhe "Novo relatório de aula" na Web, **Then** vê "Você tem um relatório em aberto", com o canal de origem e a data e hora da última alteração.
2. **Given** a tela de relatório em aberto, **When** o professor escolhe "Continuar de onde parei", **Then** vai direto para a etapa em que o relatório parou (processamento, perguntas ou conferência).
3. **Given** a tela de relatório em aberto, **When** o professor escolhe "Descartar e começar um novo" e confirma no diálogo, **Then** o relatório anterior é cancelado e a tela "Novo relatório de aula" é aberta.
4. **Given** um relatório em aberto de um aluno, **When** o professor abre o Painel, **Then** vê o cartão "Relatório em aberto" com o aluno e a data da última alteração, e a lista de alunos marca o aluno como "Em aberto".
5. **Given** um relatório em aberto iniciado na Web, **When** o professor seleciona o mesmo aluno no Telegram, **Then** o bot informa que existe um relatório em aberto para esse aluno, reenvia as perguntas pendentes ou o PDF vigente, e aceita resposta, alteração, "aprovar" ou "cancelar".
6. **Given** um relatório que foi alterado em outro canal depois que o professor abriu a conferência, **When** ele tenta aprovar, **Then** a aprovação é recusada, a tela mostra "Esta versão está desatualizada..." com a opção "Recarregar", e só a versão vigente pode ser aprovada.

---

### User Story 4 - Gerar e aprovar o relatório semestral (Priority: P2)

Ao fim do semestre, o professor abre "Relatório semestral" de um aluno no portal, escolhe o período numa lista (o mais recente vem pré-selecionado) e gera o relatório. O sistema consolida as aulas aprovadas por ele naquele semestre em um documento com 10 seções, informa quantas aulas foram consideradas e segue o mesmo ciclo de conferência, alteração e aprovação.

**Why this priority**: resolve a segunda dor do cliente (o relatório consolidado a cada seis meses), mas depende de haver aulas aprovadas, produzidas pelas histórias 1 e 2.

**Independent Test**: com aulas aprovadas de um aluno em um semestre, gerar o semestral, verificar a contagem, pedir uma alteração, aprovar e conferir que aparece no histórico; repetir num semestre sem aulas aprovadas e verificar que nada é criado.

**Acceptance Scenarios**:

1. **Given** um aluno associado, **When** o professor abre "Relatório semestral", **Then** vê os semestres disponíveis (1º semestre = 01/01 a 30/06; 2º = 01/07 a 31/12), com o mais recente pré-selecionado e o intervalo de datas do período escolhido.
2. **Given** um semestre com N ≥ 1 aulas aprovadas do professor para o aluno, **When** ele toca "Gerar relatório semestral", **Then** vê o progresso da consolidação e, ao fim, a conferência com "N relatórios de aula foram considerados nesta consolidação", as 10 seções em texto (recolhíveis no celular) e o PDF.
3. **Given** um semestre sem aulas aprovadas do professor para o aluno, **When** ele tenta gerar, **Then** vê "Nenhum relatório de aula aprovado neste período. Registre as aulas do semestre antes de gerar o relatório." e nada é criado.
4. **Given** um relatório semestral em conferência, **When** o professor pede uma alteração ou aprova, **Then** valem as mesmas regras do relatório de aula: nova versão com novo PDF a cada alteração e aprovação somente da versão vigente, com confirmação.
5. **Given** um semestral já aprovado para o mesmo aluno, professor e período, **When** o professor tenta gerar outro, **Then** o sistema recusa e informa que o relatório daquele semestre já foi aprovado.
6. **Given** um semestral em conferência para o mesmo aluno, professor e período, **When** o professor tenta gerar outro, **Then** o sistema oferece continuar o existente ou descartá-lo e gerar um novo.

---

### User Story 5 - Consultar o histórico e abrir os PDFs oficiais (Priority: P3)

O professor abre o histórico de um aluno no portal e vê os relatórios aprovados (aula e semestral), do mais recente ao mais antigo, com a data de aprovação, e abre o PDF oficial de qualquer um deles.

**Why this priority**: é a saída consolidada do fluxo e dá confiança ao professor, mas não bloqueia o registro nem a aprovação.

**Independent Test**: com relatórios aprovados, abrir o histórico do aluno, verificar ordem, rótulos e datas, abrir um PDF; tentar abrir o PDF de um relatório de outro professor e verificar que o acesso é negado.

**Acceptance Scenarios**:

1. **Given** um aluno com relatórios aprovados do professor, **When** ele abre "Histórico", **Then** vê a lista do mais recente ao mais antigo, com "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)" e "Aprovado em dd/mm/aaaa às hh:mm".
2. **Given** um item do histórico, **When** o professor toca "Abrir PDF", **Then** o PDF oficial da versão aprovada abre (ou é baixado, se o navegador impedir a abertura).
3. **Given** um aluno sem relatórios aprovados do professor, **When** ele abre o histórico, **Then** vê "Este aluno ainda não tem relatórios aprovados." e o atalho "Registrar aula".

---

### Edge Cases

- **Áudio sem fala, vazio, corrompido ou em formato não suportado**: nada é criado; a Web mostra "Não foi possível processar o áudio" com a opção de gravar novamente, e o Telegram explica o problema e pede um novo envio.
- **Áudio acima do limite de tamanho**: recusado antes do processamento, com a mensagem "O áudio enviado é maior que o limite permitido. Grave um trecho mais curto." No Telegram também vale o limite de download de arquivos do próprio Telegram.
- **Falha na estruturação depois de a transcrição dar certo**: o relatório fica em "falha no processamento", com a transcrição guardada; o professor pode "Tentar novamente" sem reenviar o áudio, ou cancelar.
- **Resposta inválida da IA** (formato inesperado ou campos ausentes): o sistema tenta corrigir uma vez; se não conseguir, trata como falha na estruturação (item anterior). Campos que a IA não conseguiu preencher ficam vazios, nunca inventados.
- **Perguntas pendentes que continuam depois da resposta**: no máximo 2 rodadas de perguntas por relatório; depois disso o relatório segue para conferência com os campos faltantes vazios, e o professor pode completá-los por alteração.
- **Nota de voz enviada no Telegram com um relatório em aberto do aluno selecionado**: o bot não inicia outro relatório; avisa que existe um relatório em aberto e pede para concluir ou cancelar antes.
- **Nome de aluno enviado no Telegram durante a conferência**: se o texto corresponde exatamente a outro aluno associado, o bot pergunta se o professor quer trocar de aluno (mantendo o relatório atual em aberto) em vez de tratar o texto como alteração.
- **Aprovar pelo Telegram depois de o relatório ter sido alterado na Web**: a aprovação é recusada; o bot envia o PDF da versão vigente e pede nova confirmação.
- **Aprovação dupla ou concorrente** (dois canais ao mesmo tempo): somente a primeira aprovação da versão vigente vale; a segunda recebe a informação de que o relatório já foi aprovado.
- **Cancelar ou alterar relatório já aprovado ou cancelado**: recusado com mensagem clara; o relatório aprovado não muda.
- **Aluno não associado ao professor** (URL manipulada, nome digitado no Telegram): acesso negado; o sistema não revela dados do aluno.
- **Relatório de outro professor**: tratado como inexistente para quem não é o dono.
- **Sessão expirada no meio do fluxo Web**: o professor é levado imediatamente à tela "Entrar"; o relatório em aberto continua salvo e pode ser retomado.
- **Conexão perdida durante o processamento na Web**: o processamento continua no servidor; ao voltar, o professor encontra o relatório no ponto em que parou (via retomada).
- **Processamento que excede o tempo esperado**: depois de 3 minutos sem conclusão, a Web avisa que está demorando mais que o normal e oferece voltar mais tarde; o relatório aparece como em aberto.
- **Código com 5 tentativas incorretas seguidas**: bloqueio de 15 minutos para aquela origem, inclusive para o código correto, com a mensagem "Acesso bloqueado temporariamente..." e o tempo restante.
- **Telegram já vinculado a outro professor, ou professor já vinculado a outra conta do Telegram**: vinculação recusada com mensagem específica, orientando procurar a secretaria.
- **Professor sem alunos associados**: o Painel mostra "Você ainda não tem alunos associados. Fale com a secretaria da escola."; o bot responde a mesma orientação.
- **Aula registrada perto da meia-noite**: a data da aula é sempre a de hoje no fuso da escola, nunca a do servidor.
- **Semestre corrente em andamento**: pode ser gerado com as aulas aprovadas até o momento; a lista indica "em andamento".
- **Arquivo de vídeo (ex.: .mp4 gravado no celular)**: aceito, e apenas a trilha de áudio é usada.

## Requirements *(mandatory)*

### Functional Requirements

**Acesso do professor e controle de acesso**

- **FR-001**: O sistema DEVE permitir que o professor entre no Portal Web informando o seu código de acesso de 8 caracteres (letras maiúsculas e dígitos, sem caracteres ambíguos), validando que o código existe, não expirou e pertence a um professor ativo.
- **FR-002**: O código de acesso DEVE valer por 7 dias a partir da emissão, e cada entrada bem-sucedida no portal DEVE renovar a validade por mais 7 dias. Entrar no portal NÃO DEVE consumir o código.
- **FR-003**: Vincular o Telegram NÃO DEVE invalidar o código para o portal. Um professor já vinculado NÃO DEVE conseguir vincular uma segunda conta do Telegram com o mesmo código, e uma conta do Telegram NÃO DEVE ser vinculada a mais de um professor.
- **FR-004**: Após 5 códigos incorretos consecutivos de uma mesma origem (no portal) ou de uma mesma conta do Telegram (no bot), o sistema DEVE recusar novas tentativas por 15 minutos, inclusive com o código correto, informando o tempo restante. Um acesso bem-sucedido DEVE zerar a contagem, e o fim do bloqueio também. O limite e a duração DEVEM ser configuráveis.
- **FR-005**: A sessão do professor no portal DEVE expirar após 12 horas sem uso (configurável) e DEVE ser renovada a cada uso. "Encerrar sessão" DEVE invalidar a sessão no servidor.
- **FR-006**: O professor DEVE ver, gerar, conferir, alterar, aprovar e cancelar relatórios somente de alunos associados a ele. Aluno não associado DEVE resultar em acesso negado. Relatório de outro professor DEVE ser tratado como inexistente. A identidade do professor DEVE vir sempre da sessão ou do vínculo do Telegram, nunca de dados enviados pelo cliente.
- **FR-007**: A identidade do Telegram (vínculo) DEVE servir apenas para identificar o professor no canal e NÃO DEVE autorizar nenhuma ação administrativa.
- **FR-008**: Professores, alunos (nome, data de nascimento, CPF, nome do responsável quando menor de idade, instrumento opcional) e associações professor–aluno DEVEM poder ser carregados por um procedimento operacional de carga inicial, com CPF armazenado cifrado e nunca exibido. O mesmo procedimento DEVE emitir e reemitir códigos de acesso até que a feature da secretaria exista.

**Registro de aula por áudio (comum aos canais)**

- **FR-009**: O professor DEVE poder registrar uma aula para um aluno associado enviando um áudio: na Web, gravado no navegador ou por arquivo; no Telegram, por nota de voz ou arquivo de áudio/vídeo.
- **FR-010**: O sistema DEVE aceitar os formatos de áudio comuns de celular e navegador (incluindo MP3, M4A, OGG/Opus, WAV, WebM) e vídeos MP4, dos quais usa apenas o áudio, até 50 MB (limite configurável). O limite vigente DEVE ser mostrado ao professor na tela de envio.
- **FR-011**: A data da aula DEVE ser a data de hoje no fuso da escola (padrão: America/Sao_Paulo).
- **FR-012**: O áudio DEVE ser transcrito antes de qualquer registro de aula ser criado. Se a transcrição falhar (áudio vazio, sem fala, corrompido, formato não suportado, falha do provedor), nenhuma aula ou relatório DEVE ser criado e o professor DEVE ser informado do motivo.
- **FR-013**: O áudio NÃO DEVE ser retido após a transcrição; ficam apenas a transcrição e o relatório.
- **FR-014**: A partir da transcrição, o sistema DEVE estruturar o relatório de aula em cinco seções (Conteúdos trabalhados, Evolução, Dificuldades, Atividades propostas, Observações) e identificar perguntas pendentes quando uma informação relevante não puder ser extraída com confiança.
- **FR-015**: A estruturação NÃO DEVE inventar informação ausente da transcrição ou das respostas do professor; o que faltar DEVE ficar vazio ou virar pergunta pendente.
- **FR-016**: O processamento (transcrição e estruturação) DEVE ocorrer sem prender o professor numa espera cega: o relatório DEVE ter um estado consultável (em processamento, falha no processamento, aguardando respostas, aguardando conferência, aprovado, cancelado) e os canais DEVEM mostrar o avanço e o resultado sem ação manual do professor.
- **FR-017**: Se a estruturação falhar depois de a transcrição ter sido salva, o relatório DEVE ficar em "falha no processamento" e o professor DEVE poder tentar de novo sem reenviar o áudio, ou cancelar.
- **FR-018**: Os dados enviados a provedores de IA DEVEM se limitar ao necessário para a tarefa: transcrição, respostas e instruções do professor, conteúdo do relatório. Nome, CPF, data de nascimento e nome do responsável NÃO DEVEM ser enviados.

**Perguntas pendentes**

- **FR-019**: Com perguntas pendentes, o relatório DEVE ficar "aguardando respostas", sem PDF, e o canal DEVE apresentar todas as perguntas de uma vez (Web: lista numerada e um campo de resposta; Telegram: uma única mensagem numerada).
- **FR-020**: A resposta do professor (texto obrigatório) DEVE ser incorporada pela IA aos campos adequados do relatório, e não apenas anexada às observações. Se ainda restarem lacunas, o sistema PODE fazer uma nova rodada de perguntas, até o limite de 2 rodadas por relatório; depois disso o relatório DEVE seguir para conferência com as lacunas vazias.

**Conferência, alteração, aprovação e cancelamento**

- **FR-021**: Ao chegar a "aguardando conferência", o sistema DEVE gerar o PDF da versão vigente e disponibilizá-lo junto com o texto das seções (Web) ou enviá-lo como documento no chat (Telegram).
- **FR-022**: O professor DEVE poder pedir alterações em linguagem natural (texto obrigatório). Cada alteração DEVE criar uma nova versão (número inteiro a partir de 1, incrementado a cada alteração) com seu próprio PDF, sem sobrescrever o conteúdo nem o PDF das versões anteriores.
- **FR-023**: A aprovação DEVE referir-se a uma versão identificada e DEVE ser recusada se essa não for a versão vigente. Na Web, a versão é a exibida na tela; no Telegram, é a do último PDF enviado no chat. Só relatórios "aguardando conferência" podem ser aprovados.
- **FR-024**: Na Web, a aprovação DEVE exigir confirmação num diálogo que informe que o relatório entrará no histórico e não poderá mais ser alterado. No Telegram, a palavra "aprovar" é a confirmação.
- **FR-025**: A aprovação DEVE ser irreversível: o relatório aprovado DEVE registrar data, hora e versão aprovada, entrar no histórico do aluno e ter um PDF oficial que indique quem aprovou, quando e qual versão.
- **FR-026**: O professor DEVE poder cancelar um relatório em aberto (falha no processamento, aguardando respostas ou aguardando conferência). Na Web, isso exige confirmação. O relatório cancelado DEVE deixar de aparecer para o professor e não poder ser reaberto, mas o registro DEVE ser mantido internamente como cancelado para rastreabilidade. Cancelar um relatório aprovado ou já cancelado DEVE ser recusado.
- **FR-027**: Pedir alteração, responder perguntas ou tentar de novo DEVE ser recusado para relatórios aprovados ou cancelados.
- **FR-028**: Se a versão vigente mudar em outro canal enquanto o professor confere uma versão anterior, a Web DEVE avisar que a versão está desatualizada e oferecer recarregar antes de permitir aprovar ou alterar.

**Relatório em aberto e retomada entre canais**

- **FR-029**: Deve existir no máximo um relatório de aula em aberto por professor e aluno. Ao iniciar um novo relatório para um aluno que já tem um em aberto (de qualquer canal), o sistema DEVE informar o canal de origem e a data e hora da última alteração, e oferecer continuar ou descartar (cancelar) e começar outro.
- **FR-030**: Um relatório iniciado num canal DEVE poder ser continuado, alterado, aprovado ou cancelado no outro.
- **FR-031**: O Painel da Web DEVE destacar os relatórios em aberto do professor, e a lista de alunos DEVE marcar os alunos com relatório em aberto.

**Canal Telegram**

- **FR-032**: Cada mensagem recebida pelo bot DEVE ser interpretada a partir do estado persistido no sistema (vínculo, aluno selecionado, relatório em aberto do aluno selecionado e etapa desse relatório), e não da memória do canal. Reiniciar o componente do canal NÃO DEVE perder o estado da conversa.
- **FR-033**: A seleção de aluno no Telegram DEVE procurar entre os alunos associados ao professor, ignorando maiúsculas, minúsculas e acentos, e aceitando parte do nome. Com várias correspondências, o bot DEVE listar as opções para desambiguação. Sem correspondência, DEVE listar os alunos do professor.
- **FR-034**: Com um aluno selecionado e sem relatório em aberto para ele, um áudio DEVE iniciar um novo relatório; um texto DEVE ser tratado como troca de aluno. Com um relatório em aberto do aluno selecionado: "cancelar" cancela; "aprovar" aprova (quando aguardando conferência); outro texto é resposta (quando aguardando respostas) ou pedido de alteração (quando aguardando conferência); um novo áudio é recusado com orientação.
- **FR-035**: Ao selecionar um aluno que já tem relatório em aberto (de qualquer canal), o bot DEVE avisar e reapresentar a etapa atual (perguntas pendentes ou o PDF vigente).
- **FR-036**: A seleção de aluno DEVE ser limpa após aprovar ou cancelar o relatório.
- **FR-037**: O bot DEVE responder a toda mensagem recebida. Cada tipo de falha (código inválido ou expirado, conflito de vínculo, bloqueio por tentativas, nenhum aluno selecionado, aluno não encontrado, aluno não associado, áudio inválido, áudio grande demais, falha de processamento, versão desatualizada, ação não permitida no estado atual, tipo de mensagem não suportado) DEVE ter mensagem própria, em português com acentuação correta, dizendo o que fazer em seguida.
- **FR-038**: Ao receber um áudio, o bot DEVE confirmar o recebimento (citando o aluno) antes de concluir o processamento, para que o professor saiba que o áudio foi aceito.

**Relatório semestral (somente Web)**

- **FR-039**: O sistema DEVE oferecer, para cada aluno associado, uma lista de semestres disponíveis (chave AAAA-S; rótulos "1º semestre AAAA"/"2º semestre AAAA"), do semestre da primeira aula registrada do aluno com o professor (ou do cadastro do aluno, se anterior) até o semestre corrente, com o mais recente pré-selecionado. Sem semestres disponíveis, DEVE mostrar "Nenhum semestre disponível para este aluno ainda."
- **FR-040**: Antes de consolidar, o sistema DEVE contar os relatórios de aula aprovados do professor para o aluno com data de aula dentro do período (datas inclusive). Com zero, nada DEVE ser criado, nenhuma chamada à IA DEVE ser feita e o professor DEVE ser informado.
- **FR-041**: A consolidação DEVE usar exclusivamente os relatórios de aula aprovados do professor no período e produzir as 10 seções do modelo da escola: Informações Gerais, Frequência e Estudo, Técnica, Musicalidade, Leitura e Memorização, Pontos de Atenção, Estratégias Pedagógicas, Acompanhamento Familiar, Planejamento do Próximo Semestre e Parecer Final.
- **FR-042**: Os dados de identificação de Informações Gerais (nome e idade do aluno, instrumento quando houver, professor, período) e a quantidade de aulas consideradas DEVEM ser preenchidos pelo próprio sistema a partir do cadastro e dos registros, e NÃO DEVEM ser enviados à IA nem gerados por ela. As datas das aulas consideradas PODEM ser enviadas à IA para apoiar a análise de frequência.
- **FR-043**: O relatório semestral DEVE seguir o mesmo ciclo do relatório de aula: nasce "aguardando conferência", versão 1, com PDF; alterações em linguagem natural criam novas versões com novo PDF; aprovação só da versão vigente, com confirmação; cancelamento com confirmação.
- **FR-044**: DEVE existir no máximo um relatório semestral em aberto e no máximo um aprovado por aluno, professor e período. Gerar com um em aberto DEVE oferecer continuar ou descartar; gerar com um aprovado DEVE ser recusado.

**Histórico e documentos**

- **FR-045**: O Portal Web DEVE mostrar o histórico de cada aluno associado, com os relatórios aprovados (aula e semestral) de autoria do professor, do mais recente ao mais antigo (aula pela data da aula; semestral pelo início do período), e a data e hora de aprovação.
- **FR-046**: O download de qualquer PDF DEVE exigir usuário autenticado e autorizado para aquele relatório específico (dono do relatório), e não apenas o conhecimento do nome ou do endereço do arquivo.
- **FR-047**: Os PDFs DEVEM seguir o Modelo do PDF do design: identidade do Instituto Musical Felipe Blaft, título ("Relatório de Aula"/"Relatório Semestral"), cabeçalho com aluno, professor e data da aula ou período, seções com rótulos legíveis e acentuação correta, "—" para seções vazias, rodapé institucional com numeração de páginas. O PDF semestral DEVE informar quantos relatórios de aula foram consolidados. PDFs de versões ainda não aprovadas DEVEM ser identificados como documento em conferência (não oficial).

**Interface e identidade visual**

- **FR-048**: As telas do Portal do Professor DEVEM seguir os protótipos de `portal-do-professor-design-system` (telas 01 a 12 e variações de estado) e o design system do projeto: paleta marfim, preto e dourado, tipografia serifada nos títulos e sem serifa na interface, monograma FB.
- **FR-049**: Todas as telas do fluxo DEVEM funcionar a partir de 390 px de largura (mobile-first) e também em telas de computador, e DEVEM atender a WCAG 2.1 nível AA, inclusive no modo escuro.
- **FR-050**: O portal DEVE oferecer modo claro e modo escuro, alternáveis pelo menu do professor.
- **FR-051**: O portal DEVE oferecer navegação por Painel, Alunos (com busca por nome) e Registrar aula ("Para qual aluno?"), além de busca de aluno no cabeçalho em telas largas.
- **FR-052**: Os estados de carregamento, lista vazia, busca sem resultado, falha e sucesso DEVEM ter tratamento visual e textual próprio, conforme os protótipos, e as ações que falharem NÃO DEVEM levar o professor a outra tela como se tivessem dado certo.
- **FR-053**: Erros apresentados ao professor NÃO DEVEM expor detalhes internos do sistema.

**Operação e privacidade**

- **FR-054**: Os registros de operação NÃO DEVEM conter áudio, transcrição completa, CPF nem dados pessoais do aluno além do estritamente necessário para depuração.
- **FR-055**: O sistema DEVE registrar, para cada relatório, o canal de origem (Web ou Telegram), o tempo de processamento e o custo estimado das chamadas de IA, para medir tempo e custo por relatório.

### Key Entities *(include if feature involves data)*

- **Professor**: quem ministra as aulas. Nome, e-mail, situação (ativo/inativo), código de acesso com validade. Associado a vários alunos; pode ter no máximo um vínculo com o Telegram.
- **Aluno**: quem recebe as aulas. Nome, data de nascimento, CPF (cifrado, nunca exibido), nome do responsável (obrigatório para menores de 18 anos), instrumento (opcional). Pode ter vários professores.
- **Associação Professor–Aluno**: vínculo de responsabilidade pedagógica; base de todo o controle de acesso.
- **Sessão do Professor**: acesso ativo no portal, com expiração por inatividade e revogação ao encerrar.
- **Tentativa de Acesso**: contagem de códigos incorretos e bloqueio temporário por origem (portal) ou por conta do Telegram (bot); a origem é guardada apenas de forma não reversível.
- **Vínculo Telegram**: associação entre uma conta do Telegram e um professor. Guarda o estado da conversa: aluno selecionado e versão do último PDF enviado no chat.
- **Aula**: evento de ensino (professor, aluno, data no fuso da escola). Várias aulas no mesmo dia são permitidas; cada aula tem exatamente um relatório de aula.
- **Relatório de Aula**: transcrição, cinco seções estruturadas, perguntas pendentes e rodadas já feitas, estado, versão vigente, canal de origem, datas de criação, última alteração e aprovação.
- **Versão de Relatório**: fotografia imutável do conteúdo de um relatório (aula ou semestral) em uma versão, com seu PDF e a origem da mudança (estruturação inicial, resposta a perguntas, alteração pedida).
- **Relatório Semestral**: aluno, professor, período (chave e datas), quantidade de aulas consideradas, 10 seções estruturadas, estado, versão vigente, datas de criação, última alteração e aprovação.
- **Documento PDF**: arquivo de uma versão de relatório; oficial somente quando é o da versão aprovada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Para áudios de até 10 minutos, em 90% dos envios o relatório fica pronto para conferência (ou com perguntas pendentes) em até 2 minutos após o envio, em qualquer canal.
- **SC-002**: Um professor consegue, pelo celular, ir da tela "Entrar" até a aprovação de um relatório de aula sem perguntas pendentes em até 5 minutos, sem contar o tempo de gravação.
- **SC-003**: Ao fim de 4 semanas de uso, o tempo médio que o professor gasta por relatório de aula (da gravação à aprovação) é pelo menos 50% menor que o tempo que ele levava antes do sistema.
- **SC-004**: Pelo menos 80% dos relatórios de aula são aprovados com no máximo uma alteração solicitada.
- **SC-005**: 100% dos relatórios presentes no histórico foram aprovados explicitamente pelo professor, na versão vigente no momento da aprovação.
- **SC-006**: 100% das mensagens enviadas ao bot recebem resposta; confirmações e mensagens de erro chegam em até 5 segundos.
- **SC-007**: Nenhum áudio permanece armazenado após o processamento, e nenhum professor consegue ver, alterar ou baixar relatório ou PDF de aluno não associado a ele (verificado em teste de acesso cruzado).
- **SC-008**: Um relatório iniciado num canal pode ser concluído no outro em 100% dos casos testados.
- **SC-009**: O relatório semestral fica pronto para conferência em até 3 minutos para até 30 aulas aprovadas no período.
- **SC-010**: Todas as telas do fluxo passam em auditoria WCAG 2.1 AA, nos modos claro e escuro, em 390 px e em 1440 px de largura.
- **SC-011**: Em pesquisa com os professores após o primeiro mês, pelo menos 80% dizem confiar nos relatórios gerados e preferir o sistema ao processo anterior.

## Assumptions

- Esta é a primeira feature do sistema definitivo. Os cadastros e as telas da secretaria (login administrativo, cadastro de professor e aluno, associações, emissão de código, histórico de qualquer aluno) ficam para uma feature própria; até lá, os dados são carregados pelo procedimento operacional de carga (FR-008).
- O link "É da secretaria? Acesse a área administrativa." da tela de entrada só é exibido quando a área administrativa existir.
- O mesmo código de acesso serve para o portal e para vincular o Telegram, e vincular o Telegram não consome o código (resolve a RN-09 do handoff). Revogar sessões ao reemitir o código fica para a feature da secretaria.
- O histórico do professor mostra apenas relatórios de sua autoria, coerente com a decisão de que o semestral consolida apenas as aulas do próprio professor. A visão da escola com todos os professores fica para a área administrativa.
- A data da aula não é editável nesta feature (sempre hoje no fuso da escola). Edição manual direta dos campos também fica fora: ajustes são feitos por alteração em linguagem natural.
- O relatório aprovado não é enviado automaticamente às famílias nesta feature; o texto do diálogo de aprovação ("documento oficial entregue à família") descreve o uso previsto do PDF.
- O texto dos diálogos de descarte e cancelamento no protótipo ("será excluído", "serão descartados") descreve a perspectiva do professor: o relatório some para ele e não pode ser recuperado. Internamente o registro é mantido como cancelado (RN-24).
- O texto do protótipo "Seu áudio foi recebido e não foi perdido. Tente processar novamente" vale apenas para falhas depois da transcrição (a transcrição está salva). Quando a própria transcrição falha, o áudio não é retido e o professor grava ou envia de novo.
- O texto de apoio do protótipo sobre formatos e limite ("MP3, M4A, OGG ou WAV · até 25 MB") será ajustado para refletir os formatos e o limite configurado (FR-010). O limite de 50 MB acomoda os vídeos de teste do cliente.
- O instrumento do aluno (exibido nos protótipos como "Piano · última aula ...") é um dado opcional da carga inicial.
- Retenção e exclusão de transcrições, versões antigas e relatórios cancelados seguirão a política de LGPD a ser definida na feature de privacidade e operação; nesta feature nada é apagado automaticamente, exceto o áudio.
- Os prompts de IA serão revisados (acentuação, tom institucional) e versionados. Provedores de transcrição e de IA são selecionáveis por configuração, conforme a constituição.
- As gravações fictícias do cliente em `assets/` (.ogg e .mp4) serão usadas como casos de teste de ponta a ponta.
- O tema da interface segue a preferência escolhida pelo professor no menu; na primeira visita, segue a preferência do sistema operacional.
