package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.telegram.ConsentDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de aluno em andamento no Telegram (CPF só cifrado). Uma linha por vínculo; a linha some
 * junto com o vínculo pela chave estrangeira com {@code ON DELETE CASCADE} (V12), qualquer que seja
 * a causa do fim do vínculo.
 */
@Repository
class StudentRegistrationDraftRepositoryAdapter implements StudentRegistrationDraftRepository {

    private final StudentRegistrationDraftJpa jpa;

    StudentRegistrationDraftRepositoryAdapter(StudentRegistrationDraftJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentRegistrationDraft> searchByLinkId(UUID linkId) {
        return jpa.findById(linkId).map(StudentRegistrationDraftRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(StudentRegistrationDraft draft) {
        jpa.saveAndFlush(toEntity(draft));
    }

    @Override
    @Transactional
    public void removeByLinkId(UUID linkId) {
        jpa.deleteByLinkId(linkId);
    }

    @Override
    @Transactional
    public void removeByStudent(UUID studentId) {
        jpa.deleteByStudentId(studentId);
    }

    @Override
    @Transactional
    public int removeExpired(Instant now) {
        return jpa.deleteExpired(now);
    }

    private static StudentRegistrationDraftEntity toEntity(StudentRegistrationDraft d) {
        StudentRegistrationDraftEntity e = new StudentRegistrationDraftEntity();
        e.linkId = d.linkId();
        e.kind = d.kind().name();
        e.studentId = d.studentId();
        e.providedFields =
                d.providedFields().stream()
                        .sorted(Comparator.naturalOrder())
                        .map(Field::name)
                        .collect(Collectors.joining(","));
        e.name = d.name();
        e.instrument = d.instrument();
        e.birthDate = d.birthDate();
        e.cpfEncrypted = d.cpfEncrypted();
        e.cpfHash = d.cpfHash();
        e.guardianName = d.guardianName();
        e.guardianPhone = d.guardianPhone();
        e.guardianEmail = d.guardianEmail();
        e.confirmationToken = d.confirmationToken();
        e.createdAt = d.createdAt();
        e.updatedAt = d.updatedAt();
        e.expiresAt = d.expiresAt();
        e.consent = d.consent().equals(ConsentDraft.EMPTY) ? null : JsonPersistence.write(d.consent());
        return e;
    }

    private static StudentRegistrationDraft toDomain(StudentRegistrationDraftEntity e) {
        Set<Field> fields = EnumSet.noneOf(Field.class);
        if (!e.providedFields.isBlank()) {
            Arrays.stream(e.providedFields.split(",")).map(Field::valueOf).forEach(fields::add);
        }
        return new StudentRegistrationDraft(
                e.linkId,
                Kind.valueOf(e.kind),
                e.studentId,
                fields,
                e.name,
                e.instrument,
                e.birthDate,
                e.cpfEncrypted,
                e.cpfHash,
                e.guardianName,
                e.guardianPhone,
                e.guardianEmail,
                e.confirmationToken,
                e.createdAt,
                e.updatedAt,
                e.expiresAt,
                e.consent == null ? ConsentDraft.EMPTY : JsonPersistence.read(e.consent, ConsentDraft.class));
    }
}
