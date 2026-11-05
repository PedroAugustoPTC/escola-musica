package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.privacy.ConsentCollectionMethod;
import br.com.felipeblaft.reports.domain.privacy.ConsentRelationship;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import br.com.felipeblaft.reports.port.ConsentRepository;

/**
 * Consentimentos em {@code student_consent}. O número da versão do termo vem de uma consulta às
 * versões (poucas linhas), para o domínio decidir a situação sem tocar no banco.
 */
@Repository
class ConsentRepositoryAdapter implements ConsentRepository {

    private final StudentConsentJpa jpa;
    private final ConsentTermJpa terms;

    ConsentRepositoryAdapter(StudentConsentJpa jpa, ConsentTermJpa terms) {
        this.jpa = jpa;
        this.terms = terms;
    }

    @Override
    public void save(GuardianConsent consent) {
        jpa.save(toEntity(consent));
    }

    @Override
    public Optional<GuardianConsent> findById(UUID id) {
        return jpa.findById(id).map(entity -> toDomain(entity, numbers()));
    }

    @Override
    public List<GuardianConsent> findByStudent(UUID studentId) {
        Map<UUID, Integer> numbers = numbers();
        return jpa.listByStudent(studentId).stream().map(e -> toDomain(e, numbers)).toList();
    }

    @Override
    public Map<UUID, List<GuardianConsent>> findByStudents(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> numbers = numbers();
        Map<UUID, List<GuardianConsent>> result = new LinkedHashMap<>();
        for (StudentConsentEntity entity : jpa.listByStudents(studentIds)) {
            result.computeIfAbsent(entity.getStudentId(), id -> new java.util.ArrayList<>())
                    .add(toDomain(entity, numbers));
        }
        return result;
    }

    @Override
    @Transactional
    public int removeNamesByStudent(UUID studentId) {
        return jpa.removeNamesByStudent(studentId);
    }

    private Map<UUID, Integer> numbers() {
        return terms.findAll().stream()
                .collect(Collectors.toMap(ConsentTermEntity::getId, ConsentTermEntity::getNumber));
    }

    private static StudentConsentEntity toEntity(GuardianConsent c) {
        return new StudentConsentEntity(
                c.id(),
                c.studentId(),
                c.consentingName(),
                c.relationship().name(),
                c.consentedOn(),
                c.termVersionId(),
                c.collectionMethod().name(),
                c.recordedById(),
                c.recordedByRole() == null ? null : c.recordedByRole().name(),
                c.recordedOrigin().name(),
                c.recordedAt(),
                c.revokedOn(),
                c.revokedBy(),
                c.revocationRecordedBy());
    }

    private static GuardianConsent toDomain(StudentConsentEntity e, Map<UUID, Integer> numbers) {
        return new GuardianConsent(
                e.getId(),
                e.getStudentId(),
                e.getConsentingName(),
                ConsentRelationship.valueOf(e.getRelationship()),
                e.getConsentedOn(),
                e.getTermVersionId(),
                numbers.get(e.getTermVersionId()),
                ConsentCollectionMethod.valueOf(e.getCollectionMethod()),
                e.getRecordedById(),
                e.getRecordedByRole() == null ? null : Role.valueOf(e.getRecordedByRole()),
                ActionOrigin.valueOf(e.getRecordedOrigin()),
                e.getRecordedAt(),
                e.getRevokedOn(),
                e.getRevokedByRole(),
                e.getRevocationRecordedBy());
    }
}
