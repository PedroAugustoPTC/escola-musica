package br.com.felipeblaft.reports.domain.telegram;

import br.com.felipeblaft.reports.domain.registry.Student;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de aluno em andamento no Telegram (FR-036, data-model.md §3.5): estado de conversa, não
 * trabalho de negócio. Nada é gravado como aluno antes da confirmação; vence após o prazo de
 * inatividade (cada atividade renova o prazo) e some com o vínculo. O CPF existe só cifrado e como
 * resumo. O objeto é imutável: cada campo informado devolve um rascunho novo, e qualquer mudança
 * invalida o token de confirmação anterior. O {@code toString} não revela dado pessoal.
 *
 * @param studentId obrigatório em {@code UPDATE}, {@code INACTIVATE} e {@code REACTIVATE}
 * @param providedFields campos já informados; vazio em {@code INACTIVATE} e {@code REACTIVATE}. O
 *     campo {@link Field#GUARDIAN} só consta quando nome, telefone e e-mail do responsável estão
 *     todos informados
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
        Instant expiresAt,
        ConsentDraft consent) {

    /** Alfabeto do token: sem caracteres ambíguos e seguro para o {@code callback_data}. */
    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int TOKEN_SIZE = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Tipo do cadastro em andamento. */
    public enum Kind {
        CREATE,
        UPDATE,
        INACTIVATE,
        REACTIVATE,
        /** Só o consentimento do responsável de um aluno existente (aviso de bloqueio, FR-034). */
        CONSENT;

        /** Os tipos que operam sobre um aluno já existente. */
        public boolean targetsExistingStudent() {
            return this != CREATE;
        }

        /** Os tipos que só guardam o token de confirmação, sem campos. */
        public boolean confirmationOnly() {
            return this == INACTIVATE || this == REACTIVATE;
        }
    }

    /** Campos coletados, na ordem em que o bot pergunta. */
    public enum Field {
        NAME,
        BIRTH_DATE,
        CPF,
        GUARDIAN,
        CONSENT,
        INSTRUMENT
    }

    /** Partes do responsável, perguntadas uma a uma nesta ordem. */
    public enum GuardianPart {
        NAME,
        PHONE,
        EMAIL
    }

    /** Sem consentimento em coleta (cadastros anteriores à feature 007 e os de adulto). */
    public StudentRegistrationDraft(
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
        this(
                linkId,
                kind,
                studentId,
                providedFields,
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                guardianEmail,
                confirmationToken,
                createdAt,
                updatedAt,
                expiresAt,
                ConsentDraft.EMPTY);
    }

    public StudentRegistrationDraft {
        Objects.requireNonNull(linkId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(providedFields);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        Objects.requireNonNull(expiresAt);
        providedFields = Set.copyOf(providedFields);
        consent = consent == null ? ConsentDraft.EMPTY : consent;
        cpfEncrypted = cpfEncrypted == null ? null : cpfEncrypted.clone();
    }

    /**
     * Abre um cadastro em andamento vazio. {@code CREATE} não tem aluno; os demais tipos exigem o
     * aluno. O prazo de inatividade corre a partir de {@code now}.
     */
    public static StudentRegistrationDraft start(
            UUID linkId, Kind kind, UUID studentId, Instant now, Duration validity) {
        Objects.requireNonNull(kind);
        if (kind.targetsExistingStudent() == (studentId == null)) {
            throw new IllegalArgumentException("aluno incompatível com o tipo do cadastro");
        }
        return new StudentRegistrationDraft(
                linkId,
                kind,
                studentId,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                now.plus(validity),
                ConsentDraft.EMPTY);
    }

    /** Novo token de confirmação: 8 caracteres aleatórios, de uso único. */
    public static String newConfirmationToken() {
        StringBuilder token = new StringBuilder(TOKEN_SIZE);
        for (int i = 0; i < TOKEN_SIZE; i++) {
            token.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return token.toString();
    }

    /** O prazo de inatividade acabou (no instante do vencimento já está vencido). */
    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Compara o token em tempo constante; rascunho sem token nunca confere. */
    public boolean matchesToken(String candidate) {
        if (confirmationToken == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                confirmationToken.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    /** O aluno é menor de 18 anos em {@code today} (data de hoje no fuso da escola). */
    public boolean guardianRequired(LocalDate today) {
        return birthDate != null && Student.requiresGuardian(birthDate, today);
    }

    /**
     * Próximo campo a perguntar, ou {@code null} quando o cadastro está completo. No {@code
     * CREATE}: nome, nascimento, CPF, responsável (só se menor) e instrumento. No {@code UPDATE} só
     * se pergunta o que falta por exigência: o responsável, quando a data informada torna o aluno
     * menor. Nos tipos de situação não há campo.
     */
    public Field nextField(LocalDate today) {
        if (kind.confirmationOnly()) {
            return null;
        }
        if (kind == Kind.CONSENT) {
            return providedFields.contains(Field.CONSENT) ? null : Field.CONSENT;
        }
        if (kind == Kind.UPDATE) {
            boolean guardianMissing =
                    guardianRequired(today) && !providedFields.contains(Field.GUARDIAN);
            if (guardianMissing) {
                return Field.GUARDIAN;
            }
            return consentMissing(today) ? Field.CONSENT : null;
        }
        for (Field field : Field.values()) {
            if (field == Field.GUARDIAN && !guardianRequired(today)) {
                continue;
            }
            if (field == Field.CONSENT && !guardianRequired(today)) {
                continue;
            }
            if (!providedFields.contains(field)) {
                return field;
            }
        }
        return null;
    }

    /** Pronto para o resumo: nada falta e (no {@code UPDATE}) algo foi informado. */
    public boolean readyToConfirm(LocalDate today) {
        if (kind.confirmationOnly()) {
            return true;
        }
        return nextField(today) == null && (kind == Kind.CREATE || !providedFields.isEmpty());
    }

    /** Parte do responsável a perguntar agora, ou {@code null} quando as três estão informadas. */
    public GuardianPart nextGuardianPart() {
        if (guardianName == null) {
            return GuardianPart.NAME;
        }
        if (guardianPhone == null) {
            return GuardianPart.PHONE;
        }
        return guardianEmail == null ? GuardianPart.EMAIL : null;
    }

    public StudentRegistrationDraft withName(String value, Instant now, Duration validity) {
        return next(
                provided(Field.NAME),
                value,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                guardianEmail,
                now,
                validity);
    }

    public StudentRegistrationDraft withBirthDate(LocalDate value, Instant now, Duration validity) {
        return next(
                provided(Field.BIRTH_DATE),
                name,
                instrument,
                value,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                guardianEmail,
                now,
                validity);
    }

    /** O CPF entra só cifrado e como resumo com chave; o valor em claro nunca chega aqui. */
    public StudentRegistrationDraft withCpf(
            byte[] encrypted, String hash, Instant now, Duration validity) {
        return next(
                provided(Field.CPF),
                name,
                instrument,
                birthDate,
                Objects.requireNonNull(encrypted),
                Objects.requireNonNull(hash),
                guardianName,
                guardianPhone,
                guardianEmail,
                now,
                validity);
    }

    /** Instrumento é opcional: {@code null} registra que o professor pulou a pergunta. */
    public StudentRegistrationDraft withInstrument(String value, Instant now, Duration validity) {
        return next(
                provided(Field.INSTRUMENT),
                name,
                value,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                guardianEmail,
                now,
                validity);
    }

    /** Recomeça o responsável pelo nome: telefone e e-mail anteriores são descartados. */
    public StudentRegistrationDraft withGuardianName(String value, Instant now, Duration validity) {
        return next(
                without(Field.GUARDIAN),
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                value,
                null,
                null,
                now,
                validity);
    }

    public StudentRegistrationDraft withGuardianPhone(
            String value, Instant now, Duration validity) {
        return next(
                without(Field.GUARDIAN),
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                value,
                guardianEmail,
                now,
                validity);
    }

    /** Com o e-mail, o responsável fica completo e o campo {@code GUARDIAN} passa a informado. */
    public StudentRegistrationDraft withGuardianEmail(
            String value, Instant now, Duration validity) {
        boolean complete = guardianName != null && guardianPhone != null;
        return next(
                complete ? provided(Field.GUARDIAN) : without(Field.GUARDIAN),
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                value,
                now,
                validity);
    }

    /** Descarta o responsável por inteiro (o aluno informado é adulto). */
    public StudentRegistrationDraft withoutGuardian(Instant now, Duration validity) {
        if (guardianName == null
                && guardianPhone == null
                && guardianEmail == null
                && !providedFields.contains(Field.GUARDIAN)) {
            return this;
        }
        return next(
                without(Field.GUARDIAN),
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                null,
                null,
                null,
                now,
                validity);
    }

    /** O consentimento do responsável é exigido (menor) e ainda não foi coletado por inteiro. */
    private boolean consentMissing(LocalDate today) {
        return guardianRequired(today) && !providedFields.contains(Field.CONSENT);
    }

    /** Grava o consentimento em coleta; o campo {@code CONSENT} só consta com os cinco passos. */
    public StudentRegistrationDraft withConsent(
            ConsentDraft value, Instant now, Duration validity) {
        Set<Field> fields = value.nextPart() == null ? provided(Field.CONSENT) : without(Field.CONSENT);
        return new StudentRegistrationDraft(
                linkId,
                kind,
                studentId,
                fields,
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                guardianEmail,
                null,
                createdAt,
                now,
                now.plus(validity),
                value);
    }

    /** Descarta o consentimento (o aluno informado é adulto, ou o professor recomeça). */
    public StudentRegistrationDraft withoutConsent(Instant now, Duration validity) {
        if (consent.equals(ConsentDraft.EMPTY) && !providedFields.contains(Field.CONSENT)) {
            return this;
        }
        return withConsent(ConsentDraft.EMPTY, now, validity);
    }

    /** Guarda o token do resumo mostrado agora; o anterior deixa de valer. */
    public StudentRegistrationDraft withConfirmationToken(
            String token, Instant now, Duration validity) {
        return new StudentRegistrationDraft(
                linkId,
                kind,
                studentId,
                providedFields,
                name,
                instrument,
                birthDate,
                cpfEncrypted,
                cpfHash,
                guardianName,
                guardianPhone,
                guardianEmail,
                Objects.requireNonNull(token),
                createdAt,
                now,
                now.plus(validity),
                consent);
    }

    private StudentRegistrationDraft next(
            Set<Field> fields,
            String newName,
            String newInstrument,
            LocalDate newBirthDate,
            byte[] newCpfEncrypted,
            String newCpfHash,
            String newGuardianName,
            String newGuardianPhone,
            String newGuardianEmail,
            Instant now,
            Duration validity) {
        return new StudentRegistrationDraft(
                linkId,
                kind,
                studentId,
                fields,
                newName,
                newInstrument,
                newBirthDate,
                newCpfEncrypted,
                newCpfHash,
                newGuardianName,
                newGuardianPhone,
                newGuardianEmail,
                null,
                createdAt,
                now,
                now.plus(validity),
                consent);
    }

    private Set<Field> provided(Field field) {
        Set<Field> fields = EnumSet.noneOf(Field.class);
        fields.addAll(providedFields);
        fields.add(field);
        return fields;
    }

    private Set<Field> without(Field field) {
        Set<Field> fields = EnumSet.noneOf(Field.class);
        fields.addAll(providedFields);
        fields.remove(field);
        return fields;
    }

    /** Cópia: o vetor interno não escapa. */
    @Override
    public byte[] cpfEncrypted() {
        return cpfEncrypted == null ? null : cpfEncrypted.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof StudentRegistrationDraft that
                && Objects.equals(linkId, that.linkId)
                && kind == that.kind
                && Objects.equals(studentId, that.studentId)
                && Objects.equals(providedFields, that.providedFields)
                && Objects.equals(name, that.name)
                && Objects.equals(instrument, that.instrument)
                && Objects.equals(birthDate, that.birthDate)
                && Arrays.equals(cpfEncrypted, that.cpfEncrypted)
                && Objects.equals(cpfHash, that.cpfHash)
                && Objects.equals(guardianName, that.guardianName)
                && Objects.equals(guardianPhone, that.guardianPhone)
                && Objects.equals(guardianEmail, that.guardianEmail)
                && Objects.equals(confirmationToken, that.confirmationToken)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(consent, that.consent);
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(
                        linkId,
                        kind,
                        studentId,
                        providedFields,
                        name,
                        instrument,
                        birthDate,
                        cpfHash,
                        guardianName,
                        guardianPhone,
                        guardianEmail,
                        confirmationToken,
                        createdAt,
                        updatedAt,
                        expiresAt,
                        consent);
        return 31 * result + Arrays.hashCode(cpfEncrypted);
    }

    @Override
    public String toString() {
        return "StudentRegistrationDraft[linkId=" + linkId + "]";
    }
}
