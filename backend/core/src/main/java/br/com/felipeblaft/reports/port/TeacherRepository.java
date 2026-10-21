package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.util.Optional;
import java.util.UUID;

public interface TeacherRepository {

    Optional<Teacher> searchById(UUID id);

    Optional<Teacher> searchByEmail(String normalizedEmail);

    Optional<Teacher> searchByAccessCodeHash(String hash);

    /**
     * Lista em ordem alfabética, com busca opcional por nome ou e-mail e filtro opcional de
     * situação ({@code null} = todos).
     */
    PageResult<Teacher> list(String search, Boolean active, PageRequest page);

    /**
     * Insere somente se o e-mail ainda não existe, sem abortar a transação (FR-041); devolve {@code
     * false} em caso de duplicidade.
     */
    boolean insertIfAbsent(Teacher teacher);

    void save(Teacher teacher);
}
