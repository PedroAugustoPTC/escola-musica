package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class TelegramLinkRepositoryAdapter implements TelegramLinkRepository {

    private final TelegramLinkJpa jpa;

    TelegramLinkRepositoryAdapter(TelegramLinkJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<TelegramLink> searchByTelegramUserId(long telegramUserId) {
        return jpa.findByTelegramUserId(telegramUserId).map(TelegramLinkMapper::toDomain);
    }

    @Override
    public Optional<TelegramLink> searchByTeacherId(UUID teacherId) {
        return jpa.findByTeacherId(teacherId).map(TelegramLinkMapper::toDomain);
    }

    @Override
    public void save(TelegramLink link) {
        TelegramLinkEntity saved = jpa.saveAndFlush(TelegramLinkMapper.toEntity(link));
        link.registerPersistedVersion(saved.lockVersion);
    }

    @Override
    public void remove(UUID id) {
        jpa.deleteById(id);
        jpa.flush();
    }
}
