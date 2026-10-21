package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Professores em memória. A lista segue a regra da porta: ordem alfabética e busca por nome ou
 * e-mail sem diferenciar acento nem caixa (mesma normalização da busca de alunos), filtro opcional
 * de situação e paginação depois do filtro.
 */
public class InMemoryTeacherRepository implements TeacherRepository {

    private final Map<UUID, Teacher> data = new LinkedHashMap<>();
    private int recordings;

    @Override
    public Optional<Teacher> searchById(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<Teacher> searchByEmail(String normalizedEmail) {
        return data.values().stream().filter(p -> p.email().equals(normalizedEmail)).findFirst();
    }

    @Override
    public Optional<Teacher> searchByAccessCodeHash(String hash) {
        return data.values().stream()
                .filter(p -> Objects.equals(p.accessCodeHash(), hash))
                .findFirst();
    }

    @Override
    public PageResult<Teacher> list(String search, Boolean active, PageRequest page) {
        String query = StudentSearch.normalize(search);
        List<Teacher> filtered =
                data.values().stream()
                        .filter(teacher -> active == null || teacher.active() == active)
                        .filter(teacher -> query.isEmpty() || matches(teacher, query))
                        .sorted(
                                Comparator.comparing(
                                                (Teacher teacher) ->
                                                        StudentSearch.normalize(teacher.name()))
                                        .thenComparing(Teacher::name))
                        .toList();
        return PageResult.slice(filtered, page);
    }

    @Override
    public boolean insertIfAbsent(Teacher teacher) {
        if (searchByEmail(teacher.email()).isPresent()) {
            return false;
        }
        save(teacher);
        return true;
    }

    @Override
    public void save(Teacher teacher) {
        recordings++;
        data.put(teacher.id(), teacher);
    }

    public List<Teacher> all() {
        return new ArrayList<>(data.values());
    }

    public int recordings() {
        return recordings;
    }

    private static boolean matches(Teacher teacher, String normalizedQuery) {
        return StudentSearch.normalize(teacher.name()).contains(normalizedQuery)
                || StudentSearch.normalize(teacher.email()).contains(normalizedQuery);
    }
}
