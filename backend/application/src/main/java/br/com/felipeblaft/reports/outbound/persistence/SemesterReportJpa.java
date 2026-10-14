package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SemesterReportJpa extends JpaRepository<SemesterReportEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SemesterReportEntity r where r.id = :id")
    Optional<SemesterReportEntity> searchForUpdate(UUID id);

    @Query(
            """
            select r from SemesterReportEntity r
            where r.teacherId = :teacherId and r.studentId = :studentId
              and r.periodKey = :periodKey and r.status = :status
            """)
    Optional<SemesterReportEntity> doTrio(
            UUID teacherId, UUID studentId, String periodKey, String status);

    @Query(
            """
            select r from SemesterReportEntity r
            where r.teacherId = :teacherId and r.studentId = :studentId and r.status = 'APPROVED'
            order by r.periodStart desc
            """)
    List<SemesterReportEntity> approved(UUID teacherId, UUID studentId);

    @Query(
            """
            select r from SemesterReportEntity r
            where r.teacherId = :teacherId and r.status = 'AWAITING_REVIEW'
            order by r.updatedAt desc
            """)
    List<SemesterReportEntity> teacherOpenReports(UUID teacherId);
}
