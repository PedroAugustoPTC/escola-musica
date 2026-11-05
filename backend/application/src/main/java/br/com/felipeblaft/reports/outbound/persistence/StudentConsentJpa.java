package br.com.felipeblaft.reports.outbound.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface StudentConsentJpa extends JpaRepository<StudentConsentEntity, UUID> {

    @Query(
            "select c from StudentConsentEntity c where c.studentId = :studentId"
                    + " order by c.recordedAt desc")
    List<StudentConsentEntity> listByStudent(UUID studentId);

    @Query(
            "select c from StudentConsentEntity c where c.studentId in :studentIds"
                    + " order by c.recordedAt desc")
    List<StudentConsentEntity> listByStudents(Collection<UUID> studentIds);

    @Modifying
    @Query(
            value = "update student_consent set consenting_name = null where student_id = :studentId",
            nativeQuery = true)
    int removeNamesByStudent(UUID studentId);
}
