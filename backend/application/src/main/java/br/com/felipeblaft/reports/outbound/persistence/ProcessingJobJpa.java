package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.ProcessingJobEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProcessingJobJpa extends JpaRepository<ProcessingJobEntity, UUID> {

    /** Próximo pendente sem disputar com outros workers (R-06). */
    @Query(
            value =
                    "select id from processing_job where status = 'PENDENTE'"
                            + " order by created_at limit 1 for update skip locked",
            nativeQuery = true)
    Optional<UUID> nextPending();

    @Query(
            """
            select t from ProcessingJobEntity t
            where t.reportType = :type and t.reportId = :reportId
              and t.status in ('PENDENTE', 'EM_EXECUCAO')
            order by t.createdAt desc
            """)
    List<ProcessingJobEntity> reportInProgress(String type, UUID reportId);

    @Query(
            """
            select t from ProcessingJobEntity t
            where t.teacherId = :teacherId and t.studentId = :studentId and t.reportId is null
              and t.type in ('REGISTRO_AULA') and t.status in ('PENDENTE', 'EM_EXECUCAO')
            """)
    List<ProcessingJobEntity> registrationInProgress(UUID teacherId, UUID studentId);

    @Query(
            """
            select t from ProcessingJobEntity t
            where t.reportId is null and t.type = 'REGISTRO_AULA'
              and t.teacherId = :teacherId and t.status in ('PENDENTE', 'EM_EXECUCAO')
            order by t.createdAt desc
            """)
    List<ProcessingJobEntity> teacherRegistrationsInProgress(UUID teacherId);

    @Query(
            """
            select t from ProcessingJobEntity t
            where t.status in ('PENDENTE', 'EM_EXECUCAO') and t.delayNoticeSent = false
              and t.createdAt <= :limit
            """)
    List<ProcessingJobEntity> delayedWithoutNotice(Instant limit);

    List<ProcessingJobEntity> findByStatus(String status);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
            value =
                    "update processing_job set delay_notice_sent = true"
                            + " where id = :id and delay_notice_sent = false",
            nativeQuery = true)
    int markDelayNotice(UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
            value =
                    "update processing_job set parameters ="
                            + " coalesce(parameters, '{}'::jsonb) || jsonb_build_object(:key, cast(:value as text))"
                            + " where id = :id",
            nativeQuery = true)
    int defineParameter(UUID id, String key, String value);

    @Query(
            value =
                    "select * from processing_job where type = 'CONSOLIDACAO_SEMESTRAL'"
                            + " and status in ('PENDENTE', 'EM_EXECUCAO') and teacher_id = :teacherId"
                            + " and student_id = :studentId and parameters ->> 'periodKey' = :periodKey",
            nativeQuery = true)
    List<ProcessingJobEntity> consolidationInProgress(
            UUID teacherId, UUID studentId, String periodKey);

    @Query(
            """
            select t from ProcessingJobEntity t
            where t.type = 'CONSOLIDACAO_SEMESTRAL' and t.reportId is null
              and t.teacherId = :teacherId and t.status in ('PENDENTE', 'EM_EXECUCAO')
            order by t.createdAt desc
            """)
    List<ProcessingJobEntity> teacherConsolidationsInProgress(UUID teacherId);
}
