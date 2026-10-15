package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
}
