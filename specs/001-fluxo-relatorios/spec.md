# Feature Specification: Fluxo de envio e criação de relatórios pedagógicos (Web e Telegram)

> **Nota de histórico (feature 003)**: este documento foi escrito antes da migração do código para
> inglês e cita nomes anteriores a ela (classes, arquivos, tabelas, rotas, variáveis). Ele permanece como
> histórico, sem reescrita. Para o nome atual de cada termo, consulte o glossário em
> [`.specify/memory/glossary.json`](../../.specify/memory/glossary.json) e o Princípio VI da constituição.

**Feature Branch**: `001-fluxo-relatorios`

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "implementar agora todo o fluxo de envio e de criação de relatórios do sistema, use os materiais de apoio HANDOFF_MVP_Relatorios_Escola_Musica.md e o mvp anterior. Use o projeto do Claude Design (Telas.dc.html, assets/fb-monograma-preto.png, support.js) e use também o portal-do-professor-design-system como material para o design."

**Revisão (2026-09-30)**: spec alinhada à constituição v1.3.0 (Princípio I, paridade funcional). Web e Telegram são canais equivalentes: tudo o que o professor faz em um canal nesta feature, ele também faz no outro, com as mesmas regras e o mesmo resultado.

**Revisão (2026-09-30, alteração por áudio)**: Input: "o professor deve ter a possibilidade de enviar o que ele quer que seja revisado nos relatórios também via áudio. Então, quando a IA envia o relatório para ele revisar e aprovar, ele deve ser capaz de enviar o que ele quer que altere usando áudio." Na conferência, o pedido de alteração pode ser feito por texto ou por áudio, nos dois canais e para os dois tipos de relatório (FR-027a, FR-027b). Por clarificação, as respostas às perguntas pendentes também podem ser enviadas por áudio (FR-024a).

**Materiais de referência**:

- `HANDOFF_MVP_Relatorios_Escola_Musica.md`: regras de negócio (RN-xx), fluxos, textos e lições aprendidas do MVP.
- `portal-do-professor-design-system/project/`: protótipos navegáveis (`Telas.dc.html`, `Portal do Professor.dc.html`, `Modelo PDF.dc.html`, `Design System.dc.html`) e ativos da marca. O comportamento da Web segue as telas 01 a 12 do Portal do Professor; os PDFs seguem o Modelo do PDF.
- MVP `mvp-relatorios` (somente leitura), como referência de comportamento.

**Decisões de escopo confirmadas pelo dono do produto (2026-09-30)**:

- **Web e Telegram** são canais de entrada e saída **equivalentes**: acesso, registro de aula, perguntas, conferência, alteração, aprovação, cancelamento, retomada, relatório semestral e histórico ficam disponíveis nos dois.
- O **acesso do professor** e o **controle de acesso por aluno** fazem parte desta feature. Cadastros e funções da secretaria ficam para uma feature própria, também com paridade entre os canais. Até lá, professores, alunos e associações entram por carga inicial.

  > **Nota (feature 005)**: a "secretaria" deste trecho foi substituída pelo papel **administrador**, somente na Web, e o professor passou a cadastrar os próprios alunos na Web e no Telegram. Ver [`005-admin-registrations-access/spec.md`](../005-admin-registrations-access/spec.md).

- O relatório semestral consolida **somente as aulas aprovadas do professor que gera o relatório**.

## Clarifications

### Session 2026-09-30

- Q: Qual deve ser o limite de tamanho do arquivo de áudio/vídeo, igual nos dois canais, considerando que o Telegram só deixa um bot receber arquivos de até 20 MB? → A: 50 MB nos dois canais; o plano inclui o servidor próprio da API de bots do Telegram para aceitar arquivos acima de 20 MB.
- Q: No Telegram, como o sistema deve saber de qual aluno é o áudio enviado, para evitar que o relatório fique no aluno errado? → A: Todo áudio recebido no Telegram pede que o professor confirme o aluno, com um botão, antes de ser processado, mesmo que já haja um aluno selecionado.
- Q: Usar o sistema normalmente, com a sessão da Web aberta ou o Telegram já vinculado, deve renovar a validade de 7 dias do código de acesso? → A: Sim; qualquer uso com sessão ou vínculo ativos, em qualquer canal, renova a validade por mais 7 dias, e o código só vence após 7 dias sem nenhum uso.
- Q: Quando um relatório termina de processar ou sai uma nova versão, o sistema deve avisar o professor pelo Telegram mesmo quando ele iniciou essa etapa pela Web? → A: Sim; o resultado chega no canal em que a etapa foi pedida e, se ela foi pedida pela Web e o professor tem Telegram vinculado, o bot também envia um aviso curto com a opção de continuar no chat.
- Q: Ao gerar o relatório semestral, o que o sistema deve fazer se ainda houver relatórios de aula daquele período em aberto, isto é, não aprovados? → A: Avisar antes de gerar ("Há N relatórios de aula em aberto neste período; eles não serão considerados") e oferecer "Gerar mesmo assim" ou "Voltar"; se o professor seguir, a conferência repete o aviso.
- Q: Quando a IA faz perguntas porque faltou informação no áudio da aula, o professor também deve poder respondê-las por áudio, e não só digitando? → A: Sim; as perguntas podem ser respondidas por texto ou por áudio, nos dois canais, com as mesmas regras de formato, limite e falha da alteração por áudio.
- Q: Depois que o áudio do professor vira texto (pedido de alteração ou resposta às perguntas), o sistema deve mostrar esse texto para ele confirmar antes de aplicar, ou deve aplicar direto? → A: Aplicar direto, sem etapa de confirmação da transcrição, e mostrar o texto entendido junto com a nova versão ou o relatório atualizado.
- Q: No Telegram, quando o professor manda um áudio enquanto há um relatório esperando respostas ou conferência, o bot deve perguntar por botão se o áudio é para esse relatório ou para uma aula nova, ou deve assumir direto que é para o relatório em andamento? → A: Assumir direto que o áudio é para o relatório em andamento (resposta ou alteração, conforme a etapa); para registrar outra aula, o professor usa antes o comando ou botão "Registrar aula".

## Paridade entre canais

Cada funcionalidade executa as mesmas regras nos dois canais; só muda a forma de interação.

| Funcionalidade | Portal Web | Telegram |
|---|---|---|
| Acessar | Tela "Entrar" com o código de acesso | Enviar o código de acesso ao bot (vinculação da conta) |
| Encerrar acesso | "Encerrar sessão" no menu | Comando "sair" (desfaz o vínculo; novo acesso exige o código) |
| Início | Painel: saudação, relatórios em aberto, alunos | Comando "início"/"menu": saudação, relatórios em aberto, alunos e ações |
| Encontrar aluno | Lista com busca por nome | Nome digitado (busca aproximada) ou lista de opções |
| Enviar áudio | Gravação no navegador ou arquivo, na tela do aluno escolhido | Mensagem de voz ou arquivo de áudio/vídeo + confirmação do aluno por botão |
| Acompanhar processamento | Tela com etapas (e aviso pelo bot, se houver Telegram vinculado) | Mensagens de etapa no chat |
| Responder perguntas | Lista numerada + campo de resposta com texto, gravação no navegador ou arquivo de áudio | Mensagem numerada + resposta em texto, mensagem de voz ou arquivo de áudio |
| Conferir | Texto das seções + PDF | Texto das seções no chat + PDF enviado como documento |
| Pedir alteração | Campo "Precisa ajustar algo?" com texto, gravação no navegador ou arquivo de áudio | Texto livre, mensagem de voz ou arquivo de áudio ou botão "Solicitar alteração" |
| Aprovar | Botão + diálogo de confirmação com a versão | Botão ou "aprovar" + pedido de confirmação com a versão |
| Cancelar / descartar | Botão + diálogo de confirmação | Botão ou "cancelar" + pedido de confirmação |
| Retomar relatório em aberto | Tela "Você tem um relatório em aberto" | Mensagem com o mesmo aviso e as opções "continuar"/"descartar" |
| Relatório semestral | Tela de período + conferência | Escolha de aluno e semestre por lista + conferência no chat |
| Histórico | Lista de relatórios aprovados + "Abrir PDF" | Lista de relatórios aprovados + envio do PDF escolhido |

