package br.com.felipeblaft.reports.outbound.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TelegramLinkJpa extends JpaRepository<TelegramLinkEntity, UUID> {

    Optional<TelegramLinkEntity> findByTelegramUserId(long telegramUserId);

    Optional<TelegramLinkEntity> findByTeacherId(UUID teacherId);
}
