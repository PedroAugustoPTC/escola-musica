<!--
Sync Impact Report
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
  em produção; implantação definitiva em VPS da Hostinger). "Stack agnóstica" passa a abrir
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
- Web e Telegram DEVEM ser canais de entrada equivalentes (paridade funcional): toda
  funcionalidade disponível em um deles DEVE estar disponível no outro, executando os mesmos casos
  de uso com as mesmas regras de autorização, validação e versionamento.
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
perder nenhuma funcionalidade.

### II. Fonte Única da Verdade

- Somente o backend (núcleo da aplicação) DEVE escrever no banco de dados.
- Orquestradores, automações e integrações de canal NÃO DEVEM conter regra de negócio,
  autorização ou validação de domínio e NÃO DEVEM persistir dados de domínio; todo dado que
  entra por eles DEVE passar por um caso de uso do backend.
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

### VI. Nomenclatura Previsível e Linguagem Ubíqua

- Código e identificadores (tipos, funções, variáveis, módulos, arquivos, tabelas, campos e rotas)
  DEVEM ser escritos em português do Brasil, sem acentos nem cedilha (ex.: `Servico`,
  `Relatorio`, `Aprovacao`).
- Os nomes DEVEM usar a linguagem ubíqua do domínio (Professor, Aluno, Aula, RelatorioAula,
  RelatorioSemestral, VinculoTelegram etc.); NÃO DEVEM existir sinônimos para o mesmo conceito.
- Os nomes DEVEM indicar camada e responsabilidade por meio dos sufixos padronizados:
  - Entidades de domínio: substantivo de negócio, sem sufixo técnico (ex.: `Aluno`, `Aula`).
  - `*CasoDeUso`: uma ação de negócio (ex.: `AprovarRelatorioAulaCasoDeUso`).
  - `*Controlador`: adaptador de entrada (ex.: `RelatorioAulaControlador`).
  - `*Servico`: serviço de aplicação que apoia casos de uso sem substituí-los.
  - `*Repositorio`: porta de persistência (ex.: `AlunoRepositorio`).
  - `*Porta`: porta de saída para provedor externo (ex.: `TranscricaoPorta`, `LlmPorta`).
  - `*Adaptador`: implementação concreta de uma porta ou repositório
    (ex.: `AlunoRepositorioAdaptador`).
  - `*Mapeador`: conversão entre objetos de transporte e de domínio.
  - `*Dto`, `*Requisicao`, `*Resposta`: objetos de transporte na borda.
- Exceções permitidas, e somente estas: palavras reservadas da linguagem, APIs de terceiros, nomes
  exigidos por ferramentas ou frameworks e siglas consagradas (PDF, LLM, HTTP, DTO).

**Justificativa**: nomes previsíveis tornam a arquitetura autoexplicativa e impedem que regra de
negócio escape para a camada errada; um único idioma elimina a inconsistência vista no MVP.

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
  - Web e Telegram são canais de entrada equivalentes (Princípio I): registro de aula,
    relatório semestral, histórico e administração DEVEM estar disponíveis nos dois canais.
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
  - A implantação definitiva DEVE ser feita em uma VPS da Hostinger, subindo os contêineres a partir
    das definições versionadas no repositório. Na VPS, apenas o ponto de entrada HTTPS DEVE ser
    exposto publicamente (Princípio IV).
  - As definições de contêiner e os scripts de implantação são artefatos de automação e DEVEM ser
    versionados (Princípio VIII).
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
    em um dos canais (Princípio I); entregar em um canal só é desvio que DEVE ser justificado no
    plano e registrar a entrega pendente no outro canal;
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

**Version**: 1.3.0 | **Ratified**: 2026-09-30 | **Last Amended**: 2026-09-30
