package br.com.felipeblaft.relatorios.saida.persistencia;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VinculoTelegramJpa extends JpaRepository<VinculoTelegramEntidade, UUID> {

    Optional<VinculoTelegramEntidade> findByTelegramUsuarioId(long telegramUsuarioId);

    Optional<VinculoTelegramEntidade> findByProfessorId(UUID professorId);
}
