package br.com.felipeblaft.reports.domain.telegram;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de aluno em andamento no Telegram (FR-036, data-model.md §3.5): estado de conversa, não
 * trabalho de negócio. Nada é gravado como aluno antes da confirmação; vence em 30 minutos sem
 * atividade e some com o vínculo. O CPF existe só cifrado e como resumo. Esqueleto da Fundação com
 * o estado; o comportamento (coleta campo a campo, token de uso único, prazo) é do fluxo W-D. O
 * {@code toString} não revela dado pessoal.
 *
 * @param studentId obrigatório em {@code UPDATE}, {@code INACTIVATE} e {@code REACTIVATE}
 * @param providedFields campos já informados; vazio em {@code INACTIVATE} e {@code REACTIVATE}
 * @param confirmationToken gerado a cada resumo mostrado; uso único
 */
public record StudentRegistrationDraft(
        UUID linkId,
        Kind kind,
        UUID studentId,
        Set<Field> providedFields,
        String name,
        String instrument,
        LocalDate birthDate,
        byte[] cpfEncrypted,
        String cpfHash,
        String guardianName,
        String guardianPhone,
        String guardianEmail,
        String confirmationToken,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    /** Tipo do cadastro em andamento. */
    public enum Kind {
        CREATE,
        UPDATE,
        INACTIVATE,
        REACTIVATE
    }

    /** Campos coletados, na ordem em que o bot pergunta. */
    public enum Field {
        NAME,
        BIRTH_DATE,
        CPF,
        GUARDIAN,
        INSTRUMENT
    }

    public StudentRegistrationDraft {
        Objects.requireNonNull(linkId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(providedFields);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        Objects.requireNonNull(expiresAt);
        providedFields = Set.copyOf(providedFields);
    }

    @Override
    public String toString() {
        return "StudentRegistrationDraft[linkId=" + linkId + "]";
    }
}
