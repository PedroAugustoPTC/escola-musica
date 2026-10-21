package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Administradores em memória. Segue a regra da porta: e-mail único (já normalizado), lista em ordem
 * alfabética com busca por nome ou e-mail sem diferenciar acento nem caixa, filtro opcional de
 * situação, inserção atômica do primeiro administrador e bloqueio dos ativos em ordem fixa de id.
 */
public class InMemoryAdministratorRepository implements AdministratorRepository {

    private final Map<UUID, Administrator> data = new LinkedHashMap<>();
    private int recordings;
    private int locks;

    @Override
    public Optional<Administrator> searchById(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<Administrator> searchByEmail(String normalizedEmail) {
        return data.values().stream()
                .filter(administrator -> administrator.email().equals(normalizedEmail))
                .findFirst();
    }

    @Override
    public long count() {
        return data.size();
    }

    @Override
    public boolean insertIfNoneExists(Administrator administrator) {
        if (!data.isEmpty()) {
            return false;
        }
        save(administrator);
        return true;
    }

    @Override
    public boolean insertIfAbsent(Administrator administrator) {
        if (searchByEmail(administrator.email()).isPresent()) {
            return false;
        }
        save(administrator);
        return true;
    }

    @Override
    public void save(Administrator administrator) {
        recordings++;
        data.put(administrator.id(), administrator);
    }

    @Override
    public PageResult<Administrator> list(String search, Boolean active, PageRequest page) {
        String query = StudentSearch.normalize(search);
        List<Administrator> filtered =
                data.values().stream()
                        .filter(administrator -> active == null || administrator.active() == active)
                        .filter(administrator -> query.isEmpty() || matches(administrator, query))
                        .sorted(
                                Comparator.comparing(
                                                (Administrator administrator) ->
                                                        StudentSearch.normalize(
                                                                administrator.name()))
                                        .thenComparing(Administrator::name))
                        .toList();
        return PageResult.slice(filtered, page);
    }

    @Override
    public List<Administrator> lockAllActiveForUpdate() {
        locks++;
        return data.values().stream()
                .filter(Administrator::active)
                .sorted(Comparator.comparing(Administrator::id))
                .toList();
    }

    public List<Administrator> all() {
        return new ArrayList<>(data.values());
    }

    public int recordings() {
        return recordings;
    }

    /** Quantas vezes os ativos foram bloqueados (R-15). */
    public int locks() {
        return locks;
    }

    private static boolean matches(Administrator administrator, String normalizedQuery) {
        return StudentSearch.normalize(administrator.name()).contains(normalizedQuery)
                || StudentSearch.normalize(administrator.email()).contains(normalizedQuery);
    }
}