Preferências de apresentação próprias de um meio, como o modo claro/escuro do portal, não são funcionalidades de negócio e não precisam de equivalente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar uma aula por áudio até a aprovação, em qualquer canal (Priority: P1)

A professora Clara termina a aula de Matteo e registra o relatório pelo canal que tiver à mão. No portal, ela escolhe o aluno, grava o áudio no navegador (ou envia um arquivo) e acompanha as etapas na tela. No Telegram, ela envia o nome do aluno, manda uma mensagem de voz (ou um arquivo) e recebe as etapas no chat. Nos dois casos, se faltar informação, ela recebe todas as perguntas de uma vez e responde numa única mensagem, digitada ou gravada. Depois confere o relatório em texto e em PDF, pede um ajuste com suas palavras, digitando ou gravando um áudio curto, confere a nova versão e aprova. O relatório vira oficial e entra no histórico do aluno.

**Why this priority**: é a proposta de valor central ("o professor manda um áudio e recebe um relatório pronto para revisar") e o caminho mais usado. Sozinha, esta história já entrega um produto utilizável nos dois canais.

**Independent Test**: com um professor e um aluno associado na carga inicial, executar o fluxo completo duas vezes, uma em cada canal, com as gravações fictícias de `assets/`: enviar o áudio, responder às perguntas (se houver), pedir uma alteração por texto e outra por áudio e aprovar. Verificar que os dois relatórios aprovados têm a versão aprovada, o PDF oficial e aparecem no histórico, e que as mesmas entradas produzem os mesmos estados nos dois canais.

**Acceptance Scenarios**:

1. **Given** um professor acessado em qualquer canal, **When** ele escolhe registrar aula para um aluno associado sem relatório em aberto, **Then** o canal indica o aluno e a data da aula (hoje, no fuso da escola) e pede o áudio.
2. **Given** a Web, **When** o professor grava pelo navegador, **Then** pode ouvir, regravar ou enviar; o envio só fica disponível com gravação concluída ou arquivo selecionado. Sem suporte a gravação ou com o microfone bloqueado, a tela avisa "Gravação indisponível neste navegador..." e aceita apenas arquivo.
3. **Given** o Telegram, **When** o professor envia uma mensagem de voz ou um arquivo de áudio/vídeo, **Then** o bot confirma o recebimento e pede a confirmação do aluno antes de processar: com aluno selecionado, oferece os botões "Confirmar {aluno}" e "Trocar aluno"; sem aluno selecionado, mostra a lista de alunos. O processamento só começa depois que o professor confirma ou escolhe o aluno.
4. **Given** um áudio enviado em qualquer canal, **When** o processamento avança, **Then** o professor acompanha as etapas "Enviando o áudio", "Transcrevendo", "Estruturando o relatório" e "Pronto para conferência", sem ação manual.
5. **Given** uma estruturação que identificou informações faltantes, **When** o processamento termina, **Then** o professor recebe "Faltam algumas informações", com todas as perguntas numeradas, e pode responder a todas numa única mensagem, por texto ou por áudio (na Web, gravado no navegador ou por arquivo; no Telegram, por voz ou arquivo,).
6. **Given** a resposta às perguntas enviada, **When** o sistema a incorpora, **Then** os campos correspondentes do relatório são atualizados e o relatório segue para conferência.
7. **Given** um relatório em conferência, **When** o professor o abre em qualquer canal, **Then** recebe a versão vigente, o cabeçalho (aluno, professor, data da aula), o texto das cinco seções e o PDF da mesma versão (na Web: abas "Texto"/"Ver PDF" no celular, lado a lado no computador; no Telegram: texto no chat e PDF como documento).
8. **Given** um relatório em conferência na versão N, **When** o professor descreve uma alteração, **Then** é avisado de que a alteração está sendo aplicada e em seguida recebe a versão N+1, com novo PDF e o aviso "Alteração aplicada. Confira a versão N+1."
9. **Given** um relatório em conferência na versão N na Web, **When** o professor grava o pedido de alteração no navegador (ou envia um arquivo de áudio) no campo "Precisa ajustar algo?", **Then** pode ouvir, regravar ou enviar; enviado o áudio, acompanha as etapas "Transcrevendo o pedido" e "Aplicando a alteração" e recebe a versão N+1, com novo PDF, o aviso "Alteração aplicada. Confira a versão N+1." e o texto do pedido como foi entendido. Sem suporte a gravação ou com o microfone bloqueado, o campo aceita texto ou arquivo.
10. **Given** um relatório em conferência na versão N no Telegram, **When** o professor envia uma mensagem de voz ou um arquivo de áudio, **Then** o bot confirma o recebimento e trata o áudio diretamente como pedido de alteração, sem pedir confirmação, com o mesmo resultado do cenário anterior. Para registrar uma aula nova nesse momento, o professor escolhe antes "Registrar aula"; o relatório em conferência continua em aberto e o áudio seguinte segue o fluxo do cenário 3.
11. **Given** um relatório em conferência na versão N, **When** o professor pede para aprovar, **Then** o canal pede confirmação citando a versão N e informando que o relatório entrará no histórico e não poderá mais ser alterado; confirmado, o relatório é aprovado e o professor recebe "Relatório aprovado", com data e hora, e as opções de registrar outra aula ou ver o histórico.
12. **Given** um relatório em aberto, **When** o professor pede para cancelar e confirma, **Then** o relatório é cancelado, deixa de aparecer para ele e o canal volta ao início ("Relatório cancelado.").

---

### User Story 2 - Acessar o sistema e se orientar em qualquer canal (Priority: P1)

O professor recebe da secretaria um código de acesso. Com ele, entra no portal e vincula a conta do Telegram. Em qualquer canal, vê uma saudação, os relatórios em aberto e os seus alunos, e escolhe o que fazer: registrar aula, gerar semestral ou consultar o histórico. Quando quiser, encerra o acesso.

> **Nota (feature 005)**: quem entrega o código ao professor passou a ser o **administrador** (e não a "secretaria"), que o emite e reemite pela área administrativa; reemitir o código ou inativar o professor encerra o acesso na hora. A entrada com código vencido, inexistente ou substituído tem mensagem única (`INVALID_CODE`; o `EXPIRED_CODE` deixou de existir). Ver [`005-admin-registrations-access/spec.md`](../005-admin-registrations-access/spec.md) e [`contracts/messages.md`](../005-admin-registrations-access/contracts/messages.md).

**Why this priority**: sem acesso não há registro; a tela ou mensagem inicial é o ponto de partida de todas as outras histórias nos dois canais.

**Independent Test**: com um professor na carga inicial, entrar no portal com o código, vincular uma conta de teste do Telegram com o mesmo código, abrir o início nos dois canais e comparar as informações; errar o código 5 vezes em cada canal e verificar o bloqueio; encerrar o acesso nos dois canais.

