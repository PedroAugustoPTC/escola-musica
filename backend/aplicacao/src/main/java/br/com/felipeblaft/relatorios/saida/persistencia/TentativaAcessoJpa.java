package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TentativaAcessoJpa extends JpaRepository<TentativaAcessoEntidade, UUID> {

    Optional<TentativaAcessoEntidade> findByCanalAndOrigemHash(Canal canal, String origemHash);
}
