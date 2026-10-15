package br.com.felipeblaft.reports.outbound.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TeacherJpa extends JpaRepository<TeacherEntity, UUID> {

    Optional<TeacherEntity> findByEmail(String email);

    Optional<TeacherEntity> findByAccessCodeHash(String accessCodeHash);
}
