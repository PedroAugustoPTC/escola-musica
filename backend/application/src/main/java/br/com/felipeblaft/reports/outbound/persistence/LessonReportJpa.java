package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.LessonReportEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface LessonReportJpa extends JpaRepository<LessonReportEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LessonReportEntity r where r.id = :id")
    Optional<LessonReportEntity> searchForUpdate(UUID id);

    @Query(
            """
            select r from LessonReportEntity r
            where r.teacherId = :teacherId and r.studentId = :studentId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
            """)
    Optional<LessonReportEntity> searchOpen(UUID teacherId, UUID studentId);

    @Query(
            """
            select r from LessonReportEntity r
            where r.teacherId = :teacherId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
            order by r.updatedAt desc
            """)
    List<LessonReportEntity> listTeacherOpenReports(UUID teacherId);

    /** Aprovados do par com aula no período, com a data da aula, em ordem cronológica. */
    @Query(
            """
            select r, a.lessonDate from LessonReportEntity r, LessonEntity a
            where a.id = r.lessonId and r.teacherId = :teacherId and r.studentId = :studentId
              and r.status = 'APROVADO' and a.lessonDate between :start and :end
            order by a.lessonDate, r.approvedAt
            """)
    List<Object[]> approvedInPeriod(
            UUID teacherId, UUID studentId, java.time.LocalDate start, java.time.LocalDate end);

    @Query(
            """
            select count(r) from LessonReportEntity r, LessonEntity a
            where a.id = r.lessonId and r.teacherId = :teacherId and r.studentId = :studentId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
              and a.lessonDate between :start and :end
            """)
    long countOpenInPeriod(
            UUID teacherId, UUID studentId, java.time.LocalDate start, java.time.LocalDate end);

    @Query(
            "select min(a.lessonDate) from LessonEntity a where a.teacherId = :teacherId and a.studentId = :studentId")
    Optional<java.time.LocalDate> firstLesson(UUID teacherId, UUID studentId);

    @Query(
            """
            select r, a.lessonDate from LessonReportEntity r, LessonEntity a
            where a.id = r.lessonId and r.teacherId = :teacherId and r.studentId = :studentId
              and r.status = 'APROVADO'
            """)
    List<Object[]> approved(UUID teacherId, UUID studentId);
}
