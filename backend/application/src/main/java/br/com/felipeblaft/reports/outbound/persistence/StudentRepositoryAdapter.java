package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class StudentRepositoryAdapter implements StudentRepository {

    private static final String CPF_HASH_CONSTRAINT = "uk_student_cpf_hash";

    private final StudentJpa jpa;
    private final TransactionTemplate independent;

    StudentRepositoryAdapter(StudentJpa jpa, PlatformTransactionManager transactions) {
        this.jpa = jpa;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<Student> searchById(UUID id) {
        return jpa.findById(id).filter(e -> e.getErasedAt() == null).map(RegistryMapper::toDomain);
    }

    @Override
    public Optional<Student> searchByCpfHash(String cpfHash) {
        return jpa.findByCpfHash(cpfHash).map(RegistryMapper::toDomain);
    }

    @Override
    public List<Student> listByTeacher(UUID teacherId) {
        return jpa.listByTeacher(teacherId).stream().map(RegistryMapper::toDomain).toList();
    }

    @Override
    public void save(Student student) {
        StudentEntity entity = RegistryMapper.toEntity(student);
        try {
            // descarrega já: a violação do índice único surge aqui, não no commit
            jpa.saveAndFlush(entity);
        } catch (DataIntegrityViolationException violation) {
            if (!violatesCpfHash(violation)) {
                throw violation;
            }
            throw duplicateCpf(entity.getCpfHash());
        }
    }

    private static boolean violatesCpfHash(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && CPF_HASH_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Duplicidade de CPF decidida pelo índice único. A transação do chamador está abortada, então o
     * dono do CPF é lido numa transação própria; só o nome sai (nunca o CPF).
     */
    private DomainException duplicateCpf(String cpfHash) {
        String holderName =
                independent.execute(
                        status ->
                                jpa.findByCpfHash(cpfHash)
                                        .map(StudentEntity::getName)
                                        .orElse(null));
        return holderName == null
                ? new DomainException(ErrorCode.DUPLICATE_CPF)
                : new DomainException(ErrorCode.DUPLICATE_CPF, Map.of("name", holderName));
    }

    @Override
    public Optional<Student> findVisible(StudentScope scope, UUID id) {
        Optional<StudentEntity> found =
                scope.unrestricted()
                        ? jpa.findById(id).filter(e -> e.getErasedAt() == null)
                        : jpa.findVisibleToTeacher(id, scope.teacherId());
        return found.map(RegistryMapper::toDomain);
    }

    @Override
    public List<Student> list(StudentScope scope, StudentStatusFilter status) {
        List<Boolean> states =
                switch (status) {
                    case ACTIVE -> List.of(true);
                    case INACTIVE -> List.of(false);
                    case ALL -> List.of(true, false);
                };
        List<StudentEntity> entities =
                scope.unrestricted()
                        ? jpa.listByState(states)
                        : jpa.listByTeacherAndState(scope.teacherId(), states);
        return entities.stream().map(RegistryMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Student student) {
        return jpa.insertIfNewCpf(
                        student.id(),
                        student.name(),
                        student.birthDate(),
                        student.encryptedCpf(),
                        student.cpfHash(),
                        student.guardianName(),
                        student.guardianPhone(),
                        student.guardianEmail(),
                        student.instrument(),
                        student.active(),
                        student.createdAt(),
                        student.updatedAt())
                > 0;
    }

    @Override
    @Transactional
    public boolean erase(UUID id, java.time.Instant now) {
        return jpa.erase(id, now) > 0;
    }
}
