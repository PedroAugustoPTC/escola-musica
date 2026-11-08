package br.com.felipeblaft.reports.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TeacherStudentJpa extends JpaRepository<TeacherStudentEntity, UUID> {

    boolean existsByTeacherIdAndStudentId(UUID teacherId, UUID studentId);

    @Modifying
    @Query(
            value =
                    "delete from teacher_student_assignment where teacher_id = :teacherId"
                            + " and student_id = :studentId",
            nativeQuery = true)
    int deleteByPair(UUID teacherId, UUID studentId);

    @Query("select a.teacherId from TeacherStudentEntity a where a.studentId = :studentId")
    List<UUID> listTeachersOfStudent(UUID studentId);

    @Query(
            value =
                    "select count(*) from teacher_student_assignment a join teacher t on t.id = a.teacher_id"
                            + " where a.student_id = :studentId and t.active",
            nativeQuery = true)
    int countActiveTeachers(UUID studentId);

    @Modifying
    @Query(
            value = "delete from teacher_student_assignment where student_id = :studentId",
            nativeQuery = true)
    int deleteAllByStudent(UUID studentId);
}