**Acceptance Scenarios**:

1. **Given** um professor ativo com código válido, **When** ele informa o código na tela "Entrar" da Web, **Then** vê o Painel com "Olá, {nome}", os relatórios em aberto, o total de alunos associados e os primeiros alunos.
2. **Given** uma conta do Telegram não vinculada, **When** o usuário envia o código válido de um professor ativo que ainda não tem Telegram vinculado, **Then** a conta é vinculada e o bot responde "Conta vinculada com sucesso, {nome}!" seguido das mesmas informações do Painel e das ações disponíveis.
3. **Given** uma conta do Telegram não vinculada, **When** o usuário envia qualquer mensagem que não seja um código válido, **Then** o bot pede o código de acesso fornecido pela secretaria.

   > **Nota (feature 005)**: o bot pede o código de acesso recebido do **administrador da escola** (texto `CODE_REQUIRED` atualizado). Ver [`contracts/messages.md`](../005-admin-registrations-access/contracts/messages.md).

4. **Given** um professor vinculado no Telegram, **When** ele envia "início" ou "menu", **Then** recebe as mesmas informações do Painel da Web e as ações "Registrar aula", "Relatório semestral" e "Histórico".
5. **Given** um texto que corresponde a exatamente um aluno associado (ignorando maiúsculas, minúsculas e acentos, aceitando parte do nome), **When** o professor o envia no Telegram ou o digita na busca da Web, **Then** o aluno é encontrado; com várias correspondências, as opções são listadas; sem nenhuma, o canal informa `Nenhum aluno encontrado para "{busca}".` e mostra os alunos do professor.
6. **Given** 5 códigos incorretos seguidos de uma mesma origem (Web) ou conta (Telegram), **When** há nova tentativa, mesmo com o código correto, **Then** o canal recusa com "Acesso bloqueado temporariamente..." e o tempo restante.
7. **Given** um professor acessado, **When** ele escolhe "Encerrar sessão" na Web ou envia "sair" no Telegram, **Then** o acesso daquele canal é encerrado e só é refeito com o código.

---

### User Story 3 - Continuar um relatório em aberto no outro canal (Priority: P2)

O professor começou um relatório pelo Telegram e não terminou. Mais tarde, no portal, ao escolher o mesmo aluno, recebe o aviso de que existe um relatório em aberto iniciado pelo Telegram, com a data da última alteração, e escolhe "Continuar de onde parei" ou "Descartar e começar um novo". O mesmo acontece no sentido inverso, com as mesmas opções no chat.

**Why this priority**: os dois canais compartilham identidade e ciclo de vida; sem a retomada, rascunhos ficariam presos no canal de origem e gerariam relatórios duplicados.

**Independent Test**: iniciar um relatório em um canal, parar antes da aprovação e concluí-lo no outro; repetir escolhendo "descartar" e verificar que o relatório original fica cancelado. Executar nos dois sentidos (Web → Telegram e Telegram → Web).

**Acceptance Scenarios**:

1. **Given** um relatório de aula em aberto para o aluno, iniciado em qualquer canal, **When** o professor escolhe registrar aula para esse aluno em qualquer canal, **Then** recebe "Você tem um relatório em aberto", com o canal de origem e a data e hora da última alteração, e as opções continuar ou descartar.
2. **Given** o aviso de relatório em aberto, **When** o professor escolhe continuar, **Then** retoma a etapa em que o relatório parou (processamento, perguntas ou conferência), com as mesmas informações nos dois canais.
3. **Given** o aviso de relatório em aberto, **When** o professor escolhe descartar e confirma, **Then** o relatório anterior é cancelado e o canal pede o novo áudio.
4. **Given** relatórios em aberto, **When** o professor abre o início em qualquer canal, **Then** vê cada relatório em aberto com o aluno e a data da última alteração, e pode retomá-lo dali; a lista de alunos marca os alunos com relatório "Em aberto".
5. **Given** um relatório alterado em um canal depois que o professor recebeu uma versão anterior no outro, **When** ele tenta aprovar a versão antiga, **Then** a aprovação é recusada com o aviso "Esta versão está desatualizada...", e o canal apresenta a versão vigente para nova conferência.

---

### User Story 4 - Gerar e aprovar o relatório semestral, em qualquer canal (Priority: P2)

Ao fim do semestre, o professor escolhe "Relatório semestral" para um aluno, no portal ou no Telegram, escolhe o período numa lista (o mais recente vem pré-selecionado ou em destaque) e gera o relatório. O sistema consolida as aulas aprovadas por ele naquele semestre em 10 seções, informa quantas aulas foram consideradas e segue o mesmo ciclo de conferência, alteração e aprovação.

**Why this priority**: resolve a segunda dor do cliente (o relatório consolidado a cada seis meses), mas depende de haver aulas aprovadas, produzidas pela história 1.

**Independent Test**: com aulas aprovadas de um aluno num semestre, gerar o semestral pela Web e, para outro aluno, pelo Telegram; verificar a contagem, pedir uma alteração, aprovar e conferir o histórico. Repetir num semestre sem aulas aprovadas e verificar que nada é criado, nos dois canais.

**Acceptance Scenarios**:

1. **Given** um aluno associado, **When** o professor escolhe "Relatório semestral" em qualquer canal, **Then** recebe os semestres disponíveis (1º semestre = 01/01 a 30/06; 2º = 01/07 a 31/12), com o mais recente pré-selecionado e o intervalo de datas de cada período.
2. **Given** um semestre com N ≥ 1 aulas aprovadas do professor para o aluno, **When** ele pede para gerar, **Then** acompanha a consolidação e recebe a conferência com "N relatórios de aula foram considerados nesta consolidação", as 10 seções em texto e o PDF.
3. **Given** um semestre sem aulas aprovadas do professor para o aluno, **When** ele pede para gerar, **Then** recebe "Nenhum relatório de aula aprovado neste período. Registre as aulas do semestre antes de gerar o relatório." e nada é criado.
4. **Given** um semestral em conferência, **When** o professor pede alteração, aprova ou cancela, **Then** valem as mesmas regras do relatório de aula, nos dois canais.
5. **Given** um semestral já aprovado para o mesmo aluno, professor e período, **When** o professor pede para gerar outro, **Then** o sistema recusa e informa que o relatório daquele semestre já foi aprovado.
6. **Given** um semestral em aberto para o mesmo aluno, professor e período, iniciado em qualquer canal, **When** o professor pede para gerar outro, **Then** o sistema oferece continuar o existente ou descartá-lo e gerar um novo.
7. **Given** um semestre com relatórios de aula do professor para o aluno ainda em aberto, **When** ele pede para gerar o semestral em qualquer canal, **Then** recebe, antes da consolidação, o aviso "Há N relatórios de aula em aberto neste período; eles não serão considerados." com as opções "Gerar mesmo assim" e "Voltar"; se escolher gerar, a conferência do semestral repete o aviso.

---

### User Story 5 - Consultar o histórico e receber os PDFs oficiais, em qualquer canal (Priority: P3)

O professor consulta o histórico de um aluno, no portal ou no Telegram, vê os relatórios aprovados (aula e semestral), do mais recente ao mais antigo, com a data de aprovação, e abre (Web) ou recebe no chat (Telegram) o PDF oficial de qualquer um deles.

**Why this priority**: é a saída consolidada do fluxo e dá confiança ao professor, mas não bloqueia o registro nem a aprovação.

