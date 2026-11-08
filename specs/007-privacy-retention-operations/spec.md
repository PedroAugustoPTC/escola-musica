# Feature Specification: Privacidade, retenção e operação (LGPD)

**Feature Branch**: `feat/007-privacy-retention-operations`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "Privacidade, retenção e operação do sistema, para operar com dados reais de alunos, inclusive menores de idade, conforme a LGPD e o Princípio IV da constituição. Contexto: o sistema já cifra o CPF, não retém áudio e restringe cada professor aos seus alunos, mas nada é apagado automaticamente (spec 001, Assumptions), não há trilha de auditoria, o consentimento do responsável não é registrado, as métricas de custo e tempo são limitadas e o backup é um comando manual documentado. Referências: HANDOFF 13.3, 14.1, 17.1 (item 8), 17.2 (itens 15 e 16), 17.3 (itens 8 a 10); spec 001 FR-055 e FR-056; decisões D6 a D9 e D11 desta etapa. Entregas: 1. Retenção: política configurável que remove ou anonimiza transcrições, PDFs de versões antigas e relatórios cancelados após prazos definidos pelo dono do produto (D6), com execução automática, registro do que foi tratado (sem conteúdo) e proteção contra apagar relatório aprovado vigente. 2. Eliminação a pedido: fluxo conduzido pelo administrador, nos dois canais, que registra o pedido, anonimiza o aluno e seus relatórios conforme D7 e registra o atendimento. 3. Consentimento: registro, por aluno menor, de quem consentiu, quando e qual versão do termo (D8); regra de bloqueio quando ausente. 4. Auditoria: trilha imutável de eventos (login e falha de login, cadastro e alteração, emissão de código, aprovação, cancelamento, consulta e download de PDF, eliminação), com ator, momento, tipo e identificador do registro, nunca o conteúdo; consultável pelo administrador; com prazo de guarda próprio. 5. Observabilidade: logs estruturados em português sem áudio, transcrição, CPF nem dados pessoais além do necessário; métricas de tempo e custo estimado por áudio, aula, aluno, relatório semestral e mês, por canal e por provedor (confirmar o que o FR-056 já cobre e completar o resto); alertas para falha de provedor de IA, fila de entrega do canal acumulada e erro de armazenamento. 6. Backup e restauração: rotina automática do banco e do armazenamento de PDFs, cifrada, enviada para fora da VPS, com retenção configurável e teste de restauração documentado e automatizado; as chaves de cifragem do CPF e do HMAC guardadas junto, fora do repositório. 7. Inventário de tratamento: documento versionado dizendo quais dados vão para quais provedores de IA, por que, onde ficam e sob quais termos. Paridade: as ações do administrador (eliminação, consulta de auditoria) existem na Web e no Telegram. Fora de escopo: parecer jurídico; mudança de provedor de IA; evoluções de produto; anonimização de logs antigos fora da política definida. Critérios mensuráveis: zero dado de aluno em logs, métricas e trilha (verificado por teste automatizado); 100% dos eventos listados na trilha; restauração testada de ponta a ponta dentro do tempo-alvo definido no clarify; eliminação a pedido concluída sem deixar dado pessoal recuperável fora do que a lei exigir manter."

**Materiais de referência**:

- Constituição v2.2.0: Princípio I (funções do Administrador **somente na Web**; paridade Web/Telegram para tudo o que o Professor faz), II (só o núcleo grava e decide), III (aprovado é oficial; versões rastreáveis), IV (minimização, áudio não retido, CPF cifrado, segredos fora do repositório, logs sem dado sensível), V (testes e cobertura), VI (idioma), VII (simplicidade) e as restrições de contêiner e de VPS genérica.
- `specs/001-fluxo-relatorios/spec.md`: FR-055 (logs sem dado sensível), FR-056 (canal, meio, tempo de processamento e custo estimado por relatório) e Assumptions ("nada é apagado automaticamente, exceto o áudio"). A spec 001 é **histórico** e não é reescrita.
- `specs/005-admin-registrations-access/spec.md`: FR-017 (dados do responsável; "a retenção definitiva é da feature 007"), FR-020 (CPF cifrado, resumo com chave, "as cópias de segurança NÃO DEVEM incluir a chave"), FR-039 e FR-040 (logs e registro de autoria), FR-049 (classes de mensagens de erro), a Matriz de permissões e a premissa "leituras sem rastro até a 007".
- `specs/006-admin-report-history/spec.md`: FR-020 a FR-024 (registro de autoria das consultas e PDFs do administrador; registro e entrega valem juntos).
- `HANDOFF_MVP_Relatorios_Escola_Musica.md`: 2.3 (métricas de custo), 13.3, 14.1, 17.1 (item 8), 17.2 (itens 15 e 16) e 17.3 (itens 8 a 10). `PLANO_EXECUCAO_SPECKIT.md`: seção 4.2 (D6 a D9 e D11) e seção 10. `infra/README.md`, seção 5 (backup manual atual).

**Escopo desta feature**: transformar a política de dados em comportamento verificável — retenção automática, eliminação a pedido, consentimento do responsável, trilha de auditoria completa e consultável, observabilidade de tempo e custo com alertas e inventário de tratamento — sem mudar o ciclo de vida dos relatórios nem os provedores de IA.

