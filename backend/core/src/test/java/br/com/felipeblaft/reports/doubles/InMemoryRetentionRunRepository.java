package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.privacy.RetentionItemType;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import br.com.felipeblaft.reports.port.RetentionRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dublê em memória das execuções da retenção. */
public class InMemoryRetentionRunRepository implements RetentionRunRepository {

    /** Uma execução. */
    public static final class Run {
        public final UUID id;
        public final Instant startedAt;
        public final Map<String, Integer> policySnapshot;
        public RetentionRunStatus status = RetentionRunStatus.RUNNING;
        public String errorCode;
        public Instant finishedAt;

        Run(UUID id, Instant startedAt, Map<String, Integer> policySnapshot) {
            this.id = id;
            this.startedAt = startedAt;
            this.policySnapshot = policySnapshot;
        }
    }

    /** Um item tratado. */
    public record Item(UUID runId, RetentionItemType type, UUID targetId, int count) {}

    public final List<Run> runs = new ArrayList<>();
    public final List<Item> items = new ArrayList<>();

    @Override
    public Optional<UUID> start(Instant now, Map<String, Integer> snapshot, Duration staleAfter) {
        for (Run run : runs) {
            if (run.status == RetentionRunStatus.RUNNING) {
                if (run.startedAt.plus(staleAfter).isAfter(now)) {
                    return Optional.empty();
                }
                run.status = RetentionRunStatus.INTERRUPTED;
                run.finishedAt = now;
            }
        }
        Run run = new Run(GeneratorId.create(), now, snapshot);
        runs.add(run);
        return Optional.of(run.id);
    }

    @Override
    public void addItem(UUID runId, RetentionItemType type, UUID targetId, int count) {
        items.add(new Item(runId, type, targetId, count));
    }

    @Override
    public void finish(UUID runId, Instant now, RetentionRunStatus status, String errorCode) {
        Run run = runs.stream().filter(r -> r.id.equals(runId)).findFirst().orElseThrow();
        run.status = status;
        run.errorCode = errorCode;
        run.finishedAt = now;
    }

    @Override
    public int purgeStartedBefore(Instant cutoff) {
        List<Run> old =
                runs.stream()
                        .filter(r -> r.status != RetentionRunStatus.RUNNING)
                        .filter(r -> r.startedAt.isBefore(cutoff))
                        .toList();
        runs.removeAll(old);
        items.removeIf(i -> old.stream().anyMatch(r -> r.id.equals(i.runId())));
        return old.size();
    }

    public int totalOf(RetentionItemType type) {
        return items.stream().filter(i -> i.type() == type).mapToInt(Item::count).sum();
    }
}
