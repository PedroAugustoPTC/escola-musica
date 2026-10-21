package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registro de autoria em memória, somente de acréscimo como a porta. Guarda os dois modos
 * separados: {@code record} (mesma transação do caso de uso) e {@code recordIndependent} (transação
 * independente, que sobrevive ao desfazer da operação).
 */
public class FakeAuthorshipRecorder implements AuthorshipRecorderPort {

    private final List<Entry> entries = new ArrayList<>();
    private RuntimeException nextRecordFailure;

    private record Entry(AuthorshipRecord record, boolean independent) {}

    @Override
    public void record(AuthorshipRecord record) {
        if (nextRecordFailure != null) {
            RuntimeException failure = nextRecordFailure;
            nextRecordFailure = null;
            throw failure;
        }
        entries.add(new Entry(record, false));
    }

    @Override
    public void recordIndependent(AuthorshipRecord record) {
        entries.add(new Entry(record, true));
    }

    /** A próxima chamada a {@code record} falha com esta exceção e nada é guardado. */
    public void failNextRecordWith(RuntimeException failure) {
        this.nextRecordFailure = failure;
    }

    /**
     * Simula o desfazer da transação do caso de uso: os registros gravados na mesma transação
     * somem; os independentes permanecem.
     */
    public void rollBackTransaction() {
        entries.removeIf(entry -> !entry.independent());
    }

    /** Todos os registros, na ordem de chegada. */
    public List<AuthorshipRecord> records() {
        return select(entry -> true);
    }

    /** Só os gravados na mesma transação do caso de uso. */
    public List<AuthorshipRecord> inTransaction() {
        return select(entry -> !entry.independent());
    }

    /** Só os gravados em transação independente. */
    public List<AuthorshipRecord> independent() {
        return select(Entry::independent);
    }

    public List<AuthorshipRecord> ofOperation(String operation) {
        return select(entry -> entry.record().operation().equals(operation));
    }

    public List<AuthorshipRecord> ofOperation(Operation operation) {
        return ofOperation(operation.name());
    }

    public Optional<AuthorshipRecord> last() {
        return entries.isEmpty()
                ? Optional.empty()
                : Optional.of(entries.get(entries.size() - 1).record());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private List<AuthorshipRecord> select(java.util.function.Predicate<Entry> filter) {
        return entries.stream().filter(filter).map(Entry::record).toList();
    }
}