**Divergência com o pedido (canal do Administrador)**: o pedido diz que eliminação e consulta da auditoria existem "na Web e no Telegram". A constituição v2.2.0 (Princípio I, exceção do Administrador) diz que as funções do Administrador, inclusive as consultas administrativas, existem **somente na Web**, e que o Telegram não vincula nem oferece nada ao Administrador. Como a constituição prevalece, esta spec é **somente Web para o Administrador** (mesmo encaminhamento das features 005 e 006); o Telegram dá a negativa neutra a pedidos administrativos. O que cabe ao **Professor** nesta feature (registrar consentimento dos próprios alunos, ver o bloqueio por falta de consentimento) segue a paridade e existe nos dois canais. Levar eliminação ou auditoria ao Telegram exige emenda (ver Assumptions). Regra vinculante, sem pergunta no clarify (mesmo encaminhamento confirmado nas features 005 e 006).

**Aviso**: esta feature **não é parecer jurídico**. Os prazos foram fechados pelo dono do produto (Clarifications); bases legais e textos do termo seguem como propostas a validar pelo cliente e por quem responde pela LGPD na escola (D11) antes da implantação com dados reais.

## Clarifications

### Session 2026-11-03

- Q: Quando a escola atende um pedido de eliminação, o que acontece com os relatórios aprovados do aluno (D7)? → A: **Remove tudo**: conteúdo e PDFs de todos os relatórios, inclusive aprovados e semestrais, de todos os professores; ficam só identificadores, datas, situações e autoria (FR-022, SC-004).
- Q: Quais prazos de retenção valem como padrão (D6, D9)? → A: Transcrição, 30 dias após aprovar ou cancelar; PDFs de versões não aprovadas, 30 dias após aprovar; cancelados anonimizados em 90 dias; trilha guardada por 5 anos. Todos configuráveis (FR-002).
- Q: Como fica o aluno menor sem consentimento registrado, inclusive os já cadastrados (D8)? → A: **Não haverá menor cadastrado sem consentimento** (acordado com a escola): o consentimento é obrigatório no cadastro do menor, o dos já cadastrados é registrado na implantação, e o bloqueio de IA fica só como garantia para revogação ou nova versão obrigatória do termo (FR-030, FR-034 a FR-036).
- Q: Qual a meta do backup (frequência, tempo de restauração, destino)? → A: **Fora desta feature**: backup automático e restauração testada ficam para a próxima fase. Removidos a história do backup, FR-060 a FR-065 (reservados) e os critérios de restauração; fica a lista de alunos eliminados (FR-026), que a restauração futura DEVE reaplicar.
- Q: Quem recebe os alertas de operação e por qual meio? → A: **Só um painel na Web** do administrador, sem envio por e-mail ou Telegram; risco aceito de o alerta só ser visto quando alguém entra na área administrativa (FR-055, FR-056, SC-006).

## Quem usa esta feature

- **Administrador** (Web): conduz a eliminação a pedido, consulta a trilha de auditoria, registra e consulta consentimentos, vê o painel de tempo e custo.
- **Professor** (Web e Telegram, com paridade): registra o consentimento do responsável ao cadastrar ou editar os próprios alunos (feature 005, FR-024) e é avisado quando um aluno não pode ter relatório de IA por falta de consentimento.
- **Responsável pela LGPD na escola** (D11): pessoa designada pela escola, citada no termo, no inventário e no fluxo de eliminação; não é usuário do sistema por esse papel (se for também administrador, usa a conta de administrador).
- **Operação técnica**: quem mantém a VPS; acompanha os alertas pelo painel da Web (com conta de administrador). Não é papel do sistema.
- **Aluno e responsável: não são atores** (feature 005, FR-001). Os pedidos deles chegam à escola fora do sistema e são registrados pelo administrador.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trilha de auditoria completa e consultável (Priority: P1)

O administrador abre a trilha de auditoria na Web e vê, por período, ator, tipo de evento e registro-alvo, tudo o que aconteceu de relevante: entradas e falhas de entrada, cadastros e alterações, emissão de códigos, aprovações, cancelamentos, consultas e downloads de PDF (do administrador **e do professor**), consentimentos, eliminações e execuções da retenção. Cada evento mostra quem, quando, o quê e sobre qual registro, por identificadores, nunca o conteúdo.

**Why this priority**: sem trilha não há como responder "quem viu os dados desta criança" nem provar que a retenção e a eliminação aconteceram; as demais histórias gravam eventos nela. As features 005 e 006 já gravam parte dos eventos; esta história completa a lista e a torna consultável.

**Independent Test**: executar cada operação da lista de eventos (FR-030) em cada canal onde ela existe e conferir, pela tela da trilha, que existe exatamente um evento correspondente, com os campos obrigatórios e sem nenhum dado pessoal do aluno.

**Acceptance Scenarios**:

1. **Given** um professor que baixa o PDF aprovado de um aluno pelo Telegram, **When** o administrador filtra a trilha por esse aluno, **Then** vê o evento "download de PDF" com o professor como ator, o canal Telegram, o momento, o identificador do relatório e da versão, e nenhum nome, CPF ou trecho do relatório.
2. **Given** uma tentativa de entrada com código errado, **When** o administrador filtra a trilha por "falha de entrada", **Then** vê o evento com momento, canal, resultado e o resumo da origem (feature 005, FR-040), sem o código digitado.
3. **Given** um administrador consultando a trilha, **When** aplica um filtro, **Then** essa consulta também vira evento na trilha.
4. **Given** um evento já gravado, **When** qualquer pessoa ou processo tenta alterá-lo ou apagá-lo fora da retenção da própria trilha, **Then** o sistema recusa e a tentativa fica registrada.
5. **Given** um professor no Telegram que pede a trilha (texto ou comando administrativo), **When** o bot responde, **Then** recebe a negativa neutra do canal (feature 005, FR-037).

