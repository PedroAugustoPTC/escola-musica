package br.com.felipeblaft.reports.outbound.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
