package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.outbound.persistence.ReportEntities.LessonEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LessonJpa extends JpaRepository<LessonEntity, UUID> {}
