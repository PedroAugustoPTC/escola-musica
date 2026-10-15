package br.com.felipeblaft.reports.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TeacherStudentJpa extends JpaRepository<TeacherStudentEntity, UUID> {

    boolean existsByTeacherIdAndStudentId(UUID teacherId, UUID studentId);
}