---

### User Story 2 - Consentimento do responsável e bloqueio (Priority: P1)

Ao cadastrar ou editar um aluno menor de 18 anos, quem cadastra (administrador na Web; professor na Web ou no Telegram) registra o consentimento do responsável: nome de quem consentiu, data em que consentiu, versão do termo apresentada e forma de coleta. O consentimento é **obrigatório** para cadastrar aluno menor: sem ele o cadastro não é concluído, então não existe aluno menor sem consentimento. O bloqueio de IA continua como garantia para quando o consentimento deixa de valer (revogação ou nova versão obrigatória do termo); nesse caso o professor é avisado com mensagem clara e orientado a registrar o consentimento ou procurar o administrador.

**Why this priority**: é condição para operar com dados reais de menores (D8). Sem ela, o primeiro áudio de uma criança já seria tratado sem base registrada.

**Independent Test**: tentar cadastrar um aluno menor sem consentimento nos dois canais (recusado); cadastrá-lo com consentimento; revogar o consentimento, tentar registrar uma aula nos dois canais (bloqueado, sem chamar provedor de IA), registrar novo consentimento e repetir (permitido).

**Acceptance Scenarios**:

1. **Given** o cadastro de um aluno de 12 anos, **When** quem cadastra (Web ou Telegram) tenta concluir sem informar o consentimento, **Then** o cadastro é recusado com orientação sobre os campos do consentimento, igual nos dois canais.
2. **Given** um aluno de 12 anos cujo consentimento foi revogado, **When** o professor envia um áudio de aula para ele (Web ou Telegram), **Then** nada é enviado a provedor de IA, o áudio é descartado, e o professor vê a mensagem de bloqueio por falta de consentimento, igual nos dois canais.
3. **Given** o mesmo aluno, **When** o professor registra o consentimento (quem consentiu, data, versão vigente do termo) e envia o áudio de novo, **Then** o relatório segue o fluxo normal.
4. **Given** a escola publica uma nova versão do termo e decide que ela exige novo consentimento, **When** o administrador marca a versão como obrigatória, **Then** os consentimentos dados a versões anteriores deixam de valer para novas gerações e os alunos afetados aparecem numa lista de pendências na Web.
5. **Given** um aluno com consentimento revogado pelo responsável, **When** o administrador registra a revogação, **Then** novas gerações de IA para o aluno ficam bloqueadas, relatórios em aberto deixam de ser processados pela IA, e os relatórios aprovados seguem a política (FR-015).
6. **Given** um aluno adulto (18 anos ou mais), **When** o professor registra aula, **Then** não há bloqueio por consentimento do responsável.

---

### User Story 3 - Retenção automática (Priority: P2)

Uma rotina automática trata, todos os dias, os dados que passaram do prazo configurado: apaga transcrições de relatórios já concluídos, apaga PDFs de versões não aprovadas e anonimiza relatórios cancelados. Cada execução registra na trilha quantos itens de cada tipo foram tratados e quais identificadores, sem conteúdo. Nenhum relatório aprovado vigente, nem o seu PDF, é tocado.

**Why this priority**: reduz o volume de dados de menores guardados (minimização) e cumpre a promessa adiada pela spec 001; depende da trilha (US1) para registrar o que foi tratado.

**Independent Test**: com o relógio do teste avançado, criar relatórios aprovados, cancelados e com várias versões, rodar a rotina e verificar que só o que passou do prazo foi tratado, que o aprovado vigente e seu PDF continuam íntegros e que a trilha registra a execução.

**Acceptance Scenarios**:

1. **Given** um relatório aprovado há mais de 30 dias (prazo padrão), **When** a rotina roda, **Then** a transcrição dele é apagada; o conteúdo e o PDF da versão aprovada permanecem e continuam disponíveis no histórico.
2. **Given** um relatório aprovado com versões 1 a 3 (aprovada a 3), **When** passa o prazo de PDFs de versões antigas, **Then** os PDFs das versões 1 e 2 são apagados e o da versão 3 permanece.
3. **Given** um relatório cancelado há mais que o prazo de cancelados, **When** a rotina roda, **Then** a transcrição, o conteúdo de todas as versões e os PDFs são removidos, restando só identificadores, datas, situação e autoria.
4. **Given** um relatório que o professor aprova enquanto a rotina está rodando, **When** a rotina chega a ele, **Then** ela reavalia a situação no momento do tratamento e não apaga nada que tenha passado a ser aprovado vigente.
5. **Given** uma falha no meio da execução, **When** a rotina roda de novo, **Then** retoma sem apagar duas vezes nem deixar item pela metade, e a falha gera alerta.

---

### User Story 4 - Eliminação a pedido do titular (Priority: P2)

O responsável (ou o aluno adulto) pede à escola a eliminação dos dados. O administrador registra o pedido na Web (quem pediu, quando, por qual meio, referência do protocolo), confere o alcance mostrado pelo sistema (quantos relatórios, PDFs e associações serão afetados) e confirma. O sistema anonimiza o aluno e trata todos os relatórios dele, registra o atendimento e emite um comprovante sem dado pessoal para a escola responder ao titular.