**Independent Test**: com relatórios aprovados, consultar o histórico do mesmo aluno nos dois canais e verificar que a lista é idêntica (ordem, rótulos e datas); obter um PDF em cada canal; tentar obter o PDF de relatório de outro professor e verificar que o acesso é negado.

**Acceptance Scenarios**:

1. **Given** um aluno com relatórios aprovados do professor, **When** ele pede o histórico em qualquer canal, **Then** recebe a lista do mais recente ao mais antigo, com "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)" e "Aprovado em dd/mm/aaaa às hh:mm".
2. **Given** um item do histórico, **When** o professor o escolhe, **Then** recebe o PDF oficial da versão aprovada: na Web, abre (ou é baixado, se o navegador impedir a abertura); no Telegram, chega como documento no chat.
3. **Given** um aluno sem relatórios aprovados do professor, **When** ele pede o histórico, **Then** recebe "Este aluno ainda não tem relatórios aprovados." e a opção de registrar aula.

---

### Edge Cases

- **Áudio sem fala, vazio, corrompido ou em formato não suportado**: nada é criado; o professor é informado do problema e convidado a gravar ou enviar de novo, em qualquer canal.
- **Áudio acima do limite de tamanho**: recusado antes do processamento, com a mensagem "O áudio enviado é maior que o limite permitido. Grave um trecho mais curto." O limite é o mesmo nos dois canais.
- **Falha na estruturação depois de a transcrição dar certo**: o relatório fica em "falha no processamento", com a transcrição guardada; o professor pode "Tentar novamente" sem reenviar o áudio, ou cancelar, em qualquer canal.
- **Resposta inválida da IA** (formato inesperado ou campos ausentes): o sistema tenta corrigir uma vez; se não conseguir, trata como falha na estruturação (item anterior). Campos que a IA não conseguiu preencher ficam vazios, nunca inventados.
- **Perguntas que continuam depois da resposta**: no máximo 2 rodadas de perguntas por relatório; depois disso o relatório segue para conferência com os campos faltantes vazios, e o professor pode completá-los por alteração.
- **Novo áudio com relatório em aberto para o mesmo aluno** (qualquer canal): o sistema não inicia outro relatório; aplica o aviso de relatório em aberto (continuar ou descartar).
- **Nome de aluno enviado no Telegram durante a conferência**: se o texto corresponde exatamente a outro aluno associado, o bot pergunta se o professor quer trocar de aluno (mantendo o relatório atual em aberto) em vez de tratar o texto como alteração.
- **Aprovação dupla ou concorrente** (dois canais ao mesmo tempo): somente a primeira aprovação da versão vigente vale; a outra recebe a informação de que o relatório já foi aprovado.
- **Cancelar ou alterar relatório já aprovado ou cancelado**: recusado com mensagem clara; o relatório aprovado não muda.
- **Aluno não associado ao professor** (endereço manipulado na Web, nome digitado no Telegram): acesso negado; o sistema não revela dados do aluno.
- **Relatório de outro professor**: tratado como inexistente para quem não é o dono, em qualquer canal.
- **Sessão da Web expirada no meio do fluxo**: o professor é levado imediatamente à tela "Entrar"; o relatório em aberto continua salvo e pode ser retomado em qualquer canal.
- **Conexão perdida durante o processamento**: o processamento continua no servidor; ao voltar, o professor encontra o relatório no ponto em que parou (via retomada), em qualquer canal.
- **Processamento que excede o tempo esperado**: depois de 3 minutos sem conclusão, o canal avisa que está demorando mais que o normal e que o resultado será entregue quando pronto; na Web, o professor pode sair e retomar depois e, se tiver Telegram vinculado, recebe o aviso do resultado pelo bot (FR-021); no Telegram, o resultado chega no chat.
- **Áudio no Telegram sem confirmação do aluno**: enquanto aguarda a confirmação, o áudio fica guardado apenas temporariamente e nada é criado. Se o professor não confirmar em 30 minutos (configurável), cancelar ou iniciar outra ação, o áudio é descartado e o bot avisa que é preciso enviá-lo de novo. Se chegar um novo áudio antes da confirmação, ele substitui o anterior.
- **Mensagem de tipo não suportado no Telegram** (foto, figurinha, localização): o bot responde explicando o que pode receber naquele momento.
- **Conta do Telegram já vinculada a outro professor, ou professor já vinculado a outra conta do Telegram**: vinculação recusada com mensagem específica, orientando procurar a secretaria.
- **Código vencido após 7 dias sem uso**: o acesso com ele é recusado com mensagem própria, orientando pedir um novo código à secretaria (reemitido pela carga inicial nesta feature).
- **Professor sem alunos associados**: o início de qualquer canal mostra "Você ainda não tem alunos associados. Fale com a secretaria da escola."

  > **Nota (feature 005)**: nos três casos acima a orientação passa a ser procurar o **administrador**, e dois deles mudam: o código vencido **não tem mais mensagem própria** (`EXPIRED_CODE` removido; cai na mensagem única `INVALID_CODE`) e o professor sem alunos vê a orientação de cadastrar o primeiro em "Meus alunos" (`NO_STUDENTS`). Textos em [`contracts/messages.md`](../005-admin-registrations-access/contracts/messages.md) (`TELEGRAM_ALREADY_LINKED`, `TEACHER_ALREADY_LINKED`, `NO_STUDENTS`).

- **Aula registrada perto da meia-noite**: a data da aula é sempre a de hoje no fuso da escola, nunca a do servidor.
- **Semestre corrente em andamento**: pode ser gerado com as aulas aprovadas até o momento; a lista indica "em andamento".
- **Arquivo de vídeo (ex.: .mp4 gravado no celular)**: aceito nos dois canais, e apenas a trilha de áudio é usada.
- **Áudio de alteração ou de resposta às perguntas sem fala, vazio, corrompido, em formato não suportado ou acima do limite**: nada é aplicado; o relatório continua na mesma etapa ("aguardando conferência" na versão N ou "aguardando respostas", sem consumir rodada de perguntas) e o professor é convidado a gravar de novo ou a digitar, em qualquer canal.
- **Áudio de alteração que pede para aprovar ou cancelar** (ex.: "pode aprovar", "descarta esse"): o áudio nunca aprova nem cancela o relatório; o sistema responde lembrando que a aprovação e o cancelamento são feitos pelas ações próprias, com confirmação, e não cria nova versão se o pedido não tiver nenhuma alteração de conteúdo.
- **Áudio de alteração enviado sobre uma versão desatualizada** (o relatório mudou no outro canal): vale a mesma regra do pedido por texto (FR-033); o pedido não é aplicado sobre a versão antiga, e o professor recebe a versão vigente para conferir antes de pedir de novo.
- **Áudio de aula nova enviado no Telegram com relatório aguardando respostas ou em conferência no chat**: o áudio é tratado como resposta ou alteração do relatório em andamento (FR-027b). Para evitar o engano, as mensagens de perguntas e de conferência lembram que um áudio enviado ali será aplicado àquele relatório e oferecem o botão "Registrar aula". Se o engano acontecer, o professor vê o texto entendido junto com o resultado e pode corrigir com uma nova alteração ou cancelar o relatório.

## Requirements *(mandatory)*

### Functional Requirements

**Paridade entre canais**

