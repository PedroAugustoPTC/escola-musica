# Contrato: códigos de erro e catálogo de mensagens da feature 005

**Feature**: `005-admin-registrations-access` | Referências: FR-042, FR-049, Princípio VI;
`specs/001-fluxo-relatorios/contracts/mensagens.md` (formato e regras do catálogo).

O núcleo devolve **código** e **dados estruturados**, nunca texto (R-18 da 001). O texto vem do catálogo único
`backend/application/src/main/resources/messages/messages_pt_BR.properties`, usado pelos dois canais; o frontend gera
`messages.json` a partir dele. O teste do catálogo reprova `ErrorCode` sem texto. Chaves e códigos em inglês; textos em
português acentuado. Esta tabela é a fonte do que a fundação (fase F) grava no catálogo e em `ErrorCode`.

## 1. Classes do FR-049 → códigos

| Classe | Códigos | HTTP |
|---|---|---|
| (a) validação | `VALIDATION_FAILED` + `details.fields[]` com `INVALID_NAME`, `INVALID_EMAIL`, `INVALID_CPF`, `INVALID_BIRTH_DATE`, `INVALID_PHONE`, `INVALID_INSTRUMENT`, `INVALID_TEXT`, `GUARDIAN_REQUIRED`, `PASSWORD_LENGTH`, `PASSWORD_EQUALS_IDENTITY`, `PASSWORD_SAME_AS_PROVISIONAL` | 400 |
| (b) sessão / não autenticado | `NOT_AUTHENTICATED`, `SESSION_EXPIRED`, `ACCESS_ENDED`, `INVALID_CREDENTIALS`, `INVALID_ACTIVATION` | 401 |
| (c) negado / inexistente | `RESOURCE_NOT_FOUND` (também credencial do outro papel); `PASSWORD_CHANGE_REQUIRED` | 404; 403 |
| (d) duplicidade | `DUPLICATE_EMAIL`, `DUPLICATE_CPF` (administrador, com `details.name`); `CPF_UNAVAILABLE`, `STUDENT_ALREADY_YOURS` (professor) | 409 |
| (e) bloqueio | `TEMPORARILY_BLOCKED` (entrada, ativação, vínculo); `OPERATION_TEMPORARILY_BLOCKED` (sondagem de CPF) | 429 |
| (f) confirmação vencida | `CONFIRMATION_EXPIRED`, `CONFIRMATION_ALREADY_DONE`, `CALLBACK_EXPIRED` | Telegram |
| (g) falha parcial | `OPERATION_NOT_SAVED` | 500 |
| (h) falha interna | `INTERNAL_ERROR` + `reference` | 500 |
| regras de negócio | `LAST_ADMINISTRATOR`, `TEACHER_INACTIVE`, `STUDENT_INACTIVE`, `SHARED_STUDENT_ACTION_DENIED`, `PASSWORD_CHANGE_NOT_REQUIRED` | 409, 409, 409, 403, 409 |

Mudanças em códigos existentes: `EXPIRED_CODE` **deixa de existir** (código vencido passa a `INVALID_CODE`, FR-042);
`STUDENT_NOT_ASSIGNED` (403) **deixa de existir** (aluno não associado passa a `RESOURCE_NOT_FOUND`, 404, a mesma resposta de
aluno inexistente, FR-025 e FR-050; no Telegram, o texto de "não encontramos");
`DUPLICATE_EMAIL_IN_FILE`, `DUPLICATE_CPF_IN_FILE` e os códigos de campo da carga seguem na carga inicial.

## 2. Textos novos (erros)