**Why this priority**: é direito do titular (D7) e precisa existir antes de operar com dados reais, mas é evento raro; depende da trilha (US1).

**Independent Test**: criar um aluno com relatórios de aula e semestrais em várias situações, executar a eliminação e verificar no banco, no armazenamento de PDFs, nas telas, no bot, na trilha e nos logs que nenhum dado pessoal do aluno é recuperável.

**Acceptance Scenarios**:

1. **Given** um aluno com relatórios aprovados, cancelados e um em aberto, **When** o administrador confirma a eliminação, **Then** nome, CPF (cifra e resumo), data de nascimento, dados do responsável e instrumento são removidos; o aluno passa a aparecer como "Aluno removido" com um identificador curto; todo conteúdo, transcrição e PDF dos relatórios dele é removido; o relatório em aberto é cancelado e tratado igual.
2. **Given** a eliminação concluída, **When** um professor ou administrador busca pelo nome ou pelo CPF antigo, **Then** nada é encontrado, e o CPF antigo pode ser usado num novo cadastro.
3. **Given** a eliminação concluída, **When** o administrador consulta a trilha, **Then** vê o pedido e o atendimento (datas, administrador, alcance em números), e os eventos antigos sobre o aluno continuam lá só com identificadores.
4. **Given** um professor no Telegram que pede para "apagar aluno", **When** o bot responde, **Then** recebe a negativa neutra; o professor continua podendo apenas inativar os próprios alunos (feature 005).

---

### User Story 5 - Tempo, custo e alertas de operação (Priority: P3)

O administrador vê na Web, por mês, o tempo de processamento e o custo estimado das chamadas de IA por áudio, por aula, por aluno, por relatório semestral e o total do mês, separados por canal e por provedor, mais o custo fixo de infraestrutura informado em configuração. O painel mostra alertas quando um provedor de IA falha repetidamente, quando a fila de entrega do canal se acumula, quando o armazenamento falha e quando a retenção falha.

**Why this priority**: é a métrica de custo da seção 2.3 do handoff e dá segurança à operação, mas não bloqueia o uso com dados reais.

**Independent Test**: processar aulas e semestrais com provedores simulados de custo conhecido e conferir os totais por unidade, canal, provedor e mês; simular falhas de provedor, fila e armazenamento e conferir que cada alerta aparece uma vez no painel, sem dado pessoal.

**Acceptance Scenarios**:

1. **Given** um mês com 10 aulas e 2 semestrais processados, **When** o administrador abre o painel do mês, **Then** vê custo e tempo médios e totais por áudio, aula, aluno (por identificador e nome, só na Web do administrador), semestral, canal e provedor, coerentes com a soma das chamadas registradas.
2. **Given** um provedor de transcrição que falha 3 vezes seguidas (padrão), **When** ocorre a terceira falha, **Then** o painel de operação da Web passa a mostrar, em até 5 minutos, um alerta ativo com o provedor, o tipo de falha e o horário, sem identificador de aluno nem conteúdo; novas falhas iguais atualizam o mesmo alerta em vez de criar outro.
3. **Given** que a condição de alerta se resolve, **When** o sistema volta ao normal, **Then** o alerta passa a "normalizado", com o horário, e sai da lista de ativos (fica no histórico de alertas).

---

### User Story 6 - Inventário de tratamento (Priority: P3)

A escola tem um documento versionado no repositório que diz, para cada provedor externo (transcrição, LLM, mensageria e armazenamento), quais dados recebe, com que finalidade, sob qual base legal proposta, onde ficam, por quanto tempo e sob quais termos (inclusive não uso para treinamento e região), além do contato do responsável pela LGPD.

**Why this priority**: é documentação exigida para operar e responder ao titular, mas não muda comportamento do sistema.

**Independent Test**: comparar o inventário com o que os adaptadores efetivamente enviam a cada provedor (por inspeção e pelos testes de contrato) e confirmar que não há dado enviado que não esteja listado.

**Acceptance Scenarios**:

1. **Given** o inventário publicado, **When** um revisor compara com os dados enviados ao provedor de LLM, **Then** encontra listados transcrição, respostas do professor e relatórios aprovados usados no semestral, e encontra declarado que nome, CPF, data de nascimento e dados do responsável não são enviados.
2. **Given** um PR que muda o que vai a um provedor, **When** é revisado, **Then** o checklist de revisão exige a atualização do inventário no mesmo PR.

---

### Edge Cases

