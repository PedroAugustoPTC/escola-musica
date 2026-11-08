package br.com.felipeblaft.reports.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditChainTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static AuditChain.Fields fields(long sequence) {
        return new AuditChain.Fields(
                sequence,
                Instant.parse("2026-11-04T10:15:30.123456Z"),
                AUTHOR,
                "ADMINISTRATOR",
                "WEB",
                "STUDENT_REGISTER",
                "STUDENT",
                TARGET,
                "SUCCESS",
                null,
                "abc123",
                "corr-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName(
            "a forma canônica segue a ordem do modelo de dados, NULL vazio e data com microssegundos")
    void canonicalForm() {
        assertThat(AuditChain.canonical(fields(7)))
                .isEqualTo(
                        "7|2026-11-04T10:15:30.123456Z|00000000-0000-0000-0000-000000000001|"
                                + "ADMINISTRATOR|WEB|STUDENT_REGISTER|STUDENT|"
                                + "00000000-0000-0000-0000-000000000002|SUCCESS||abc123|corr-1||||||||||");
    }

    @Test
    @DisplayName("os campos de filtro, página, versão e quantidade entram no fim da forma canônica")
    void canonicalFormWithOptionalFields() {
        AuditChain.Fields f =
                new AuditChain.Fields(
                        1,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        null,
                        "RETENTION",
                        "RETENTION_RUN",
                        "RETENTION_RUN",
                        null,
                        "SUCCESS",
                        null,
                        null,
                        null,
                        TARGET,
                        "LESSON",
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-01-31"),
                        "STUDENT_REGISTER",
                        AUTHOR,
                        TARGET,
                        3,
                        2,
                        40);

        assertThat(AuditChain.canonical(f))
                .isEqualTo(
                        "1|2026-01-01T00:00:00.000000Z|||RETENTION|RETENTION_RUN|RETENTION_RUN||SUCCESS||||"
                                + "00000000-0000-0000-0000-000000000002|LESSON|2026-01-01|2026-01-31|"
                                + "STUDENT_REGISTER|00000000-0000-0000-0000-000000000001|"
                                + "00000000-0000-0000-0000-000000000002|3|2|40");
    }

    @Test
    @DisplayName("o hash do registro é sha256 hexadecimal de anterior + barra + forma canônica")
    void recordHash() throws Exception {
        String previous = AuditChain.GENESIS_HASH;

        String hash = AuditChain.hash(previous, fields(1));

        assertThat(previous).isEqualTo("0".repeat(64));
        assertThat(hash).isEqualTo(sha256(previous + "|" + AuditChain.canonical(fields(1))));
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("o primeiro evento depois de uma âncora parte do hash da âncora")
    void startsFromAnchor() {
        String anchor = "a".repeat(64);

        assertThat(AuditChain.hash(anchor, fields(9)))
                .isNotEqualTo(AuditChain.hash(AuditChain.GENESIS_HASH, fields(9)));
    }

    private static List<AuditChain.Link> chain(int size) {
        List<AuditChain.Link> links = new ArrayList<>();
        String previous = AuditChain.GENESIS_HASH;
        for (int i = 1; i <= size; i++) {
            String hash = AuditChain.hash(previous, fields(i));
            links.add(new AuditChain.Link(fields(i), previous, hash));
            previous = hash;
        }
        return links;
    }

    @Test
    @DisplayName("cadeia íntegra não aponta linha quebrada")
    void intactChain() {
        assertThat(AuditChain.firstBroken(chain(5), AuditChain.GENESIS_HASH)).isEmpty();
    }

    @Test
    @DisplayName("alterar um campo aponta a sequência dessa linha como a primeira quebrada")
    void tamperedField() {
        List<AuditChain.Link> links = chain(5);
        AuditChain.Link original = links.get(2);
        AuditChain.Fields changed =
                new AuditChain.Fields(
                        3,
                        Instant.parse("2026-11-04T10:15:30.123456Z"),
                        AUTHOR,
                        "ADMINISTRATOR",
                        "WEB",
                        "STUDENT_REGISTER",
                        "STUDENT",
                        TARGET,
                        "DENIED",
                        null,
                        "abc123",
                        "corr-1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        links.set(2, new AuditChain.Link(changed, original.previousHash(), original.recordHash()));

        assertThat(AuditChain.firstBroken(links, AuditChain.GENESIS_HASH))
                .isEqualTo(OptionalLong.of(3));
    }

    @Test
    @DisplayName("linha removida do meio quebra o encadeamento na linha seguinte")
    void removedLine() {
        List<AuditChain.Link> links = chain(5);
        links.remove(1);

        assertThat(AuditChain.firstBroken(links, AuditChain.GENESIS_HASH))
                .isEqualTo(OptionalLong.of(3));
    }

    @Test
    @DisplayName(
            "a verificação parte do hash da âncora quando as primeiras linhas foram expurgadas")
    void verifyFromAnchor() {
        List<AuditChain.Link> links = chain(5);
        String anchor = links.get(1).recordHash();
        List<AuditChain.Link> rest = new ArrayList<>(links.subList(2, 5));

        assertThat(AuditChain.firstBroken(rest, anchor)).isEmpty();
        assertThat(AuditChain.firstBroken(rest, AuditChain.GENESIS_HASH))
                .isEqualTo(OptionalLong.of(3));
    }
}
