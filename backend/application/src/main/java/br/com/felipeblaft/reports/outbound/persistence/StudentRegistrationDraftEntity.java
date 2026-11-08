package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cadastro de aluno em andamento no Telegram (V12). O CPF existe aqui só cifrado e como resumo com
 * chave; o {@code toString} não revela dado algum.
 */
@Entity
@Table(name = "student_registration_draft")
public class StudentRegistrationDraftEntity {

    @Id
    @Column(name = "link_id")
    UUID linkId;

    @Column(nullable = false)
    String kind;

    @Column(name = "student_id")
    UUID studentId;

    @Column(name = "provided_fields", nullable = false)
    String providedFields;

    String name;

    String instrument;

    @Column(name = "birth_date")
    LocalDate birthDate;

    @Column(name = "cpf_encrypted")
    byte[] cpfEncrypted;

    @Column(name = "cpf_hash")
    String cpfHash;

    @Column(name = "guardian_name")
    String guardianName;

    @Column(name = "guardian_phone")
    String guardianPhone;

    @Column(name = "guardian_email")
    String guardianEmail;

    /** Consentimento do responsável em coleta (V17), JSON sem o nome em log. */
    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String consent;

    @Column(name = "confirmation_token")
    String confirmationToken;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    protected StudentRegistrationDraftEntity() {}

    @Override
    public String toString() {
        return "StudentRegistrationDraftEntity[linkId=" + linkId + "]";
    }
}
