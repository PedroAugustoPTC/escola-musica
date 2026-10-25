package br.com.felipeblaft.reports.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorshipRecordJpa extends JpaRepository<AuthorshipRecordEntity, UUID> {}
