package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.common.Channel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TentativaAcessoJpa extends JpaRepository<TentativaAcessoEntidade, UUID> {

    Optional<TentativaAcessoEntidade> findByCanalAndOrigemHash(Channel canal, String origemHash);
}