```properties
# --- Acesso do administrador
INVALID_CREDENTIALS=E-mail ou senha incorretos.
INVALID_ACTIVATION=Código de ativação inválido ou vencido. Peça um novo a outro administrador.
ACCESS_ENDED=Seu acesso foi encerrado. Procure o administrador.
PASSWORD_CHANGE_REQUIRED=Defina uma nova senha para continuar.
PASSWORD_CHANGE_NOT_REQUIRED=Sua senha já foi definida.
PASSWORD_LENGTH=A senha deve ter de 12 a 128 caracteres.
PASSWORD_EQUALS_IDENTITY=A senha não pode ser igual ao seu e-mail ou ao seu nome.
PASSWORD_SAME_AS_PROVISIONAL=A nova senha deve ser diferente da senha provisória.
LAST_ADMINISTRATOR=A escola precisa ter pelo menos um administrador ativo.

# --- Validação (campo + motivo; nunca repete o dado)
VALIDATION_FAILED=Confira os campos indicados e tente de novo.
INVALID_NAME=O nome deve ter de 2 a 120 caracteres.
INVALID_EMAIL=Informe um e-mail válido, com até 254 caracteres.
INVALID_CPF=O CPF informado não é válido.
INVALID_BIRTH_DATE=Informe uma data de nascimento real, que não seja futura nem de mais de 120 anos atrás.
INVALID_PHONE=Informe um telefone com DDD, com 10 ou 11 dígitos.
INVALID_INSTRUMENT=O instrumento deve ter até 60 caracteres.
INVALID_TEXT=O texto contém caracteres que não são aceitos.
GUARDIAN_REQUIRED=Para alunos menores de 18 anos, informe nome, telefone e e-mail do responsável.

# --- Duplicidade e escopo
DUPLICATE_EMAIL=Já existe um cadastro com este e-mail: {name}.
DUPLICATE_CPF=Este CPF já pertence ao aluno {name}.
CPF_UNAVAILABLE=Não foi possível usar este CPF. Procure o administrador.
STUDENT_ALREADY_YOURS=Este aluno já está entre os seus.
SHARED_STUDENT_ACTION_DENIED=Este aluno tem mais de um professor. Procure o administrador para inativá-lo ou reativá-lo.
OPERATION_TEMPORARILY_BLOCKED=Esta operação está indisponível no momento. Tente novamente em {minutes} minuto(s).
TEACHER_INACTIVE=Este professor está inativo. Reative-o antes.
STUDENT_INACTIVE=Este aluno está inativo e não pode receber novas aulas.

# --- Confirmação, falha
CONFIRMATION_EXPIRED=Esta confirmação venceu. Comece de novo.
CONFIRMATION_ALREADY_DONE=Essa operação já foi concluída.
CALLBACK_EXPIRED=Este botão não vale mais. Use o menu.
OPERATION_NOT_SAVED=Não foi possível concluir. Nada foi gravado; tente de novo.
INTERNAL_ERROR=Algo deu errado. Informe o código de referência {reference} ao suporte.
```

## 3. Textos que mudam (FR-042) — nenhum texto do professor cita a "secretaria"

```properties
CODE_REQUIRED=Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.
INVALID_CODE=Código inválido ou vencido. Procure o administrador.
TELEGRAM_ALREADY_LINKED=Esta conta do Telegram já está vinculada a outro professor. Procure o administrador.
TEACHER_ALREADY_LINKED=Seu cadastro já está vinculado a outra conta do Telegram. Procure o administrador.
NO_STUDENTS=Você ainda não tem alunos. Cadastre o primeiro em “Meus alunos”.
```

`EXPIRED_CODE` e `STUDENT_NOT_ASSIGNED` são removidos do catálogo. O teste do catálogo passa a reprovar a palavra "secretaria" em qualquer texto.

## 4. Textos do bot (fluxo "Meus alunos")

```properties
REGISTRY_MY_STUDENTS=Seus alunos ({status}), página {page} de {pages}. Toque em um aluno ou envie parte do nome ou do instrumento.
REGISTRY_EMPTY_FILTER=Nenhum aluno encontrado nesta lista.
REGISTRY_CARD={name}\n{age} anos · {instrument}\nSituação: {status}\nResponsável: {guardian}
REGISTRY_ASK_NAME=Qual é o nome do aluno?
REGISTRY_ASK_BIRTH_DATE=Qual é a data de nascimento? Use dia/mês/ano, por exemplo 05/03/2012.
REGISTRY_ASK_CPF=Qual é o CPF do aluno? Vou apagar a sua mensagem em seguida.
REGISTRY_ASK_GUARDIAN_NAME=O aluno tem menos de 18 anos. Qual é o nome do responsável?
REGISTRY_ASK_GUARDIAN_PHONE=Qual é o telefone do responsável, com DDD?
REGISTRY_ASK_GUARDIAN_EMAIL=Qual é o e-mail do responsável?
REGISTRY_ASK_INSTRUMENT=Qual é o instrumento? Se preferir, envie "pular".
REGISTRY_EDIT_FIELD=O que você quer editar?
REGISTRY_CONFIRM_CREATE=Confira o cadastro:\n{summary}\nConfirmar?
REGISTRY_CONFIRM_UPDATE=Confira as alterações em {name}:\n{summary}\nConfirmar?
REGISTRY_CPF_WILL_CHANGE=O CPF será substituído.
REGISTRY_SAVED=Pronto! {name} foi cadastrado(a) e está entre os seus alunos.
REGISTRY_UPDATED=Pronto! Os dados de {name} foram atualizados.
REGISTRY_CANCELLED=Cadastro cancelado. Nada foi gravado.
REGISTRY_DRAFT_EXPIRED=O cadastro ficou parado por muito tempo e foi descartado. Nada foi gravado.
CONFIRM_INACTIVATE_STUDENT=Inativar {name}? O aluno deixa de aparecer para novas aulas; nada é apagado.
CONFIRM_REACTIVATE_STUDENT=Reativar {name}?
STUDENT_INACTIVATED={name} foi inativado(a).
STUDENT_REACTIVATED={name} foi reativado(a).
CPF_MESSAGE_NOT_DELETED=Não consegui apagar a mensagem com o CPF. Apague-a você mesmo(a) para não deixá-lo no chat.
```

