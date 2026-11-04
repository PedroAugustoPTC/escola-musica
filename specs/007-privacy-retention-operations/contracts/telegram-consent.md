# Contrato: consentimento e bloqueio no Telegram (FR-030, FR-031, FR-034, FR-081)

Emenda o cadastro guiado de [`telegram-student-registry.md`](../../005-admin-registrations-access/contracts/telegram-student-registry.md)
da 005 (que recebe uma `> **Nota (feature 007)**` apontando para cá). O n8n não muda: tudo é resposta do backend em
ações da outbox. Mesmos casos de uso da Web (`RegisterStudentUseCase`, `UpdateStudentUseCase`, `RecordConsentUseCase`)
com o mesmo `ConsentInput`.

## Passos novos no cadastro e na edição

Entram **depois** do passo da data de nascimento, **só quando** o aluno é menor de 18 anos na data de hoje (fuso da
escola). Para adulto, o fluxo da 005 segue igual.

| # | Pergunta do bot (texto do catálogo, em português) | Resposta aceita | Erro |
|---|---|---|---|
| C1 | Apresenta a versão vigente do termo (número e data), um resumo, o link da página do termo no portal e o contato do responsável pela LGPD; pergunta se o responsável consentiu | botões `consent:yes` / `consent:no` | `consent:no` → o cadastro **não é concluído**: mensagem com orientação e o rascunho é descartado (igual ao cancelar da 005) |
| C2 | Nome de quem consentiu | texto, `PersonName` | `VALIDATION_FAILED`, repete a pergunta |
| C3 | Relação com o aluno | botões `consent-rel:MOTHER`, `FATHER`, `LEGAL_GUARDIAN`, `OTHER` | — |
| C4 | Data do consentimento | `dd/mm/aaaa` ou botão `consent-date:today` | data futura ou inválida → repete |
| C5 | Forma de coleta | botões `consent-method:SIGNED_PAPER`, `DIGITAL`, `OTHER` | — |

A versão do termo é a **vigente**, fixada no rascunho no passo C1 (se outra versão for publicada no meio do cadastro, a
confirmação recusa com `VALIDATION_FAILED` em `consent.termVersion` e o bot reapresenta C1). O resumo final da 005
mostra também "Consentimento: registrado (termo v{n}, {data})" — sem repetir o nome de quem consentiu. Os dados do
consentimento ficam no rascunho existente (`student_registration_draft`), que já expira em 30 min.

Ações de botão em inglês (Princípio VI); textos em português, no catálogo `messages_pt_BR.properties`
(`telegram.consent.*`).

## Consentimento fora do cadastro

A mensagem de bloqueio (abaixo) traz o botão `consent:record:{studentId}`, que abre os passos C2–C5 (com C1 só para
mostrar o termo) e chama `RecordConsentUseCase` para o próprio aluno do professor (escopo da 005).

## Bloqueio por falta de consentimento (FR-034)

Quando o professor envia áudio, responde perguntas, pede alteração, tenta de novo ou gera semestral de aluno menor sem
consentimento válido:

- o backend responde `CONSENT_REQUIRED` **antes** de qualquer chamada de IA; o áudio é descartado (não é baixado do
  Telegram se o bloqueio for decidido antes do download; se já foi baixado, o arquivo temporário é apagado);
- o bot envia o texto `telegram.consent.blocked` (mesmo conteúdo do aviso da Web): o aluno não pode ter relatório gerado
  por IA até o consentimento do responsável ser registrado; botão para registrar agora e orientação para procurar a
  secretaria;
- aprovar e cancelar relatório já existente continuam funcionando.

O texto não cita a idade, a data de nascimento nem o motivo (revogação ou termo novo), para não expor dado do aluno no
chat além do nome que o professor já vê.

## Paridade (teste `parity/`)

Para cada passo e erro acima, a Web (`MyStudentFormScreen`, aviso na gravação) produz o mesmo resultado de negócio pelo
mesmo caso de uso: mesmo código de erro, mesma gravação, mesmo evento na trilha (com `origin` diferente).
