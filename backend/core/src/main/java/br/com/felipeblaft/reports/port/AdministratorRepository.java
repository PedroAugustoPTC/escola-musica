package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência dos administradores. O e-mail é único, em minúsculas, e a unicidade é por papel (o
 * e-mail pode coincidir com o de um professor). A ativação é por e-mail: não há busca por código.
 */
public interface AdministratorRepository {

    Optional<Administrator> searchById(UUID id);

    /** Busca pelo e-mail já normalizado (minúsculas, sem espaços nas pontas). */
    Optional<Administrator> searchByEmail(String normalizedEmail);

    long count();

    /**
     * Insere somente se ainda não existe nenhum administrador (primeiro administrador, R-16),
     * atomicamente; devolve {@code false} se já havia algum.
     */
    boolean insertIfNoneExists(Administrator administrator);

    /**
     * Insere somente se o e-mail ainda não existe, sem abortar a transação (FR-041); devolve {@code
     * false} em caso de duplicidade.
     */
    boolean insertIfAbsent(Administrator administrator);

    void save(Administrator administrator);

    /**
     * Lista em ordem alfabética, com busca opcional por nome ou e-mail e filtro opcional de
     * situação ({@code null} = todos).
     */
    PageResult<Administrator> list(String search, Boolean active, PageRequest page);

    /**
     * Bloqueia, na transação corrente, todos os administradores ativos em ordem fixa de id (sem
     * deadlock) e os devolve; base do invariante de nunca ficar sem administrador ativo (R-15).
     */
    List<Administrator> lockAllActiveForUpdate();
}