## 5. Textos da Web (rótulos principais; o restante segue o mesmo padrão `ADMIN_*` e `MY_STUDENTS_*`)

```properties
ADMIN_AREA_TITLE=Área administrativa
ADMIN_SIGN_IN_TITLE=Entrar na área administrativa
ADMIN_LINK_FROM_SIGN_IN=É administrador? Acesse a área administrativa.
ADMIN_HAVE_ACTIVATION_CODE=Tenho um código de ativação
ADMIN_SESSION_EXPIRES_AT=Sua sessão expira às {time}.
ADMIN_SESSION_EXPIRES_SOON=Sua sessão termina em {minutes} minuto(s). Conclua o que está fazendo e entre de novo.
ADMIN_SECRET_SHOWN_ONCE=Este código aparece uma única vez e não pode ser exibido de novo. Anote-o e entregue ao destinatário por um canal de confiança.
ADMIN_SECRET_COPIED=Código copiado.
ADMIN_SECRET_ACKNOWLEDGE=Já anotei o código
ADMIN_CONFIRM_INACTIVATE_TEACHER=Inativar {name}? O acesso dele(a) termina agora, na Web e no Telegram. Relatórios em aberto ficam preservados.
ADMIN_CONFIRM_REISSUE_CODE=Reemitir o código de {name}? O código atual deixa de valer e as sessões da Web são encerradas.
ADMIN_END_TELEGRAM_OPTION=Também encerrar o vínculo do Telegram
ADMIN_CONFIRM_UNLINK_TELEGRAM=Desvincular o Telegram de {name}? Ele(a) precisará enviar um código de acesso válido para vincular de novo.
ADMIN_CONFIRM_REMOVE_ASSIGNMENT=Retirar {teacher} de {student}? O acesso dele(a) ao aluno termina agora. {frozen} relatório(s) em aberto ficarão congelados.
ADMIN_CONFIRM_INACTIVATE_ADMINISTRATOR=Inativar {name}? As sessões dele(a) terminam agora.
ADMIN_ACTIVATION_PENDING=Ativação pendente
ADMIN_ACTIVATED_AT=Ativado em {date}
ADMIN_FORCE_PASSWORD_TITLE=Defina uma nova senha
MY_STUDENTS_TITLE=Meus alunos
MY_STUDENTS_EMPTY=Você ainda não tem alunos. Cadastre o primeiro.
MY_STUDENTS_SEARCH_EMPTY=Nenhum aluno encontrado para “{search}”.
MY_STUDENTS_GUARDIAN_SECTION=Responsável (obrigatório para menores de 18 anos)
MY_STUDENTS_CPF_KEEP=Deixe em branco para manter o CPF atual.
MY_STUDENTS_CONFIRM_INACTIVATE=Inativar {name}? O aluno deixa de aparecer para novas aulas; nada é apagado.
```

## 6. Conferências de qualidade

- Todo `ErrorCode` novo tem texto (teste do catálogo); nenhum texto menciona "secretaria"; nenhum texto interpola CPF,
  senha ou código (placeholders permitidos: `{name}`, `{minutes}`, `{reference}`, `{summary}` montado sem CPF).
- Em `DUPLICATE_*` o `{name}` só é preenchido para o administrador; para o professor o núcleo devolve `CPF_UNAVAILABLE`.
- O corpo de `RESOURCE_NOT_FOUND` é idêntico para inexistente, aluno alheio e credencial do outro papel.
