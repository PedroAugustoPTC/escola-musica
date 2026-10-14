package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_telegram_update")
public class ProcessedTelegramUpdateEntity {

    @Id
    @Column(name = "update_id")
    private long updateId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "total_actions")
    private Integer totalActions;

    @Column(name = "origin")
    private String origin;

    protected ProcessedTelegramUpdateEntity() {}

    long getUpdateId() {
        return updateId;
    }

    Instant getReceivedAt() {
        return receivedAt;
    }

    Integer getTotalActions() {
        return totalActions;
    }

    String getOrigin() {
        return origin;
    }
}
