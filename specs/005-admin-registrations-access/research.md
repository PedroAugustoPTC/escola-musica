# Research: Administração — acesso e cadastros

**Feature**: `005-admin-registrations-access` | **Data**: 2026-10-20 | **Spec**: [spec.md](./spec.md) | **Plano**: [plan.md](./plan.md)

Cada decisão segue o formato *Decisão / Justificativa / Alternativas descartadas*. A constituição considerada é a
**v2.2.0** (administração só na Web; paridade integral para o Professor). As decisões D2, D4, D5 e D9 já foram fechadas
no `/speckit-clarify` e aqui só se detalha *como*. Nenhuma pendência do tipo "NEEDS CLARIFICATION" restou: a tabela da
seção 0 mostra onde cada item que a spec delegou ao plano foi resolvido.

> **Aviso de divergência com o pedido de planejamento.** O pedido cita "administração só na Web" entre as *alternativas
> descartadas* e fala em vínculo do Telegram "(administrador e professor)". As Clarifications de 2026-10-20 e a emenda
> v2.2.0 (PR #28, mesclado) **adotaram** exatamente o contrário: o administrador opera **somente pela Web**, sem vínculo
> de Telegram; o vínculo e a confirmação no Telegram valem para o **professor**. O plano segue a spec e a constituição
> vigentes (D2 fechada) e registra aqui, como descartada, a alternativa oposta (R-09): administração também no Telegram.
> Se a intenção do pedido era reabrir a D2, isso exige nova emenda da constituição e voltar ao `/speckit-clarify`.

## 0. Itens que a spec delegou ao plano

| Item delegado | Resolvido em |
|---|---|
| parâmetros do hash de senha (FR-004) | R-04 |
| nomes das variáveis do primeiro administrador (FR-034) | R-16 |
| constantes dos limites de campos (FR-018) | `data-model.md` §5 |
| procedimento de rotação de chaves do CPF (FR-020) | R-12 e `contracts/initial-load.md` §5 |
| modelo de sessão do administrador e revogação (FR-005, FR-047) | R-03, R-08 |
| vínculo e confirmação no Telegram (D2, FR-009, FR-036) | R-09, R-21 |
| colunas de autoria com o papel do autor (FR-040) | R-10 |
| validação de CPF e de data no núcleo (FR-018) | R-11 |
| CPF duplicado sem vazar dado de outro professor (FR-024, FR-025) | R-13, R-14 |
| contrato OpenAPI da área administrativa e do cadastro pelo professor | R-18 |
| telas e identidade (FR-045) | R-20 |
| ação nova "apagar mensagem" no transporte (Assumptions) | R-21 |
| limite contra sondagem de CPF (FR-024) | R-06 |

## R-01 — Um caso de uso por operação, com ator e escopo no núcleo

**Decisão**: cada operação da Matriz de permissões é **um** `*UseCase` do núcleo que recebe um `Actor`
(`role`, `id`, `origin`, e a marca `passwordChangeRequired`) e, como primeira instrução, chama
`ActorAuthorizationService.require(actor, Operation.X)`, que consulta a `PermissionMatrix` (declarada no núcleo e
idêntica a `contracts/permission-matrix.json`). O **escopo** (administrador: escola inteira; professor: só os alunos
associados) é aplicado por `StudentScope`, passado às consultas do repositório, e não por filtragem posterior. O mesmo
caso de uso atende Web, Telegram e carga inicial; os adaptadores só montam o `Actor` a partir da sessão/vínculo,
traduzem DTO ↔ comando e mapeiam `DomainException` para o canal.

**Justificativa**: FR-002 e FR-038 exigem autorização decidida no núcleo, "independentemente de interface, canal ou
transporte"; Princípio I proíbe regra de negócio em adaptador; Princípio II impede que identificador de canal
autorize. Concentrar a decisão em uma matriz torna o teste de acesso cruzado (SC-005) uma iteração sobre dados.

**Alternativas descartadas**: (a) autorizar com papéis do Spring Security (`@PreAuthorize`) nos controllers —
decisão fora do núcleo, não vale para Telegram nem carga, e revela rota existente com 403; (b) casos de uso por papel
(`AdminRegisterStudent` e `TeacherRegisterStudent`) — duplica validação, unicidade e CPF; (c) filtrar o resultado no
controller — vaza dado se o filtro falhar e quebra FR-038.

## R-02 — Exatamente dois papéis; "sistema" não é papel

**Decisão**: `enum Role { ADMINISTRATOR, TEACHER }`. Operações sem usuário (primeiro administrador, carga inicial) usam
`Actor.system(ActionOrigin)`, **não autenticável**: não vem de cookie, vínculo nem requisição; só `inbound.seed` e o
bootstrap o constroem (regra ArchUnit). `ActionOrigin` = `WEB`, `TELEGRAM`, `SYSTEM_STARTUP`, `INITIAL_LOAD` (é o "canal"
do registro de autoria; `Channel` WEB/TELEGRAM continua só para o que já usa). Um teste enumera `Role.values()` e exige 2
(SC-007).

**Justificativa**: FR-001 fecha o conjunto e proíbe papel de aluno/responsável; o ator de sistema é uma origem, não uma
credencial (FR-044: "sem criar papel nem credencial novos").

**Alternativas descartadas**: papel `SECRETARIAT`/`SYSTEM` (cria credencial e superfície); papel `GUARDIAN`/`STUDENT`
(proibido); carga inicial autenticada como administrador "técnico" (credencial permanente no banco).

## R-03 — Sessão do administrador: opaca, revogável, fixa em 2 h

**Decisão**: tabela própria `administrator_session` (`id`, `administrator_id`, `token_hash`, `created_at`, `expires_at`
= `created_at` + 2 h, `revoked_at`, `revocation_reason`). Token de 256 bits gerado pelo servidor a cada login (nunca
aceita identificador do cliente), só o SHA-256 vai ao banco (mesmo `DataProtectionPort.generateSessionToken`/`sessionTokenHash`
do professor). **Nunca renova**: `expires_at` não muda por uso. A cada requisição o filtro executa **uma** consulta que
junta sessão e conta (`active`, `password_provisional`) e rejeita se vencida, revogada ou conta inativa — sem cache de
autorização (FR-047). Cookie `admin_session`: `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/admin`, sem `Max-Age`
(cookie de sessão do navegador; a expiração real é do servidor). "Sair" apaga a linha daquela sessão; outras sessões do
mesmo administrador continuam (FR-046). Várias sessões simultâneas permitidas.

**Justificativa**: o pedido oferece "token com versão de credencial **ou** sessão revogável". O FR-047 exige que o servidor
valide sessão, vínculo e estado da conta em **toda** requisição; isso já obriga uma leitura de banco por requisição, o
que elimina o ganho do token autocontido e deixa a revogação em lote (inativar, reemitir) trivial: marcar as linhas. Reusa
o padrão testado do professor (`WebSession`, hash do token, `AccessSweepTask`) e mantém papéis em tabelas e cookies
independentes (FR-003).

**Alternativas descartadas**: (a) JWT com `credential_version` — validar a versão exige ler a conta por requisição de
qualquer forma; acrescenta gestão de chaves, algoritmo e relógio; expõe papel no cliente; revogação seletiva ("Sair" em
uma sessão) fica complexa; (b) reaproveitar `web_session` com coluna de papel — mistura a expiração deslizante do
professor com a fixa do administrador, a FK aponta para `teacher`, e um erro de consulta cruzaria credenciais;
(c) `HttpSession` do servlet — a aplicação é stateless por desenho (R-12 da 001) e a sessão não sobreviveria a reinício;
(d) renovar por uso — vetado pelo FR-005.

## R-04 — Hash de senha: Argon2id

**Decisão**: `PasswordHashPort` (`hash`, `verify`, `verifyDummy`) no núcleo; adaptador `Argon2PasswordHashAdapter` usa
Argon2id (`Argon2PasswordEncoder` do Spring Security Crypto, com `org.bouncycastle:bcprov-jdk18on` como **única
dependência nova** de produção). Parâmetros configuráveis (`reports.administrator.password.*`), padrão no mínimo
recomendado pela OWASP: memória 19 MiB, 2 iterações, paralelismo 1, sal de 16 bytes **único por senha**, saída de 32
bytes; o formato codificado guarda os parâmetros, então ajustar o custo não invalida hashes antigos (rehash no
próximo login bem-sucedido quando os parâmetros mudam). Política (`PasswordPolicy`, domínio): 12 a 128 caracteres
(contados em pontos de código, após normalização NFC), sem regra de composição, recusa igual ao e-mail ou ao nome
(sem diferenciar maiúsculas). E-mail desconhecido, conta inativa ou ainda não ativada executam
`verifyDummy` com um hash fixo do mesmo custo, para que tempo e contagem não revelem a existência da conta (FR-006).

**Justificativa**: FR-004 pede hash adaptativo, custo configurável e sal único; senha de até 128 caracteres elimina o
bcrypt (limite de 72 bytes truncaria em silêncio). O bloqueio por origem é checado **antes** do hash, então um atacante
não consegue usar o custo do Argon2 como amplificador de negação de serviço além dos 5 erros por origem.

**Alternativas descartadas**: bcrypt (72 bytes); PBKDF2-HMAC-SHA-256 do JDK (sem dependência, mas sem dureza de memória,
inferior contra GPU); scrypt (menos suporte); manter o resumo HMAC usado nos códigos (resumo rápido, inadequado a senha).
Se a dependência for vetada na revisão, o plano de contingência é PBKDF2 com 600 000 iterações, trocando só o adaptador.

## R-05 — Bloqueio por tentativas: estender o serviço existente

**Decisão**: **o mesmo** `AccessAttemptsControlService` (usado por Web e Telegram) ganha um **escopo** e uma **política**:
`AttemptScope` = `TEACHER_CODE` (comportamento atual, por canal), `ADMIN_SIGN_IN`, `ADMIN_ACTIVATION`,
`TEACHER_CPF_PROBE`; `AttemptPolicy(maxAttempts, lockDuration, window)` (janela `null` = falhas consecutivas até um
sucesso, como hoje). A chave é `(scope, channel, origin_hash)`. Mudanças de comportamento em `AccessAttempt`:
(1) falha **durante** bloqueio ativo não conta nem prorroga (FR-006); (2) sucesso só zera se **não** houver bloqueio ativo
— e `confirmSuccess` roda sob o mesmo lock de linha, de modo que uma senha correta que chega depois da quinta falha é
recusada (FR-006, "inclusive com a senha correta"); (3) com janela, a contagem recomeça quando a primeira falha tem mais
que a janela. **Atomicidade (FR-041)**: o repositório ganha `registerFailure(scope, channel, hash, now, policy)` que, em
transação `REQUIRES_NEW`, faz `INSERT … ON CONFLICT DO NOTHING`, `SELECT … FOR UPDATE` da linha e aplica a regra do
domínio (`AccessAttempt.registerFailure`) antes de gravar — a regra continua no núcleo; o adaptador só serializa. A
**origem** do administrador é o endereço visto pelo Caddy: o Caddy já substitui `X-Forwarded-For` por `{remote_host}`
(`header_up`) e `RequestOriginResolver` só confia no cabeçalho quando a conexão vem da rede interna; o plano reusa o
resolvedor e acrescenta um teste que envia `X-Forwarded-For` forjado de fora da rede interna e de dentro e confere
que a origem usada é a do proxy.

**Justificativa**: o pedido exige reaproveitar o serviço; a spec exige contagem separada por papel e atômica. A janela
generaliza o mesmo mecanismo para a sondagem de CPF.

**Alternativas descartadas**: serviço novo só do administrador (duplica regra); limitador em memória ou no Caddy (não
sobrevive a reinício, não conhece a regra de zerar no sucesso, não cobre Telegram); bloqueio por conta (permite bloquear o
administrador conhecendo só o e-mail — risco já descartado na spec); bibliotecas de rate limit (Bucket4j) — dependência
sem ganho.

## R-06 — Limite contra sondagem de CPF pelo professor

**Decisão**: escopo `TEACHER_CPF_PROBE`, chave = id do professor (hash), política **5 recusas em 15 minutos → bloqueio de
15 minutos**, todos configuráveis (`reports.access.cpf-probe.max-refusals`, `window-minutes`, `lock-minutes`). Só as
operações que informam CPF (`STUDENT_REGISTER` e `STUDENT_UPDATE` com CPF novo) verificam o bloqueio **antes** de qualquer
consulta de duplicidade e respondem `OPERATION_TEMPORARILY_BLOCKED` (tempo restante, sem motivo). Só recusa por CPF de
**aluno alheio** conta; "já está entre os seus", CPF inválido e CPF livre não contam. O sucesso não zera a janela (uma
sondagem esperta alterna acertos e erros).

**Justificativa**: FR-024 quantifica o limite; manter a janela deslizante (e não "consecutivas") evita que um acerto
intercalado reinicie a contagem.

**Alternativas descartadas**: CAPTCHA (proibido ao agente e inadequado ao Telegram); atraso progressivo (estado extra, sem
ganho); bloqueio por origem (o professor pode estar no Telegram, sem IP útil).

## R-07 — Códigos de acesso e de ativação

**Decisão**: o professor continua com `AccessCode` (8 caracteres) e o `AccessCodeIssuanceService` **é reaproveitado**
(unicidade por tentativa de regeração, HMAC). Novo `ActivationCode` do administrador: 12 caracteres do **mesmo alfabeto
sem ambíguos** (32 símbolos → 60 bits), HMAC-SHA-256 com o `HMAC_SECRET` e prefixo de contexto `activation:` (prefixo novo,
em inglês; o prefixo `codigo:` do código de acesso é valor já persistido e fica como está), guardado em
`administrator.activation_code_hash` (**no máximo um pendente** por administrador por construção da coluna) com
`activation_code_expires_at` (7 dias). A ativação é feita com **e-mail + código**: carrega-se a conta pelo e-mail e compara-se o
resumo em tempo constante (`MessageDigest.isEqual`), com o bloqueio `ADMIN_ACTIVATION`. Mensagem única
`INVALID_ACTIVATION` para e-mail desconhecido, código errado, vencido ou já usado. O código só existe na resposta da
operação que o gerou (`TeacherWithCode`/`AdministratorWithActivationCode`), com `Cache-Control: no-store`; nunca em URL,
log, registro de autoria ou lista; o sistema nunca o envia.

**Justificativa**: FR-011 e FR-032 definem forma, tamanho, tentativas e exibição única; a chave secreta fora do banco
(HMAC) é exigida porque o espaço de códigos é pequeno.

**Alternativas descartadas**: link de ativação com token na URL (URL vai a logs do proxy, histórico e `Referer`);
envio por e-mail (a spec veda; o sistema não tem canal de e-mail); código de ativação numérico de 6 dígitos (entropia
insuficiente); reutilizar a tabela de professores para administradores (papéis independentes, RN-14).

## R-08 — Revogação "na hora", em todos os caminhos

**Decisão**: uma operação de revogação por situação, todas **na mesma transação** da mudança de estado e do registro
de autoria:

| Gatilho | Efeito |
|---|---|
| inativar professor | `active=false`; invalida o código; **marca** as sessões Web do professor como revogadas (`revocation_reason = ACCOUNT_INACTIVATED`); encerra o vínculo do Telegram |
| reemitir código | troca o código; marca as sessões Web como revogadas (`CODE_REISSUED`); vínculo só com a opção `endTelegramLink` |
| desvincular Telegram | encerra o vínculo |
| inativar administrador | `active=false`, apaga `password_hash` e código de ativação pendente; revoga todas as sessões (`ACCOUNT_INACTIVATED`) |
| reativar administrador | `active=true`, **sem** senha, com código de ativação novo (exige definir senha de novo) |

"Encerrar o vínculo" é o novo `TelegramLinkTerminationService`, chamado por **todo** caminho (inativar, desvincular,
reemitir com opção, e o "sair" do professor): apaga `telegram_link` (e, por `ON DELETE CASCADE`, o cadastro em andamento
com o CPF cifrado — FR-036) e **descarta** as ações `PENDING` daquele chat (`ChannelActionRepository.discardPendingByChat`,
código `LINK_UNDONE`). Marcar em vez de apagar as sessões permite o texto próprio para quem perde o acesso
(`ACCESS_ENDED`, FR-042) e impede que reativar "ressuscite" uma sessão. A validação (R-03 e `ValidateSessionWebUseCase`) lê
`revoked_at` e o estado da conta em **toda** requisição.

**Justificativa**: SC-010 e FR-047 pedem que nenhuma requisição iniciada depois da conclusão seja aceita; como a
validação não usa cache, o commit da transação de revogação é o ponto de corte. Uma requisição em curso pode terminar.

**Alternativas descartadas**: apagar as sessões na hora (perde a distinção entre expirada e encerrada); cache de sessão em
memória com invalidação por evento (reintroduz a janela que o FR-047 proíbe); avisar o chat desvinculado (a spec veda).

## R-09 — Vínculo e confirmação no Telegram (D2): só o professor

**Decisão**: o Telegram é do **Professor**. O vínculo continua o da 001 (`LinkTelegramUseCase`: o professor envia o **código de
acesso**; uma conta ⇄ um professor; não é consumido). O administrador **não tem vínculo, código de vínculo, comando, menu
nem botão**; qualquer tentativa recebe a negativa neutra (R-21) e a conta sem vínculo só recebe a orientação de entrada.
A **confirmação** antes de gravar é obrigatória para toda gravação do professor no bot (cadastrar, editar, inativar,
reativar aluno): cadastrar e editar passam por um **cadastro em andamento persistido** com resumo sem CPF e token de uso
único (R-21); inativar e reativar usam a confirmação "sim" já existente do bot. A confirmação vale só para o dono
(o vínculo do chamador), uma vez, e perde a validade junto com o cadastro.

**Justificativa**: D2 fechada no clarify e emenda v2.2.0 (Princípio I: "Estender o Telegram ao Administrador exige
emenda"). O ganho de segurança é real: um papel que enxerga a escola inteira não fica atrelado a uma conta de
mensageria.

**Alternativa descartada — administração também no Telegram**: vincular o administrador por código de ativação ou
senha no bot, com menu administrativo. Descartada pela decisão do dono do produto (a secretaria trabalha no
computador; vincular mensageria a um papel de poder total amplia a superfície sem ganho de uso) e porque exigiria
emendar a constituição de volta. **Não é um desvio de paridade**: a constituição v2.2.0 define as funções do
administrador como somente Web (ver Constitution Check I do plano).

## R-10 — Registro de autoria (D9): colunas com o papel do autor

**Decisão**: tabela `authorship_record`, somente de acréscimo. Colunas: `id`, `occurred_at`, `author_id` (nulo para
"sistema" e para falha de entrada com e-mail desconhecido), `author_role` (`ADMINISTRATOR` | `TEACHER` | nulo), `origin`
(`WEB` | `TELEGRAM` | `SYSTEM_STARTUP` | `INITIAL_LOAD`), `operation` (nome do `Operation` ou do evento de autenticação),
`target_type`, `target_id`, `result` (`SUCCESS` | `FAILURE` | `DENIED` | `BLOCKED`), `error_code`, `origin_hash` (só nas
autenticações; hash com chave do endereço de rede, não o endereço) e `correlation_id` (id da requisição Web ou
`update_id` do Telegram). **Sem CPF, sem dado do responsável, sem conteúdo de relatório, sem e-mail digitado** quando a
conta não existe (`error_code = UNKNOWN_EMAIL`). Sem chaves estrangeiras (sobrevive à remoção de administradores, FR-034b).
`BEFORE UPDATE OR DELETE` e `TRUNCATE` disparam exceção: nenhuma operação da aplicação altera ou apaga (FR-040,
integridade); a retenção/expurgo é problema da 007, que tratará o gatilho. Gravação em dois modos pela mesma porta
(`AuthorshipRecorderPort`): `record` (mesma transação do caso de uso — **gravação e registro valem juntos**; falha do
registro desfaz a operação e vira `INTERNAL_ERROR`) e `recordIndependent` (`REQUIRES_NEW`, para falhas, bloqueios e negativas
cuja transação será desfeita; falha de gravação vai ao log técnico sem dado sensível e o acesso continua recusado).

**Justificativa**: FR-040 lista campos, eventos e atomicidade; "papel do autor" separa o administrador do professor no
mesmo `author_id` (UUIDs de tabelas diferentes). `origin_hash` e não o IP bruto aplicam o Princípio IV (mínimo necessário)
e usam o mesmo hash das tentativas.

**Alternativas descartadas**: log de aplicação (não é registro estruturado nem append-only); Hibernate Envers (cobre só
gravações JPA, não autenticações/negativas, e acopla o domínio ao ORM); tabela por entidade (inviabiliza a trilha única
da 007); guardar o IP em claro (reversível; se a 007 exigir, acrescenta-se a coluna então — o custo é perder o histórico
anterior, aceito em favor da privacidade); event sourcing (Princípio VII).

## R-11 — Validação de CPF, datas e campos no núcleo

**Decisão**: objetos de valor do domínio fazem toda a validação e normalização: `Cpf` (já existe: 11 dígitos, dígitos
verificadores, repetidos recusados; ganha aceitação de máscara testada), `BirthDate` (calendário real via `LocalDate`,
nem futura nem anterior a 120 anos, referência = **data de hoje no fuso da escola** via `ClockPort`), `PersonName`
(2–120, NFC, trim, sem caracteres de controle), `EmailAddress` (≤ 254, formato, minúsculas), `BrazilianPhone`
(DDD válido + 10 ou 11 dígitos, só dígitos guardados), `Instrument` (≤ 60), `PasswordPolicy`. `StudentData.of(...)`
valida **tudo** e acumula as violações em um único `VALIDATION_FAILED` com `details.fields[]` (a Web mostra todos os
erros de uma vez); a coleta campo a campo do Telegram chama o mesmo validador de um campo. Menor de 18 anos na data do
cadastro ou da edição exige nome, telefone e e-mail do responsável; ao virar adulto, deixam de ser obrigatórios e são
**mantidos** até quem edita apagá-los. DTOs da Web têm só validação de **forma** (tamanho de payload, tipo).

**Justificativa**: o pedido manda validar CPF e data no núcleo; a mesma regra precisa valer na Web, no Telegram e na
carga (FR-044). Acumular violações evita ida e volta do formulário.

**Alternativas descartadas**: Bean Validation nos DTOs (a regra ficaria fora do núcleo, não valeria no Telegram nem na
carga); validar no frontend (Princípio II); validar em cada caso de uso (duplicação).

## R-12 — CPF: reaproveitar cifra e resumo; rotação de chaves

**Decisão**: **sem mudança** em `DataProtectionAdapter`: AES-256-GCM com IV de 12 bytes por valor e HMAC-SHA-256 com
`HMAC_SECRET` (prefixo `cpf:`) já cumprem FR-020 (cifra autenticada + resumo com chave secreta; chaves do ambiente,
nunca do banco ou do repositório). O que o plano acrescenta: (a) as cópias de segurança do banco **não** incluem as
chaves (procedimento no quickstart); (b) o comando `rotate-cpf-key` (`contracts/initial-load.md` §5), que decifra e recifra em
memória, em lotes e em transação, sem nunca emitir o CPF; (c) a varredura automática de CPF em canário (SC-004, R-25);
(d) o CPF **nunca** em URL, parâmetro, cache, histórico, relatório de erro, cabeçalho ou corpo de requisição
registrado (FR-039): `spring.mvc.log-request-details=false`, nenhum filtro de log de corpo, `Cache-Control: no-store`,
campo sem autopreenchimento, e o Caddy não registra corpo.

**Justificativa**: o sistema já protege o CPF como a spec exige; o risco aberto era operacional (rotação) e de
vazamento lateral (log, cache), cobertos por procedimento e teste.

**Alternativas descartadas**: cifrar com chave por aluno (cofre de chaves fora de escopo, Princípio VII); hash sem chave
(revertível por enumeração); armazenar os últimos dígitos "para conferência" (dado a mais, proibido pelo FR-020).

## R-13 — CPF duplicado sem vazar dado de outro professor

**Decisão**: a unicidade é garantida pelo banco (`UNIQUE(cpf_hash)`) e usada por `StudentRepository.insertIfAbsent`
(`INSERT … ON CONFLICT (cpf_hash) DO NOTHING`, devolve se inseriu): sob concorrência, uma inserção vale e a outra recebe a
recusa de duplicidade sem aborto da transação (FR-041). A mesma ideia vale para o e-mail de professor e de administrador.
A recusa depende do ator: **administrador** → `DUPLICATE_CPF` com `details.name` (nome do aluno existente, nunca o CPF);
**professor** → verifica a associação: se o aluno existente é dele, `STUDENT_ALREADY_YOURS`; senão `CPF_UNAVAILABLE`
(mensagem neutra, sem nome, sem professor, sem situação), grava autoria `DENIED`/`CPF_UNAVAILABLE`, e conta no limite
de sondagem (R-06). A edição de CPF pelo professor segue exatamente o mesmo caminho (FR-024).

**Justificativa**: o sinal residual ("este CPF não pode ser usado") é inevitável e aceito na spec; o plano o limita
(5/15 min), o torna detectável (autoria por recusa) e garante que a resposta não carregue nenhum dado adicional.

**Alternativas descartadas**: responder sucesso falso (cria aluno fantasma e confunde o professor); respostas diferentes
para "alheio" e "inexistente" (enumeração); associar automaticamente o professor ao aluno existente (concede acesso a dado
de terceiro).

## R-14 — Escopo do professor e respostas indistinguíveis

**Decisão**: o professor nunca consulta "aluno por id" e depois verifica: ele usa `findVisible(actor, studentId)`, uma
única consulta indexada `student JOIN assignment WHERE student.id=? AND assignment.teacher_id=?`. Aluno alheio, sem
professor ou inexistente produzem o **mesmo** vazio e o mesmo caminho de código (`RESOURCE_NOT_FOUND`, mesmo corpo, mesmos
cabeçalhos, uma consulta em ambos, sem trabalho extra no ramo "existe"). Em `/my-students` não há 403
`STUDENT_NOT_ASSIGNED`. Um teste mede ~200 chamadas de cada caso e exige a mesma ordem de grandeza (razão das medianas
< 2 e nenhuma diferença estatística grosseira), além de comparar corpo e cabeçalhos byte a byte (SC-006). No Telegram, o
`callback_data` nunca leva id de aluno (leva a posição na lista persistida) e o texto de "não encontrado" é o mesmo.
A associação de leitura/edição vale por **toda** operação do professor sobre aluno (listar, abrir, editar, inativar,
buscar, cadastrar com CPF alheio), inclusive no núcleo das operações existentes da 001, que já usam
`AuthorizationService`/`ReportAccessService`.

**Justificativa**: FR-025 e SC-006 definem "indistinguível" por status, corpo, cabeçalhos e ordem de grandeza do tempo.

**Alternativas descartadas**: carregar e depois comparar (dois caminhos de tempo diferentes); manter o 403 da 001 também
no novo cadastro (reveleria existência).

## R-15 — Nunca ficar sem administrador ativo, sob concorrência

**Decisão**: inativar administrador (inclusive a si mesmo) executa `AdministratorRepository.lockAllActiveForUpdate()`
(`SELECT … WHERE active ORDER BY id FOR UPDATE`): se o alvo é o único restante, recusa com `LAST_ADMINISTRATOR`.
Duas inativações concorrentes serializam pelo lock; a segunda, ao reavaliar, vê um só ativo e é recusada (SC-009). A
ordem fixa por `id` evita deadlock.

**Alternativas descartadas**: contador ou trigger no banco (regra de negócio no banco, Princípio II); isolamento
`SERIALIZABLE` (aborta transações e exige retentativa em todo o sistema); confiar em checagem sem lock (race).

## R-16 — Primeiro administrador a partir do `.env`

**Decisão**: `FirstAdministratorBootstrap` (`ApplicationRunner` em `inbound/bootstrap`, ativo fora do perfil de teste) lê
`reports.administrator.bootstrap.{name,email,password}` (variáveis `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL`,
`ADMIN_BOOTSTRAP_PASSWORD`) e chama `BootstrapFirstAdministratorUseCase` com `Actor.system(SYSTEM_STARTUP)`. O caso de
uso insere de forma atômica **somente se `count(administrator) = 0`** (`insert … where not exists`), com senha provisória
(`password_provisional = true`) e autoria "sistema/inicialização do sistema". Quatro situações (tabela em
`contracts/initial-load.md` §4): cria; ignora se já existe algum administrador; sobe com aviso em português se faltam
variáveis; **recusa subir** se a senha violar o FR-004 ou o e-mail for inválido, citando só o nome da variável. A senha nunca
vai a log ou a autoria. Os valores do `.env.example` ficam vazios; `local.sh` preenche, só no ambiente local, valores
fictícios e uma senha aleatória impressa uma vez no terminal. Troca obrigatória no primeiro acesso: sessão com
`mustChangePassword`, e o `Actor` carrega `passwordChangeRequired`, o que faz o núcleo negar tudo exceto
`ADMIN_CHANGE_PASSWORD` e `ADMIN_SIGN_OUT` (a regra está no núcleo, não no filtro).

**Justificativa**: Clarifications: "somente se não existir nenhum administrador"; substitui o procedimento do mantenedor
com código de ativação.

**Alternativas descartadas**: migração Flyway com semente (segredo no repositório); comando `seed` com o administrador
(exige credencial no arquivo de carga); redefinição de senha por variável de ambiente (FR-034b proíbe).

## R-17 — Carga inicial passa a usar os mesmos casos de uso

**Decisão**: `LoadInitialRegistryUseCase` deixa de conter regra: valida o formato do arquivo e, **sem validar regra de
negócio própria**, chama `RegisterTeacherUseCase`/`UpdateTeacherUseCase`, `RegisterStudentUseCase`/`UpdateStudentUseCase` e
`AssignTeacherToStudentUseCase` com `Actor.system(INITIAL_LOAD)`, tudo em uma transação (tudo ou nada). `--simulate`
executa os mesmos casos de uso e **desfaz** a transação (`TransactionPort.runAndRollBack`): contagens e erros idênticos
aos da execução real, inclusive de unicidade. `EmitAccessCodeUseCase` é removido; `issue-code` chama
`ReissueAccessCodeUseCase`. É o único ponto em que um caso de uso orquestra outros (justificado em Complexity Tracking).

**Alternativas descartadas**: manter a regra própria da carga (duplica validação, vetado pelo FR-044); remover a carga
(a escola ainda a usa para importações em lote e para a massa de demonstração); simulação por "modo seco" em cada caso de
uso (parâmetro de teste em código de produção).

## R-18 — Contrato OpenAPI em arquivo novo

**Decisão**: `contracts/api-administration.openapi.yaml` (um arquivo, tags `Admin*` e `MyStudents`) com o mesmo
estilo do contrato da 001. O frontend gera um segundo arquivo de tipos (`administration-api-types.ts`, ignorado pelo Git)
com um segundo script `generate:administration-api`; o `generate` roda os dois. A matriz normativa é
`contracts/permission-matrix.json`. O contrato da 001 só recebe uma emenda mínima: o `401` de `POST /session` passa a
ser apenas `INVALID_CODE` (código vencido deixa de ter resposta própria, FR-042), com nota remetendo à 005.

**Justificativa**: o contrato é fonte de verdade ("mudança de API começa pelo contrato"); misturar as áreas na 001
reescreveria um histórico que a spec manda não reescrever e acoplaria o gerador de tipos.

**Alternativas descartadas**: nova seção dentro da 001; dois arquivos (administração e "Meus alunos") — os schemas
`Student*` são comuns e um terceiro arquivo de componentes só adicionaria indireção.

## R-19 — Rotas separadas, credencial cruzada = rota inexistente

**Decisão**: duas `SecurityFilterChain` ordenadas por `securityMatcher`: `/api/v1/admin/**` (cookie `admin_session`,
`AdminSessionFilter`, principal `AuthenticatedAdministrator`) e a existente do professor (`/api/**` exceto admin). Cada
cadeia só lê o seu cookie. Se uma requisição chega **com o cookie do outro papel e sem credencial válida do seu**,
a resposta é a de rota inexistente: `404 RESOURCE_NOT_FOUND` com o mesmo corpo e os mesmos cabeçalhos do 404 padrão (teste
byte a byte contra `GET /api/v1/__nope__`). Sem nenhum cookie: `401 NOT_AUTHENTICATED`. O cookie do administrador tem
`Path=/api/v1/admin`, então o navegador nem o envia às rotas do professor; o cookie do professor (`Path=/`) chega às
rotas administrativas e é ignorado/tratado como acima. CSRF: o mesmo cookie `XSRF-TOKEN`/cabeçalho `X-CSRF-TOKEN` da 001
(token não é credencial), exigido também em `POST /admin/session` e `/admin/activation`, que é o que impede *login
CSRF*.

**Alternativas descartadas**: papéis do Spring (`hasRole`) com 403 (revela que a rota existe); um só cookie com
papel (mistura credenciais, viola FR-003); mesmo `Path=/` para o cookie do administrador (enviaria a credencial de
poder total a toda rota).

## R-20 — Telas do portal

**Decisão** (detalhes em `contracts/portal-screens.md`): mesma SPA, `AdminLayout` e `AdminSessionGuard` próprios,
carregados por *lazy route* (o pacote do professor não carrega o código administrativo), tokens e componentes existentes,
mobile-first a partir de 390 px, tabelas viram cartões abaixo de 640 px, modo claro/escuro pelos tokens, estados
completos e `OneTimeSecretPanel` para códigos. Sessão fixa de 2 h: relógio visível e aviso 5 min antes (a exceção de tempo
essencial do WCAG 2.2.1 cobre a expiração de segurança; o aviso prévio é a mitigação). Seção "Responsável" sempre visível
no formulário do professor (a regra "menor de 18" é do servidor; o frontend não calcula idade).

**Alternativas descartadas**: SPA/bundle separado para a área administrativa (dois deploys, dois pipelines de tipos, sem
benefício de isolamento real — o isolamento é no servidor); biblioteca de componentes de terceiros (quebra a identidade
visual); calcular "menor de 18" no frontend (regra de negócio fora do backend); medidor de força de senha (a spec dispensa
regra de composição).

## R-21 — Fluxo do bot: conversa guiada, rascunho persistido e "apagar mensagem"

**Decisão** (detalhes em `contracts/telegram-student-registry.md`): o cadastro e a edição pelo Telegram são uma conversa
guiada; os valores já validados ficam em `student_registration_draft` (tabela própria, 1 por vínculo,
`ON DELETE CASCADE`, CPF só cifrado, prazo de 30 min), **não** no `flow_data` genérico do vínculo. Cada resposta passa por
`CollectStudentRegistrationFieldUseCase` (mesmos validadores da Web); o resumo (sem CPF) traz botões Confirmar/Cancelar com
**token de uso único**; `ConfirmStudentRegistrationUseCase` consome o token e chama `RegisterStudentUseCase` /
`UpdateStudentUseCase` na mesma transação. O CPF digitado é seguido de uma ação nova `DELETE_MESSAGE` (acréscimo
compatível ao `actions-v1`; o n8n só executa `deleteMessage`; falha → compensação com aviso ao professor). Negativa
neutra = a resposta de texto não reconhecido + menu do professor.

**Justificativa**: persistir o rascunho em tabela própria permite prazo e varredura independentes, mantém o CPF fora de
JSON genérico, e a cascata do vínculo cumpre "descartar na hora em que o vínculo termina, por qualquer causa" sem depender
de cada caso de uso lembrar. O token garante confirmação única e só do dono (FR-009).

**Alternativas descartadas**: guardar o rascunho em `TelegramConversation.data` (CPF e dados do responsável em JSON genérico
sem prazo próprio); Telegram Mini App/Web App com formulário (exige superfície HTTPS extra e JS, e é fora do escopo da 004);
pedir tudo numa única mensagem de texto (frágil, sem validação campo a campo); apagar a mensagem pelo próprio backend
(Princípio II: o backend nunca chama a API de bots, só o transporte); não apagar e apenas avisar (a spec manda tentar apagar).

## R-22 — Busca e paginação em memória

**Decisão**: a lista administrativa carrega os alunos (ou os do professor) e filtra/ordena/pagina no núcleo com a mesma
normalização de `StudentSearch.normalize` (minúsculas, sem acentos) que a busca existente já usa. Escala-alvo: uma escola,
centenas de alunos; reavaliar acima de ~5 000 (então `unaccent` + índice trigram).

**Alternativas descartadas**: `unaccent`/`pg_trgm` desde já (extensão, migração e ganho nulo na escala); busca por CPF
(proibida pelo FR-021).

## R-23 — Inativar sem bloquear; "congelar" sem estado novo

**Decisão** (D4/D5): nenhum estado novo de relatório. O **congelamento** é consequência do escopo: o acesso a relatório já
exige associação ativa e professor ativo (`ReportAccessService`), então retirar a associação ou inativar o professor
esconde o relatório sem alterá-lo, e reassociar/reativar o devolve. Ajustes necessários no que já existe:
`OpenReportsQueryPort` e a home passam a filtrar por associação e professor ativo; `StudentRepository.listByTeacher` ganha o
filtro de situação (padrão ativos) para as listas e a escolha de aluno para aula; `RegisterLessonByAudioUseCase` recusa
aluno inativo (`STUDENT_INACTIVE`) e a geração semestral idem; trabalhos em processamento de professor inativado podem
terminar, mas `TeacherNotificationPort` não encontra vínculo e nada é enviado. `RemovalPreview` conta os relatórios em
aberto do professor com o aluno (consulta de leitura; o administrador não vê conteúdo).

**Alternativas descartadas**: bloquear inativação/retirada com relatório em aberto (vetado por D4/D5); estado
`FROZEN` no relatório (altera o ciclo de vida do Princípio III sem necessidade); cancelar automaticamente os relatórios
(o administrador não toca em relatório).

## R-24 — Paridade Web × Telegram e desvios

**Decisão**: nenhum **desvio** de paridade do Professor. A tabela da seção 7 de `contracts/telegram-student-registry.md`
mostra os cinco casos de uso comuns. As diferenças são de **controle**, já declaradas na spec como intencionais:
confirmação em toda gravação do Telegram (sem formulário de revisão), vínculo sem expiração por inatividade, bloqueio por
conta (Telegram) × por origem (Web). As funções do administrador são somente Web **por regra** do Princípio I v2.2.0
(não é desvio). Entrega em um canal só seria desvio; todas as operações do professor entregam nos dois.

**Alternativas descartadas** (registradas a pedido): (a) administração também no Telegram — R-09; (b) professor sem
cadastro próprio de alunos (tudo pelo administrador) — vetado pela spec (gargalo da secretaria, Story 4) e deixaria o
professor dependente de terceiro para o dia a dia; (c) cadastro do professor só na Web — quebraria a paridade do
Princípio I e o SC-002 (3 minutos em cada canal); (d) associação de professores feita pelo próprio professor — quebraria o
isolamento (FR-027).

## R-25 — Logs e prova de ausência de CPF

**Decisão**: logs em português, só com identificadores e códigos (nunca nome, data, CPF, telefone, e-mail do responsável,
senha, código). O `SensitiveDataMaskingConverter` existente ganha padrões de CPF (com e sem máscara) como rede de
segurança, **sem** substituir a disciplina. SC-004 vira teste: um **CPF canário** (válido, fictício) e um **aluno canário** (nome, nascimento e contato do responsável, fictícios) atravessam os fluxos
de Web, Telegram e carga com um apêndice de log capturando tudo e um varredor que procura o canário (e variações de
máscara) em logs, corpos de resposta, saídas do bot, linhas de `authorship_record`, `channel_action.payload` e mensagens
de exceção; o conjunto de testes inteiro falha se o canário aparecer.

**Alternativas descartadas**: confiar só no mascaramento (mascara depois de vazar); revisão manual (não escala).

## R-26 — Execução em paralelo por subagentes

**Decisão**: o plano define uma **fase de fundação sequencial** (esqueleto compilável com os tipos, portas, assinaturas
dos casos de uso, `ErrorCode`, catálogo, propriedades, glossário, contratos e arquivos de configuração por fluxo) e, depois,
**fluxos de trabalho de propriedade disjunta de arquivos** (tabela no plano) que podem rodar ao mesmo tempo, cada um em
worktree própria, contra as assinaturas congeladas. Arquivos que todos tocam (`UseCaseConfig`, `ErrorCode`,
`messages_pt_BR.properties`, `ApplicationProperties`, `glossary.json`, `routes.tsx`, `ErrorHandlerController`) são
**editados uma vez na fundação**; a fiação de beans é dividida em classes `*UseCaseConfig` por fluxo; cada fluxo tem a sua
migração (`V9` a `V14`) e os seus dublês de teste.

**Alternativas descartadas**: implementar em série (mais lento e sem ganho de qualidade); paralelizar sem fundação
(conflitos em `UseCaseConfig`, `ErrorCode` e catálogo, e retrabalho por assinaturas que mudam); um subagente por arquivo
(granularidade que gera dependências cruzadas).