- **Aluno que faz 18 anos**: o consentimento do responsável continua registrado como histórico; o aluno deixa de precisar dele para novas gerações. Aluno que, por edição da data de nascimento, passa a ser menor exige consentimento antes da próxima geração.
- **Alunos menores já cadastrados antes desta feature**: a escola já tem o consentimento de todos (acordado com o dono do produto); ele DEVE ser registrado no sistema na implantação, antes de liberar o uso com dados reais. Se, por falha, algum ficar sem registro, vale o bloqueio do FR-034 e ele aparece na lista do FR-035.
- **Relatório em aberto de aluno cujo consentimento deixou de valer**: pode ser aprovado ou cancelado pelo professor, mas não recebe nova chamada de IA (revisão, resposta a pergunta) até o consentimento ser registrado.
- **Semestral que usou relatórios de aula**: a retenção da transcrição das aulas não afeta o semestral aprovado; a eliminação do aluno remove os dois.
- **Aluno compartilhado por vários professores**: a eliminação alcança os relatórios de todos os professores; cada professor perde o aluno da sua lista.
- **Pedido de eliminação de aluno com relatório em processamento pela IA**: a eliminação espera o fim da chamada em curso (ou a cancela) e descarta o resultado; nada é gravado depois da eliminação.
- **Eliminação que falha no meio**: não deixa o aluno meio anonimizado; o pedido fica "em atendimento", a falha gera alerta e o administrador pode repetir a confirmação até concluir.
- **Prazo de retenção configurado como zero ou inválido**: o sistema recusa a configuração na subida (fica com o padrão e registra aviso), nunca interpreta como "apagar tudo agora".
- **Trilha muito grande**: a consulta é paginada e filtrada por período; a retenção da trilha (FR-002) limita o volume.
- **Evento de auditoria que não pode ser gravado**: a operação que o gera não vale (regra já adotada nas features 005 e 006), salvo as execuções da retenção, que param e alertam.
- **Logs e métricas antigos (anteriores à feature)**: fora do escopo de anonimização retroativa; seguem a rotação normal dos logs.

## Requirements *(mandatory)*

### Functional Requirements

**Configuração e política**

- **FR-001**: Todos os prazos desta feature (retenção de transcrição, de PDFs de versões não aprovadas, de relatórios cancelados, da trilha de auditoria e dos registros de execução) DEVEM ser configuráveis por ambiente, ter valor padrão registrado nesta spec e no inventário, e ser validados na subida: valor ausente usa o padrão; valor inválido, zero ou negativo é recusado, mantém o padrão e gera aviso no log.
- **FR-002**: Os valores padrão (D6 e D9, fechados em 2026-11-03) são: transcrição, 30 dias após o relatório ser aprovado ou cancelado; PDFs de versões não aprovadas, 30 dias após a aprovação; relatórios cancelados, anonimizados 90 dias após o cancelamento; trilha de auditoria, 5 anos.
- **FR-003**: O sistema DEVE ter o nome e o contato do responsável pela LGPD na escola (D11) em configuração e DEVE exibi-los no termo de consentimento, na tela de eliminação e no inventário.

**Retenção (D6)**

- **FR-010**: Uma rotina automática DEVE rodar ao menos uma vez por dia e tratar os itens cujo prazo venceu, conforme FR-002.
- **FR-011**: A rotina NUNCA DEVE apagar, alterar ou anonimizar o conteúdo nem o PDF da **versão aprovada vigente** de um relatório de aula ou semestral, nem o registro do relatório aprovado. A situação de cada item DEVE ser reavaliada no momento do tratamento, não só na seleção.
- **FR-012**: Para relatório aprovado, a rotina DEVE apagar a transcrição e os PDFs das versões não aprovadas; o conteúdo textual das versões não aprovadas DEVE ser mantido como histórico de versões (Princípio III) até a eliminação do aluno.
- **FR-013**: Para relatório cancelado, a rotina DEVE apagar transcrição, conteúdo de todas as versões e todos os PDFs, mantendo apenas identificadores, datas, situação, canal e autoria (anonimização do relatório).
- **FR-014**: Relatórios em aberto (rascunho, aguardando respostas, em conferência, com falha) NÃO DEVEM ser tratados pela retenção nesta feature.
- **FR-015**: Cada execução DEVE gravar na trilha um evento com início, fim, resultado e, por tipo de item, a quantidade e os identificadores tratados, sem conteúdo. A rotina DEVE ser idempotente e retomável: repetir uma execução não trata nada duas vezes nem deixa item meio tratado.
- **FR-016**: Falha da rotina DEVE gerar alerta (FR-055) e NÃO DEVE impedir o uso normal do sistema.

**Eliminação a pedido (D7)**

- **FR-020**: O administrador DEVE poder registrar, na Web, um pedido de eliminação de um aluno: quem pediu (titular ou responsável, por papel e nome), data do pedido, meio de recebimento e referência de protocolo. O pedido nasce "recebido".
- **FR-021**: Antes de confirmar, o sistema DEVE mostrar o alcance: quantidade de relatórios de aula e semestrais por situação, quantidade de PDFs, professores associados e consentimentos registrados. A confirmação DEVE exigir digitar uma palavra de confirmação e DEVE expirar em 10 minutos.
- **FR-022**: Confirmada, a eliminação DEVE, numa operação que vale inteira ou não vale: remover nome, CPF (cifra e resumo), data de nascimento, dados do responsável e instrumento do aluno, substituindo o nome por "Aluno removido" e um identificador curto; cancelar relatórios em aberto; remover transcrição, conteúdo de todas as versões e todos os PDFs de todos os relatórios do aluno, de todos os professores, inclusive aprovados; remover as associações com professores; e manter apenas identificadores, datas, situações e autoria.
- **FR-023**: O registro do consentimento do aluno eliminado DEVE manter só identificador, data e versão do termo, sem o nome de quem consentiu.
- **FR-024**: Depois da eliminação, o aluno NÃO DEVE aparecer em buscas, listas, histórico nem no bot, e o CPF antigo DEVE ficar livre para novo cadastro.
- **FR-025**: O sistema DEVE registrar o atendimento (data de conclusão, administrador, alcance em números) e oferecer ao administrador um comprovante sem dado pessoal do aluno, com a referência do protocolo e o contato do responsável pela LGPD.
- **FR-026**: O sistema DEVE manter uma lista dos identificadores de alunos eliminados, com a data, que DEVE sobreviver a uma restauração e servir para reaplicar as eliminações feitas depois da cópia restaurada (o backup é da próxima fase).
- **FR-027**: Pedidos de eliminação NÃO DEVEM existir no Telegram: pedido administrativo no bot recebe a negativa neutra (feature 005, FR-037). O professor continua podendo só inativar os próprios alunos.