- **FR-001**: Toda funcionalidade desta feature DEVE estar disponível na Web e no Telegram, com as mesmas regras de autorização, validação, estados e versionamento, e com o mesmo resultado de negócio. As formas de interação de cada canal seguem a tabela "Paridade entre canais".
- **FR-002**: Qualquer relatório (de aula ou semestral) iniciado em um canal DEVE poder ser consultado, continuado, alterado, aprovado ou cancelado no outro.
- **FR-003**: Mensagens ao professor (sucesso, avisos e erros) DEVEM ter o mesmo conteúdo nos dois canais, adaptado apenas ao formato do meio, em português com acentuação correta.

**Acesso do professor e controle de acesso**

- **FR-004**: O professor DEVE acessar a Web informando o seu código de acesso, e DEVE vincular a conta do Telegram enviando o mesmo código ao bot. Nos dois casos, o sistema DEVE validar que o código existe, não expirou e pertence a um professor ativo. O código tem 8 caracteres (letras maiúsculas e dígitos, sem caracteres ambíguos).
- **FR-005**: O código de acesso DEVE valer por 7 dias a partir da emissão. Cada acesso bem-sucedido com o código e cada uso do sistema com sessão da Web ou vínculo do Telegram ativos, em qualquer canal, DEVEM renovar a validade por mais 7 dias; o código só vence após 7 dias sem nenhum uso. O vencimento do código NÃO DEVE encerrar sessões nem vínculos ativos, apenas impedir novos acessos com ele. Nenhum canal DEVE consumir o código.
- **FR-006**: Uma conta do Telegram NÃO DEVE ser vinculada a mais de um professor, e um professor NÃO DEVE ter mais de uma conta do Telegram vinculada ao mesmo tempo.
- **FR-007**: Após 5 códigos incorretos consecutivos de uma mesma origem (Web) ou de uma mesma conta (Telegram), o sistema DEVE recusar novas tentativas por 15 minutos, inclusive com o código correto, informando o tempo restante. Um acesso bem-sucedido DEVE zerar a contagem, e o fim do bloqueio também. Limite e duração DEVEM ser configuráveis e iguais nos dois canais.
- **FR-008**: A sessão da Web DEVE expirar após 12 horas sem uso (configurável) e DEVE ser renovada a cada uso. O vínculo do Telegram DEVE durar até o professor encerrá-lo. "Encerrar sessão" (Web) e "sair" (Telegram) DEVEM encerrar o acesso no servidor.
- **FR-009**: O professor DEVE ver, gerar, conferir, alterar, aprovar e cancelar relatórios somente de alunos associados a ele. Aluno não associado DEVE resultar em acesso negado. Relatório de outro professor DEVE ser tratado como inexistente. A identidade do professor DEVE vir sempre da sessão (Web) ou do vínculo (Telegram), nunca de dados enviados pelo cliente.
- **FR-010**: O vínculo do Telegram DEVE apenas identificar o professor no canal; a autorização de cada ação DEVE ser decidida pelo sistema com as mesmas regras aplicadas à Web.
- **FR-011**: Professores, alunos (nome, data de nascimento, CPF, nome do responsável quando menor de idade, instrumento opcional) e associações professor–aluno DEVEM poder ser carregados por um procedimento operacional de carga inicial, com CPF armazenado cifrado e nunca exibido. O mesmo procedimento DEVE emitir e reemitir códigos de acesso até que a feature da secretaria exista.

  > **Nota (feature 005)**: a área administrativa (somente Web) agora cadastra professores, alunos e associações e emite e reemite códigos; a carga inicial deixa de ter regra própria e chama os mesmos casos de uso (FR-044 da 005), e o `issue-code` virou comando de emergência que também encerra as sessões Web do professor. O "acesso negado" do FR-009 para aluno não associado passou a `404 RESOURCE_NOT_FOUND`, igual ao de aluno inexistente (FR-050 da 005). Ver [`005-admin-registrations-access/spec.md`](../005-admin-registrations-access/spec.md) e [`contracts/initial-load.md`](../005-admin-registrations-access/contracts/initial-load.md).

**Início e navegação**

- **FR-012**: O início de cada canal (Painel na Web; "início"/"menu" no Telegram) DEVE mostrar a saudação com o nome do professor, os relatórios em aberto (aluno e data da última alteração), o total de alunos associados, os alunos e as ações "Registrar aula", "Relatório semestral" e "Histórico".
- **FR-013**: A busca de aluno DEVE procurar entre os alunos associados ao professor, ignorando maiúsculas, minúsculas e acentos, e aceitando parte do nome, nos dois canais. Com várias correspondências, DEVE listar as opções; sem nenhuma, DEVE informar e listar os alunos do professor.

**Registro de aula por áudio**

- **FR-014**: O professor DEVE poder registrar uma aula para um aluno associado enviando um áudio: na Web, gravado no navegador ou por arquivo; no Telegram, por mensagem de voz ou arquivo.
- **FR-015**: Os dois canais DEVEM aceitar os mesmos formatos (os comuns de celular e navegador, incluindo MP3, M4A, OGG/Opus, WAV e WebM, e vídeos MP4, dos quais se usa apenas o áudio) e o mesmo limite de tamanho, de 50 MB (configurável). O limite vigente DEVE ser informado ao professor. No Telegram, o recebimento de arquivos acima do limite padrão de 20 MB para bots DEVE ser viabilizado por um servidor próprio da API de bots do Telegram, de modo que o limite efetivo seja o mesmo nos dois canais.
- **FR-016**: A data da aula DEVE ser a data de hoje no fuso da escola (padrão: America/Sao_Paulo).
- **FR-017**: O áudio DEVE ser transcrito antes de qualquer registro de aula ser criado. Se a transcrição falhar (áudio vazio, sem fala, corrompido, formato não suportado, falha do provedor), nenhuma aula ou relatório DEVE ser criado e o professor DEVE ser informado do motivo.
- **FR-018**: O áudio NÃO DEVE ser retido após a transcrição; ficam apenas a transcrição e o relatório. A regra vale para todo áudio recebido, inclusive os pedidos de alteração por áudio (FR-027a).
- **FR-019**: A partir da transcrição, o sistema DEVE estruturar o relatório de aula em cinco seções (Conteúdos trabalhados, Evolução, Dificuldades, Atividades propostas, Observações) e gerar perguntas pendentes quando uma informação relevante não puder ser extraída com confiança.
- **FR-020**: A estruturação NÃO DEVE inventar informação ausente da transcrição ou das respostas do professor; o que faltar DEVE ficar vazio ou virar pergunta pendente.
- **FR-021**: O relatório DEVE ter um estado consultável (em processamento, falha no processamento, aguardando respostas, aguardando conferência, aprovado, cancelado). Os dois canais DEVEM informar o avanço por etapas e entregar o resultado sem ação manual do professor. O resultado de cada etapa (pronto para conferência, perguntas pendentes, nova versão após alteração ou falha no processamento, de relatório de aula ou semestral) DEVE ser entregue no canal em que a etapa foi pedida. Quando a etapa tiver sido pedida pela Web e o professor tiver Telegram vinculado, o bot DEVE também enviar um aviso curto (aluno, tipo de relatório e resultado, ex.: "O relatório de Matteo está pronto para conferência") com a opção de continuar no chat.
- **FR-022**: Se a estruturação falhar depois de a transcrição ter sido salva, o relatório DEVE ficar em "falha no processamento" e o professor DEVE poder tentar de novo sem reenviar o áudio, ou cancelar.
- **FR-023**: Os dados enviados a provedores de IA DEVEM se limitar ao necessário para a tarefa: transcrição, respostas e instruções do professor, conteúdo dos relatórios. Nome, CPF, data de nascimento e nome do responsável NÃO DEVEM ser enviados.

**Perguntas pendentes**

