package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.port.AuditTrailRetentionPort;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Adulteração e expurgo da trilha no Postgres real (FR-043, FR-044): o dono do banco que altera uma
 * linha por fora é pego pela verificação; o expurgo respeita o piso de 365 dias, grava a âncora e a
 * cadeia continua íntegra a partir dela.
 */
class AuditTrailTamperIT extends IntegrationBase {

    @Autowired AuthorshipRecorderPort recorder;
    @Autowired AuditTrailRetentionPort retention;
    @Autowired VerifyAuditChainUseCase verify;

    private final Actor administrator = Actor.administrator(UUID.randomUUID(), false);

    private void append(Instant at) {
        recorder.recordIndependent(
                new AuthorshipRecord(
                        UUID.randomUUID(),
                        at,
                        UUID.randomUUID(),
                        Role.ADMINISTRATOR,
                        ActionOrigin.WEB,
                        "ADMIN_SIGN_OUT",
                        null,
                        null,
                        AuthorshipResult.SUCCESS,
                        null,
                        null,
                        "req-1"));
    }

    @Test
    void updateAndDeleteAreRefusedForEveryone() {
        append(Instant.now());

        assertThatThrownBy(() -> jdbc.update("update authorship_record set result = 'FAILURE'"))
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> jdbc.update("delete from authorship_record"))
                .hasMessageContaining("somente de acréscimo");
    }

    @Test
    void ownerAlteringARowOutsideTheChainIsDetected() {
        append(Instant.now());
        append(Instant.now());
        append(Instant.now());
        jdbc.execute(
                "alter table authorship_record disable trigger user;"
                        + " update authorship_record set result = 'FAILURE' where sequence ="
                        + " (select min(sequence) + 1 from authorship_record);"
                        + " alter table authorship_record enable trigger user");

        try (LogCapture log = LogCapture.start()) {
            VerifyAuditChainUseCase.Result result = verify.run(administrator, "req-v");

            assertThat(result.status()).isEqualTo(VerifyAuditChainUseCase.Status.BROKEN);
            assertThat(result.firstBrokenSequence()).isNotNull();
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from operational_alert where type = 'AUDIT_CHAIN_BROKEN' and status = 'ACTIVE'",
                                    Integer.class))
                    .as("alerta persistente da cadeia quebrada")
                    .isOne();
        }
    }

    @Test
    void purgeRefusesTheFloorAndOtherwiseLeavesAnAnchorSoTheChainStaysIntact() {
        Instant old = Instant.now().minus(500, ChronoUnit.DAYS);
        append(old);
        append(old.plusSeconds(1));
        append(Instant.now());

        assertThatThrownBy(() -> retention.purgeOlderThan(Instant.now().minus(30, ChronoUnit.DAYS)))
                .hasMessageContaining("piso de 365 dias");

        int removed = retention.purgeOlderThan(Instant.now().minus(400, ChronoUnit.DAYS));

        assertThat(removed).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_chain_anchor", Integer.class))
                .isEqualTo(1);
        VerifyAuditChainUseCase.Result result = verify.run(administrator, "req-v");
        assertThat(result.status()).isEqualTo(VerifyAuditChainUseCase.Status.INTACT);
        assertThat(result.checkedEvents()).isEqualTo(1);
    }
}