**Consentimento (D8)**

- **FR-030**: Para aluno com menos de 18 anos (idade calculada como na feature 005, FR-017), o cadastro DEVE **exigir** o consentimento do responsável (sem ele, o cadastro ou a edição que torna o aluno menor é recusado com `VALIDATION_FAILED`, nos dois canais): nome de quem consentiu, relação com o aluno, data do consentimento (não futura), versão do termo, forma de coleta (papel assinado, digital, outro) e quem registrou no sistema. Cada registro é novo; o anterior fica como histórico.
- **FR-031**: Quem pode registrar: o administrador (Web) para qualquer aluno; o professor (Web e Telegram, com paridade) para os próprios alunos, no cadastro, na edição ou a partir do aviso de bloqueio.
- **FR-032**: Os textos do termo DEVEM ser versionados; o administrador DEVE poder registrar uma versão nova e marcar se ela exige novo consentimento. Só versões marcadas como vigentes ou compatíveis contam como consentimento válido.
- **FR-033**: O administrador DEVE poder registrar a revogação do consentimento (data, quem revogou). Revogação equivale a ausência de consentimento para novas gerações.
- **FR-034**: Sem consentimento válido, para aluno menor, o sistema NÃO DEVE enviar nada a provedor de IA (transcrição, geração, revisão, resposta a pergunta, semestral). O pedido DEVE ser recusado antes de qualquer chamada, o áudio recebido DEVE ser descartado, e o professor DEVE ver a mesma mensagem de bloqueio nos dois canais, com a orientação para registrar o consentimento ou procurar o administrador. Aprovar e cancelar relatório existente continuam permitidos.
- **FR-035**: A Web do administrador DEVE ter a lista de alunos menores sem consentimento válido (consentimento revogado, versão do termo superada ou falha na carga da implantação); em operação normal ela fica vazia.
- **FR-036**: A implantação desta feature DEVE incluir o registro do consentimento dos alunos menores já cadastrados (a escola já o coletou), por carga ou pela tela do administrador, antes de liberar o uso com dados reais; a carga NÃO DEVE levar dado pessoal ao repositório.

**Auditoria (D9)**

- **FR-040**: O sistema DEVE manter uma trilha única de eventos que reúne os registros de autoria das features 005 e 006 e os desta feature, com: identificador do evento, momento, ator (identificador e papel, ou "sistema" para rotinas), canal, tipo de evento, identificador do registro-alvo (e da versão, quando houver), resultado e, nas autenticações, o resumo da origem (feature 005, FR-040).
- **FR-041**: Os eventos obrigatórios são, no mínimo: entrada com sucesso, falha de entrada, bloqueio por tentativas e saída (administrador e professor, todos os canais); cadastro, alteração, inativação e reativação de professor, aluno, associação e administrador; emissão e reemissão de código de acesso e de código de ativação; vínculo e desvínculo do Telegram; aprovação e cancelamento de relatório de aula e semestral; consulta de histórico e abertura ou download de PDF **pelo administrador e pelo professor**, nos dois canais; negativas de autorização; registro, nova versão e revogação de consentimento; registro, confirmação e conclusão de pedido de eliminação; execução da retenção; consulta da própria trilha.
- **FR-042**: A trilha NÃO DEVE conter conteúdo de relatório, transcrição, áudio, nome do aluno ou do responsável, CPF, data de nascimento, telefone, e-mail nem código ou senha digitados; usa identificadores e códigos.
- **FR-043**: A trilha DEVE ser somente acréscimo: nenhum caso de uso, rota ou rotina DEVE alterar ou apagar evento, exceto a remoção dos eventos mais antigos que o prazo de guarda (FR-002), feita pela retenção e registrada como evento. Alteração indevida DEVE ser detectável.
- **FR-044**: A operação que gera evento e o seu evento DEVEM valer juntos (regra das features 005 e 006): sem evento gravado, a operação não vale e o usuário vê a mensagem de falha interna com código de referência.
- **FR-045**: O administrador DEVE poder consultar a trilha na Web, com filtros por período (obrigatório, até 90 dias por consulta), ator, papel, canal, tipo de evento, resultado e identificador do registro-alvo, paginada, em ordem do mais recente. Para facilitar a leitura, a tela PODE resolver identificadores para nomes atuais no momento da exibição, sem gravar nomes na trilha; alunos eliminados aparecem como "Aluno removido".
- **FR-046**: A trilha NÃO DEVE ser acessível ao professor nem pelo Telegram (negativa neutra).

**Observabilidade e custo**