- **FR-024**: Com perguntas pendentes, o relatório DEVE ficar "aguardando respostas", sem PDF, e o canal DEVE apresentar todas as perguntas de uma vez, numeradas, aceitando uma única resposta (obrigatória), por texto ou por áudio (FR-024a).
- **FR-024a**: O professor DEVE poder enviar a resposta às perguntas pendentes por áudio nos dois canais: na Web, gravado no navegador ou por arquivo, no próprio espaço de resposta, podendo ouvir, regravar ou enviar; no Telegram, por mensagem de voz ou arquivo de áudio (FR-027b). Os formatos e o limite de tamanho DEVEM ser os mesmos do registro de aula (FR-015). O áudio DEVE ser transcrito, e a transcrição DEVE ser aplicada diretamente, sem etapa de confirmação do texto, exatamente como uma resposta por texto (FR-025). Se a transcrição falhar ou vier vazia, nada DEVE ser incorporado, o relatório DEVE permanecer "aguardando respostas", sem consumir rodada de perguntas, e o professor DEVE ser informado do motivo e convidado a gravar de novo ou digitar. Ao entregar o resultado, o canal DEVE mostrar o texto da resposta como foi entendido.
- **FR-025**: A resposta do professor DEVE ser incorporada pela IA aos campos adequados do relatório, e não apenas anexada às observações. Se ainda restarem lacunas, o sistema PODE fazer uma nova rodada de perguntas, até o limite de 2 rodadas por relatório; depois disso, o relatório DEVE seguir para conferência com as lacunas vazias.

**Conferência, alteração, aprovação e cancelamento** (relatório de aula e semestral)

- **FR-026**: Ao chegar a "aguardando conferência", o sistema DEVE gerar o PDF da versão vigente e entregar ao professor o texto das seções e o PDF, nos dois canais.
- **FR-027**: O professor DEVE poder pedir alterações em linguagem natural, por texto ou por áudio (FR-027a); o pedido não pode ser vazio. Cada alteração DEVE criar uma nova versão (inteiro a partir de 1, incrementado a cada alteração) com seu próprio PDF, sem sobrescrever o conteúdo nem o PDF das versões anteriores.
- **FR-027a**: Em qualquer relatório "aguardando conferência" (de aula ou semestral), o professor DEVE poder enviar o pedido de alteração por áudio nos dois canais: na Web, gravado no navegador ou por arquivo, no próprio espaço de pedido de alteração da conferência, podendo ouvir, regravar ou enviar; no Telegram, por mensagem de voz ou arquivo de áudio. Os formatos e o limite de tamanho DEVEM ser os mesmos do registro de aula (FR-015). O áudio DEVE ser transcrito, e a transcrição DEVE ser aplicada diretamente, sem etapa de confirmação do texto, exatamente como um pedido de alteração por texto, com as mesmas regras de versionamento, versão vigente e recusa (FR-027, FR-032, FR-033). Se a transcrição falhar ou vier vazia, nenhuma versão DEVE ser criada, o relatório DEVE permanecer na versão vigente e o professor DEVE ser informado do motivo e convidado a gravar de novo ou digitar o pedido. O canal DEVE informar as etapas ("Transcrevendo o pedido", "Aplicando a alteração") e, ao entregar a nova versão, DEVE mostrar o texto do pedido como foi entendido, para que o professor possa corrigir um erro de transcrição com uma nova alteração antes de aprovar. Um pedido por áudio NUNCA DEVE aprovar nem cancelar o relatório.
- **FR-027b**: No Telegram, um áudio recebido enquanto há um relatório "aguardando respostas" ou "aguardando conferência" apresentado no chat DEVE ser tratado diretamente, sem pedido de confirmação, como resposta às perguntas (FR-024a) ou como pedido de alteração (FR-027a), conforme a etapa. As mensagens de perguntas e de conferência DEVEM avisar que texto ou áudio enviado ali será aplicado àquele relatório e DEVEM oferecer "Registrar aula". Ao escolher "Registrar aula" (botão ou texto), o relatório atual DEVE continuar em aberto e o áudio seguinte DEVE seguir o fluxo de FR-040.
- **FR-028**: A aprovação DEVE referir-se à versão apresentada ao professor naquele canal e DEVE ser recusada se essa não for a versão vigente. Só relatórios "aguardando conferência" podem ser aprovados.
- **FR-029**: A aprovação DEVE exigir confirmação explícita nos dois canais, citando a versão e informando que o relatório entrará no histórico e não poderá mais ser alterado.
- **FR-030**: A aprovação DEVE ser irreversível: o relatório aprovado DEVE registrar data, hora, versão aprovada e canal da aprovação, entrar no histórico do aluno e ter um PDF oficial que indique quem aprovou, quando e qual versão.
- **FR-031**: O professor DEVE poder cancelar um relatório em aberto (falha no processamento, aguardando respostas ou aguardando conferência), com confirmação, nos dois canais. O relatório cancelado DEVE deixar de aparecer para o professor e não poder ser reaberto, mas o registro DEVE ser mantido internamente como cancelado para rastreabilidade. Cancelar um relatório aprovado ou já cancelado DEVE ser recusado.
- **FR-032**: Pedir alteração, responder perguntas ou tentar de novo DEVE ser recusado para relatórios aprovados ou cancelados.
- **FR-033**: Se a versão vigente mudar enquanto o professor confere uma versão anterior em qualquer canal, o canal DEVE avisar que a versão está desatualizada e apresentar a versão vigente antes de permitir aprovar ou alterar.

**Relatório em aberto e retomada**

- **FR-034**: DEVE existir no máximo um relatório de aula em aberto por professor e aluno. Ao iniciar um novo relatório para um aluno que já tem um em aberto, de qualquer canal, o sistema DEVE informar o canal de origem e a data e hora da última alteração, e oferecer continuar ou descartar (cancelar) e começar outro. A mesma regra vale para o semestral, por aluno, professor e período.
- **FR-035**: Os dois canais DEVEM destacar os relatórios em aberto do professor no início e permitir retomá-los dali; a lista de alunos DEVE marcar os alunos com relatório em aberto.

**Conversa no Telegram**

- **FR-036**: Cada mensagem recebida pelo bot DEVE ser interpretada a partir do estado persistido no sistema (vínculo, aluno selecionado, fluxo em andamento e etapa do relatório), e não da memória do canal. Reiniciar o componente do canal NÃO DEVE perder o estado da conversa.
- **FR-037**: No Telegram, as ações disponíveis em cada etapa DEVEM ser oferecidas por botões e também aceitas como texto ("aprovar", "cancelar", "continuar", "descartar", "registrar aula", "início", "sair"). Com um relatório aguardando respostas, um texto livre é resposta; aguardando conferência, é pedido de alteração. Um áudio recebido com relatório aguardando respostas ou em conferência segue FR-027b.
- **FR-038**: A seleção de aluno no Telegram DEVE ser limpa após aprovar ou cancelar o relatório.
- **FR-039**: O bot DEVE responder a toda mensagem recebida. Cada situação de erro (código inválido ou expirado, conflito de vínculo, bloqueio por tentativas, nenhum aluno selecionado, aluno não encontrado, aluno não associado, áudio inválido, áudio grande demais, falha de processamento, versão desatualizada, ação não permitida na etapa atual, tipo de mensagem não suportado) DEVE ter mensagem própria, dizendo o que fazer em seguida.
- **FR-040**: Ao receber um áudio (exceto com relatório aguardando respostas ou em conferência apresentado no chat, quando vale FR-027b), o bot DEVE confirmar o recebimento e DEVE pedir que o professor confirme o aluno por botão antes de iniciar o processamento, mesmo que já exista aluno selecionado ("Confirmar {aluno}" / "Trocar aluno"; sem aluno selecionado, a lista de alunos). Nenhuma transcrição, aula ou relatório DEVE ser iniciado antes dessa confirmação. O áudio aguardando confirmação DEVE ser descartado após 30 minutos (configurável), ao cancelar ou ao iniciar outra ação, e um novo áudio DEVE substituir o anterior.

