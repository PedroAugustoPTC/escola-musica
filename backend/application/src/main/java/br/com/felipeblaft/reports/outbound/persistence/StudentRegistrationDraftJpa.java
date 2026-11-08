package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface StudentRegistrationDraftJpa extends JpaRepository<StudentRegistrationDraftEntity, UUID> {

    @Modifying
    @Query(
            value = "delete from student_registration_draft where link_id = :linkId",
            nativeQuery = true)
    int deleteByLinkId(UUID linkId);

    @Modifying
    @Query(
            value = "delete from student_registration_draft where student_id = :studentId",
            nativeQuery = true)
    int deleteByStudentId(UUID studentId);

    @Modifying
    @Query(
            value = "delete from student_registration_draft where expires_at <= :now",
            nativeQuery = true)
    int deleteExpired(Instant now);
}