- **FR-050**: Os logs DEVEM ser estruturados (campos nomeados, nível, momento, identificador de correlação), com mensagens em português do Brasil, e NÃO DEVEM conter áudio, transcrição, conteúdo de relatório, CPF, nome do aluno ou do responsável, data de nascimento, telefone, e-mail, código ou senha; usam identificadores (reforça spec 001 FR-055 e feature 005 FR-039).
- **FR-051**: Para cada chamada a provedor de IA, o sistema DEVE registrar provedor, tipo de chamada, canal de origem, duração, quantidade medida (minutos de áudio ou unidades de texto, conforme o provedor), custo estimado, resultado e o relatório a que pertence. O que o FR-056 da spec 001 já grava DEVE ser reaproveitado; o plano levanta o que falta.
- **FR-052**: O custo estimado DEVE usar preços unitários por provedor em configuração, com data de vigência; mudar o preço não altera estimativas já gravadas. O custo fixo mensal de infraestrutura DEVE ser informado em configuração.
- **FR-053**: O administrador DEVE ver na Web, por mês, tempo de processamento e custo estimado médios e totais por áudio, por aula, por aluno, por relatório semestral e o total do mês (IA + infraestrutura), separados por canal e por provedor.
- **FR-054**: Os dados de métricas NÃO DEVEM conter dados pessoais; a ligação com o aluno é por identificador, e o nome só aparece na tela do administrador, resolvido na exibição.
- **FR-055**: O sistema DEVE abrir um alerta quando: um provedor de IA falha 3 vezes seguidas ou mais de 20% das chamadas em 15 minutos; a fila de entrega do canal Telegram tem itens esperando há mais de 5 minutos ou mais de 50 itens; uma gravação ou leitura do armazenamento de PDFs falha; a retenção falha. Limites configuráveis.
- **FR-056**: Os alertas DEVEM aparecer **somente num painel de operação na Web do administrador** (sem envio por e-mail, Telegram ou outro meio), em até 5 minutos da condição, sem dado pessoal, com tipo, componente, início, última ocorrência e situação (ativo ou normalizado). Ocorrências iguais enquanto o alerta está ativo DEVEM atualizar o mesmo alerta; a normalização DEVE ser registrada. A área administrativa DEVE indicar, em qualquer tela, que há alerta ativo. O histórico de alertas segue a guarda da trilha (FR-002).

**Backup e restauração**

- Fora desta feature: o backup automático e a restauração testada ficam para a próxima fase (decisão de 2026-11-03). Os números FR-060 a FR-065 ficam reservados. Esta feature só deixa pronta a lista de alunos eliminados (FR-026), que a restauração da próxima fase DEVE reaplicar.

**Inventário de tratamento**

- **FR-070**: O repositório DEVE ter um inventário de tratamento versionado, em português, listando para cada provedor externo (transcrição, LLM, mensageria, armazenamento): dados enviados, finalidade, base legal proposta (a validar), local de armazenamento e região, prazo de retenção no provedor, uso para treinamento e referência aos termos; e, para o sistema, os dados guardados, os prazos (FR-002) e o contato do responsável pela LGPD.
- **FR-071**: Toda mudança no que é enviado a um provedor DEVE atualizar o inventário no mesmo PR (item de revisão).

**Canais e permissões**

- **FR-080**: Todas as funções do administrador desta feature (trilha, eliminação, consentimentos de qualquer aluno, versões do termo, pendências, painel de custo, painel de alertas) DEVEM existir somente na Web (constituição v2.2.0, Princípio I); o Telegram DEVE dar a negativa neutra. A autorização é decidida no núcleo e a Matriz de permissões da feature 005 DEVE ser estendida.
- **FR-081**: As funções do professor desta feature (registrar consentimento dos próprios alunos e o aviso de bloqueio) DEVEM existir com paridade na Web e no Telegram, pelos mesmos casos de uso.
- **FR-082**: Mensagens de erro desta feature DEVEM seguir as classes da feature 005 (FR-049), em português, sem detalhe interno.

### Key Entities *(include if feature involves data)*

- **Política de retenção**: os prazos configurados (FR-001, FR-002), com valor efetivo e origem (padrão ou configuração).
- **Execução da retenção**: início, fim, resultado e, por tipo de item, quantidade e identificadores tratados; espelhada na trilha.
- **Pedido de eliminação**: aluno-alvo (identificador), quem pediu (papel e nome, apagado após o atendimento conforme a política), data, meio, protocolo, situação (recebido, em atendimento, concluído), administrador que confirmou, data de conclusão e alcance em números.
- **Aluno eliminado**: identificador e data da eliminação; base para reaplicar eliminações após uma restauração (próxima fase).
- **Termo de consentimento (versão)**: número, data, texto, se exige novo consentimento, se é vigente.
- **Consentimento**: aluno, quem consentiu (nome e relação), data, versão do termo, forma de coleta, quem registrou, situação (válido, revogado, superado) e data de revogação.
- **Evento de auditoria**: momento, ator e papel, canal, tipo, registro-alvo e versão, resultado, resumo da origem quando autenticação; somente acréscimo.
- **Medição de chamada de IA**: provedor, tipo, canal, duração, quantidade, custo estimado, resultado, relatório.
- **Preço de provedor**: provedor, unidade, valor, vigência.
- **Alerta**: tipo, componente, início, última ocorrência, situação (ativo, normalizado) e horário da normalização.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um teste automatizado que planta dados fictícios marcados (nome, CPF, data de nascimento, dados do responsável, trechos de transcrição e de relatório) e percorre todos os fluxos dos dois canais encontra **zero** ocorrências desses valores nos logs, nas métricas, nos alertas e na trilha.
- **SC-002**: **100%** dos tipos de evento listados no FR-041 geram exatamente um evento na trilha em cada canal onde a operação existe, verificado por teste automatizado.
- **SC-003**: Em teste com relógio avançado, **100%** dos itens vencidos são tratados e **0** relatórios aprovados vigentes (conteúdo ou PDF) são alterados ou apagados.
- **SC-004**: Após uma eliminação, a busca pelos valores do aluno no banco, no armazenamento de PDFs, nas telas, no bot, nos logs e na trilha encontra **zero** dados pessoais recuperáveis; o atendimento leva menos de 5 minutos de trabalho do administrador.
- **SC-005**: **0** chamadas a provedor de IA para aluno menor sem consentimento válido, nos dois canais, verificado por teste automatizado.
- **SC-006**: Cada condição de alerta simulada aparece no painel de operação da Web em até **5 minutos**, como um único alerta ativo, e passa a normalizado quando a condição some.
- **SC-007**: O administrador encontra, em até **3 interações**, quem acessou os relatórios de um aluno num período.
- **SC-008**: O custo estimado mensal do painel difere em menos de **1%** da soma das medições de chamadas registradas no mês.
- **SC-009**: **0** funções administrativas desta feature acessíveis pelo Telegram, e paridade completa entre Web e Telegram para as funções do professor desta feature.