**Relatório semestral**

- **FR-041**: O sistema DEVE oferecer, para cada aluno associado, a lista de semestres disponíveis (chave AAAA-S; rótulos "1º semestre AAAA"/"2º semestre AAAA"), do semestre da primeira aula registrada do aluno com o professor (ou do cadastro do aluno, se anterior) até o semestre corrente, com o mais recente pré-selecionado. Sem semestres disponíveis, DEVE informar "Nenhum semestre disponível para este aluno ainda."
- **FR-042**: Antes de consolidar, o sistema DEVE contar os relatórios de aula aprovados do professor para o aluno com data de aula dentro do período (datas inclusive). Com zero, nada DEVE ser criado, nenhuma chamada à IA DEVE ser feita e o professor DEVE ser informado. O sistema DEVE também contar os relatórios de aula do professor para o aluno no período que estejam em aberto (em processamento, falha no processamento, aguardando respostas ou aguardando conferência); havendo algum, DEVE avisar antes de consolidar que eles não serão considerados e exigir que o professor escolha "Gerar mesmo assim" ou "Voltar". A quantidade de relatórios em aberto no momento da geração DEVE ser registrada no relatório semestral e repetida como aviso na conferência.
- **FR-043**: A consolidação DEVE usar exclusivamente os relatórios de aula aprovados do professor no período e produzir as 10 seções do modelo da escola: Informações Gerais, Frequência e Estudo, Técnica, Musicalidade, Leitura e Memorização, Pontos de Atenção, Estratégias Pedagógicas, Acompanhamento Familiar, Planejamento do Próximo Semestre e Parecer Final.
- **FR-044**: Os dados de identificação de Informações Gerais (nome e idade do aluno, instrumento quando houver, professor, período) e a quantidade de aulas consideradas DEVEM ser preenchidos pelo próprio sistema a partir do cadastro e dos registros, e NÃO DEVEM ser enviados à IA nem gerados por ela. As datas das aulas consideradas PODEM ser enviadas à IA para apoiar a análise de frequência.
- **FR-045**: O relatório semestral DEVE seguir o mesmo ciclo do relatório de aula (FR-026 a FR-033): nasce "aguardando conferência", versão 1, com PDF.
- **FR-046**: DEVE existir no máximo um relatório semestral aprovado por aluno, professor e período; pedir para gerar outro nesse caso DEVE ser recusado.

**Histórico e documentos**

- **FR-047**: Os dois canais DEVEM oferecer o histórico de cada aluno associado, com os relatórios aprovados (aula e semestral) de autoria do professor, do mais recente ao mais antigo (aula pela data da aula; semestral pelo início do período), e a data e hora de aprovação. A lista DEVE ser a mesma nos dois canais.
- **FR-048**: A entrega de qualquer PDF (abertura ou download na Web, documento enviado no Telegram) DEVE exigir professor autenticado e autorizado para aquele relatório específico (o dono), e não apenas o conhecimento do nome ou do endereço do arquivo.
  > **Nota (feature 006)**: o PDF **oficial** da versão aprovada também é entregue ao **administrador**, somente na Web e somente pela rota administrativa de leitura, com sessão de administrador válida e registro de autoria. Ver [`specs/006-admin-report-history/`](../006-admin-report-history/spec.md); em divergência, vale o contrato da 006.
- **FR-049**: Os PDFs DEVEM seguir o Modelo do PDF do design: identidade do Instituto Musical Felipe Blaft, título ("Relatório de Aula"/"Relatório Semestral"), cabeçalho com aluno, professor e data da aula ou período, seções com rótulos legíveis e acentuação correta, "—" para seções vazias, rodapé institucional com numeração de páginas. O PDF semestral DEVE informar quantos relatórios de aula foram consolidados. PDFs de versões ainda não aprovadas DEVEM ser identificados como documento em conferência (não oficial). O PDF é o mesmo independentemente do canal.

**Interface e identidade visual**

- **FR-050**: As telas da Web DEVEM seguir os protótipos de `portal-do-professor-design-system` (telas 01 a 12 e variações de estado) e o design system do projeto: paleta marfim, preto e dourado, tipografia serifada nos títulos e sem serifa na interface, monograma FB.
- **FR-051**: As mensagens do bot DEVEM seguir o tom institucional e a terminologia do portal (mesmos nomes de ações, estados e seções), e o bot DEVE usar o monograma FB como imagem de perfil.
- **FR-052**: Todas as telas da Web DEVEM funcionar a partir de 390 px de largura (mobile-first) e em telas de computador, e DEVEM atender a WCAG 2.1 nível AA, nos modos claro e escuro. O portal DEVE oferecer modo claro e modo escuro, alternáveis pelo menu do professor.
- **FR-053**: Os estados de carregamento, lista vazia, busca sem resultado, falha e sucesso DEVEM ter tratamento próprio nos dois canais (na Web, conforme os protótipos), e ações que falharem NÃO DEVEM levar o professor adiante como se tivessem dado certo.
- **FR-054**: Erros apresentados ao professor NÃO DEVEM expor detalhes internos do sistema.

**Operação e privacidade**

- **FR-055**: Os registros de operação NÃO DEVEM conter áudio, transcrição completa, CPF nem dados pessoais do aluno além do estritamente necessário para depuração.
- **FR-056**: O sistema DEVE registrar, para cada relatório, o canal de origem, o canal e o meio (texto ou áudio) de cada alteração e de cada resposta a perguntas, o canal da aprovação, o tempo de processamento e o custo estimado das chamadas de IA, para medir tempo e custo por relatório e o uso de cada canal.

### Key Entities *(include if feature involves data)*

