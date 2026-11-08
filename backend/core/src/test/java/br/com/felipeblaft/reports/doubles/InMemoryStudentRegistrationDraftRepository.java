package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastros de aluno em andamento em memória, uma linha por vínculo (o novo substitui o anterior).
 * Para a cascata do banco, registre {@link #removeByLinkId(UUID)} em {@code
 * InMemoryTelegramLinkRepository#cascadeRemovalTo}. Conta as gravações e as remoções para os testes
 * conferirem que nada foi gravado ou apagado a mais.
 */
public class InMemoryStudentRegistrationDraftRepository
        implements StudentRegistrationDraftRepository {

    private final Map<UUID, StudentRegistrationDraft> data = new LinkedHashMap<>();
    private int saves;
    private int removals;

    @Override
    public Optional<StudentRegistrationDraft> searchByLinkId(UUID linkId) {
        return Optional.ofNullable(data.get(linkId));
    }

    @Override
    public void save(StudentRegistrationDraft draft) {
        saves++;
        data.put(draft.linkId(), draft);
    }

    @Override
    public void removeByLinkId(UUID linkId) {
        if (data.remove(linkId) != null) {
            removals++;
        }
    }

    @Override
    public void removeByStudent(UUID studentId) {
        List<UUID> found =
                data.values().stream()
                        .filter(d -> studentId.equals(d.studentId()))
                        .map(d -> d.linkId())
                        .toList();
        found.forEach(data::remove);
        removals += found.size();
    }

    @Override
    public int removeExpired(Instant now) {
        List<UUID> expired =
                data.values().stream().filter(d -> d.expired(now)).map(d -> d.linkId()).toList();
        expired.forEach(data::remove);
        removals += expired.size();
        return expired.size();
    }

    /** Quantas vezes {@code save} foi chamado. */
    public int saves() {
        return saves;
    }

    /** Quantos cadastros foram de fato apagados. */
    public int removals() {
        return removals;
    }

    public List<StudentRegistrationDraft> all() {
        return new ArrayList<>(data.values());
    }
}