## Assumptions

- **Canal do administrador**: somente Web, pela constituição v2.2.0; o pedido falava em Web e Telegram. Levar eliminação ou trilha ao Telegram exige emenda (Princípio I).
- **Prazos (D6) — FECHADA em 2026-11-03**: os valores do FR-002 foram aprovados pelo dono do produto; o responsável pela LGPD pode ajustá-los por configuração.
- **Eliminação (D7) — FECHADA em 2026-11-03**: eliminação **completa**, inclusive dos relatórios aprovados (mantidos só identificadores, datas e autoria). Manter o conteúdo aprovado anonimizado foi descartado: exigiria base legal validada e limpeza de nomes em texto livre.
- **Consentimento (D8) — FECHADA em 2026-11-03**: não haverá aluno menor cadastrado sem consentimento (acordado com a escola): o consentimento é obrigatório no cadastro (FR-030) e o dos já cadastrados é registrado na implantação (FR-036). O bloqueio de IA (FR-034) fica como garantia para revogação e nova versão obrigatória do termo.
- **Trilha (D9) — guarda FECHADA em 2026-11-03**: prazo de guarda de 5 anos; as leituras do professor passam a ser registradas (antes só as do administrador, feature 006). O registro de autoria existente das features 005 e 006 é a base da trilha, não um sistema paralelo.
- **Responsável pela LGPD (D11)**: pessoa a definir pela escola; o sistema só guarda nome e contato em configuração.
- **Versões não aprovadas**: o conteúdo textual delas é mantido até a eliminação (Princípio III, versões rastreáveis); só os PDFs e a transcrição saem com a retenção.
- **Relatórios em aberto abandonados**: não são tratados pela retenção nesta feature (FR-014); uma regra de expiração seria evolução de produto.
- **Destino dos alertas — FECHADA em 2026-11-03**: só o painel de operação da Web do administrador; sem envio ativo. Risco aceito: um alerta só é visto quando alguém entra na área administrativa. Envio por e-mail ou outro meio fica como evolução.
- **Fila de entrega do canal**: entende-se como as ações pendentes de envio ao Telegram (mensagens e documentos que o backend devolve ao transporte).
- **Logs antigos**: não são anonimizados retroativamente; seguem a rotação existente.
- **Inventário**: documenta termos dos provedores atuais; mudar de provedor está fora do escopo.
- **Fora de escopo**: parecer jurídico; backup automático e restauração testada (próxima fase, decisão de 2026-11-03; até lá vale o backup manual de `infra/README.md`, seção 5); mudança de provedor de IA; envio de relatórios às famílias; acesso do aluno ou do responsável ao sistema; portabilidade de dados; anonimização de logs antigos.

### Situação das decisões

| Decisão | Situação | Requisitos afetados |
|---|---|---|
| Canal do administrador | **Vinculante**: somente Web (constituição v2.2.0, Princípio I) | FR-027, FR-046, FR-080, SC-009 |
| D6 — prazos | **Fechada** em 2026-11-03: 30/30/90 dias | FR-002, FR-010 a FR-016, SC-003 |
| D7 — alcance da eliminação | **Fechada** em 2026-11-03: completa, inclusive aprovados | FR-022 a FR-026, SC-004 |
| D8 — consentimento | **Fechada** em 2026-11-03: obrigatório no cadastro do menor; carga na implantação | FR-030, FR-034 a FR-036, SC-005 |
| D9 — guarda da trilha | **Fechada** em 2026-11-03: 5 anos | FR-002, FR-043 |
| Backup | **Fora desta feature** (próxima fase) | FR-026; FR-060 a FR-065 reservados |
| Destinatário dos alertas | **Fechada** em 2026-11-03: só painel na Web | FR-055, FR-056, SC-006 |
| D11 — responsável pela LGPD | **Pendente** (a escola indica a pessoa); o sistema só guarda nome e contato em configuração, sem bloquear o plano | FR-003 |