- **Professor**: quem ministra as aulas. Nome, e-mail, situação (ativo/inativo), código de acesso com validade. Associado a vários alunos; tem no máximo um vínculo com o Telegram por vez.
- **Aluno**: quem recebe as aulas. Nome, data de nascimento, CPF (cifrado, nunca exibido), nome do responsável (obrigatório para menores de 18 anos), instrumento (opcional). Pode ter vários professores.
- **Associação Professor–Aluno**: vínculo de responsabilidade pedagógica; base de todo o controle de acesso.
- **Sessão do Professor (Web)**: acesso ativo no portal, com expiração por inatividade e revogação ao encerrar.
- **Vínculo Telegram**: associação entre uma conta do Telegram e um professor, com o estado da conversa: aluno selecionado, fluxo em andamento (registro, semestral, histórico), áudio aguardando confirmação do aluno (temporário) e versão do último relatório apresentado no chat.
- **Tentativa de Acesso**: contagem de códigos incorretos e bloqueio temporário por origem (Web) ou por conta (Telegram); a origem é guardada apenas de forma não reversível.
- **Aula**: evento de ensino (professor, aluno, data no fuso da escola). Várias aulas no mesmo dia são permitidas; cada aula tem exatamente um relatório de aula.
- **Relatório de Aula**: transcrição, cinco seções estruturadas, perguntas pendentes e rodadas já feitas, estado, versão vigente, canal de origem, datas de criação, última alteração e aprovação.
- **Relatório Semestral**: aluno, professor, período (chave e datas), quantidade de aulas consideradas, quantidade de relatórios de aula em aberto no momento da geração, 10 seções estruturadas, estado, versão vigente, canal de origem, datas de criação, última alteração e aprovação.
- **Versão de Relatório**: fotografia imutável do conteúdo de um relatório (aula ou semestral) em uma versão, com seu PDF, a origem da mudança (estruturação inicial, resposta a perguntas, alteração pedida), o canal em que foi pedida e, no caso de alteração ou de resposta a perguntas, o texto enviado (digitado ou transcrito do áudio) e o meio (texto ou áudio).
- **Documento PDF**: arquivo de uma versão de relatório; oficial somente quando é o da versão aprovada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Para áudios de até 10 minutos, em 90% dos envios o relatório fica pronto para conferência (ou com perguntas pendentes) em até 2 minutos após o envio (no Telegram, após a confirmação do aluno), em cada um dos canais.
- **SC-002**: Um professor consegue, pelo celular, ir do acesso até a aprovação de um relatório de aula sem perguntas pendentes em até 5 minutos, sem contar o tempo de gravação, em cada um dos canais.
- **SC-003**: Ao fim de 4 semanas de uso, o tempo médio que o professor gasta por relatório de aula (da gravação à aprovação) é pelo menos 50% menor que o tempo que ele levava antes do sistema.
- **SC-004**: Pelo menos 80% dos relatórios de aula são aprovados com no máximo uma alteração solicitada.
- **SC-005**: 100% dos relatórios presentes no histórico foram aprovados explicitamente pelo professor, na versão vigente no momento da aprovação.
- **SC-006**: 100% das funcionalidades desta feature podem ser executadas do começo ao fim em cada canal, e o mesmo roteiro de teste produz os mesmos estados, versões e histórico nos dois.
- **SC-007**: 100% das mensagens enviadas ao bot recebem resposta; confirmações e mensagens de erro chegam em até 5 segundos.
- **SC-008**: Nenhum áudio permanece armazenado após o processamento, e nenhum professor consegue ver, alterar ou obter relatório ou PDF de aluno não associado a ele, em nenhum dos canais (verificado em teste de acesso cruzado).
- **SC-009**: Um relatório iniciado num canal pode ser concluído no outro em 100% dos casos testados, nos dois sentidos.
- **SC-010**: O relatório semestral fica pronto para conferência em até 3 minutos para até 30 aulas aprovadas no período, em cada um dos canais.
- **SC-011**: Todas as telas da Web passam em auditoria WCAG 2.1 AA, nos modos claro e escuro, em 390 px e em 1440 px de largura.
- **SC-012**: Em pesquisa com os professores após o primeiro mês, pelo menos 80% dizem confiar nos relatórios gerados e preferir o sistema ao processo anterior.
- **SC-013**: Para pedidos de alteração e respostas às perguntas por áudio de até 2 minutos, em 90% dos casos o relatório atualizado fica pronto para conferência (ou com a nova rodada de perguntas) em até 90 segundos após o envio, em cada um dos canais, e em pelo menos 90% dos casos testados o texto mostrado ao professor corresponde ao que ele disse.

## Assumptions

- Esta é a primeira feature do sistema definitivo. As funções da secretaria (cadastros, associações, emissão de código, histórico de qualquer aluno) ficam para uma feature própria, que também deverá ter paridade entre Web e Telegram. Até lá, os dados são carregados pelo procedimento operacional de carga (FR-011). O link "É da secretaria? Acesse a área administrativa." da tela de entrada só é exibido quando essa área existir.
- A forma de manter o acesso é própria de cada meio: sessão com expiração por inatividade na Web e vínculo permanente até "sair" no Telegram. As duas seguem as mesmas regras de código, bloqueio e autorização.
- O mesmo código de acesso serve para a Web e para o Telegram, e nenhum dos canais o consome (resolve a RN-09 do handoff). Revogar acessos ao reemitir o código fica para a feature da secretaria.
- O histórico do professor mostra apenas relatórios de sua autoria, coerente com a decisão de que o semestral consolida apenas as aulas do próprio professor. A visão da escola com todos os professores fica para a secretaria.

  > **Nota (feature 005)**: a feature da "secretaria" foi entregue como a **área administrativa** (papel administrador, somente Web): cadastros, associações e emissão e reemissão de código. O link da tela de entrada passou a ser "É administrador? Acesse a área administrativa." e **é exibido agora** (FR-043 da 005), levando a `/admin/sign-in`. Reemitir o código, inativar o professor ou retirar a associação **revoga o acesso na hora** (o que este item adiava). Relatórios não fazem parte da área administrativa. Ver [`005-admin-registrations-access/spec.md`](../005-admin-registrations-access/spec.md).
- A data da aula não é editável nesta feature (sempre hoje, no fuso da escola). Edição manual direta dos campos também fica fora: ajustes são feitos por alteração em linguagem natural.
- O relatório aprovado não é enviado automaticamente às famílias nesta feature; o texto do diálogo de aprovação ("documento oficial entregue à família") descreve o uso previsto do PDF.
- O texto dos diálogos de descarte e cancelamento no protótipo ("será excluído", "serão descartados") descreve a perspectiva do professor: o relatório some para ele e não pode ser recuperado. Internamente o registro é mantido como cancelado (RN-24).
- O texto do protótipo "Seu áudio foi recebido e não foi perdido. Tente processar novamente" vale apenas para falhas depois da transcrição (a transcrição está salva). Quando a própria transcrição falha, o áudio não é retido e o professor grava ou envia de novo.
- O texto de apoio do protótipo sobre formatos e limite ("MP3, M4A, OGG ou WAV · até 25 MB") será ajustado para refletir os formatos e o limite configurado (FR-015). O limite de 50 MB acomoda os vídeos de teste do cliente. A configuração padrão do Telegram limita o recebimento de arquivos por bots a 20 MB; por decisão do dono do produto, essa diferença é eliminada com um servidor próprio da API de bots do Telegram, rodando em contêiner como os demais serviços (FR-015).
- Não há protótipo visual das conversas do Telegram. Os textos do bot serão derivados dos textos do portal e dos textos do MVP (seção 11.2 do handoff), revisados quanto a acentuação e tom.
- O instrumento do aluno (exibido nos protótipos como "Piano · última aula ...") é um dado opcional da carga inicial.
- Retenção e exclusão de transcrições, versões antigas e relatórios cancelados seguirão a política de LGPD a ser definida na feature de privacidade e operação; nesta feature nada é apagado automaticamente, exceto o áudio.
- Os prompts de IA serão revisados (acentuação, tom institucional) e versionados. Provedores de transcrição e de IA são selecionáveis por configuração, conforme a constituição.
- As gravações fictícias do cliente em `assets/` (.ogg e .mp4) serão usadas como casos de teste de ponta a ponta nos dois canais.
- O áudio vale para o registro da aula, para as respostas às perguntas pendentes e para os pedidos de alteração na conferência. Em todos os casos, o texto digitado continua disponível como alternativa.
- Os protótipos das telas de perguntas e de conferência mostram os campos de resposta e de "Precisa ajustar algo?" só com texto; a gravação da resposta e do pedido de alteração reaproveita o componente de gravação da tela de envio de áudio (ouvir, regravar, enviar) e será incorporada aos protótipos.
- O tema da interface Web segue a preferência escolhida pelo professor no menu; na primeira visita, segue a preferência do sistema operacional.
