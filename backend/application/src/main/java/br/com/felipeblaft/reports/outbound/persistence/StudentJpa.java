package br.com.felipeblaft.reports.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface StudentJpa extends JpaRepository<StudentEntity, UUID> {

    Optional<StudentEntity> findByCpfHash(String cpfHash);

    @Query(
            """
            select a from StudentEntity a
            where a.id in (select pa.studentId from TeacherStudentEntity pa where pa.teacherId = :teacherId)
            order by lower(a.name), a.id
            """)
    List<StudentEntity> listByTeacher(UUID teacherId);

    @Query(
            """
            select a from StudentEntity a
            where a.id = :id
              and exists (select 1 from TeacherStudentEntity pa
                          where pa.studentId = a.id and pa.teacherId = :teacherId)
            """)
    Optional<StudentEntity> findVisibleToTeacher(UUID id, UUID teacherId);

    @Query("select a from StudentEntity a where a.active in :states order by lower(a.name), a.id")
    List<StudentEntity> listByState(Collection<Boolean> states);

    @Query(
            """
            select a from StudentEntity a
            where a.active in :states
              and a.id in (select pa.studentId from TeacherStudentEntity pa where pa.teacherId = :teacherId)
            order by lower(a.name), a.id
            """)
    List<StudentEntity> listByTeacherAndState(UUID teacherId, Collection<Boolean> states);

    /**
     * Insere sem abortar a transação quando o resumo do CPF já existe; devolve as linhas inseridas.
     */
    @Modifying
    @Query(
            value =
                    "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, guardian_name,"
                            + " guardian_phone, guardian_email, instrument, active, created_at, updated_at)"
                            + " values (:id, :name, :birthDate, :cpfEncrypted, :cpfHash, :guardianName,"
                            + " :guardianPhone, :guardianEmail, :instrument, :active, :createdAt, :updatedAt)"
                            + " on conflict (cpf_hash) do nothing",
            nativeQuery = true)
    int insertIfNewCpf(
            UUID id,
            String name,
            java.time.LocalDate birthDate,
            byte[] cpfEncrypted,
            String cpfHash,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String instrument,
            boolean active,
            java.time.Instant createdAt,
            java.time.Instant updatedAt);
}
