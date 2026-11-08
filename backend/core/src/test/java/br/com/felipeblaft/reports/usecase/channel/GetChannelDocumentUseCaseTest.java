package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.doubles.InMemoryChannelDocumentRepository;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetChannelDocumentUseCaseTest {

    private final ReportScenario scenario = new ReportScenario();
    private final InMemoryChannelDocumentRepository repository =
            new InMemoryChannelDocumentRepository();
    private final List<String> calls = new ArrayList<>();
    private RuntimeException failure;

    private final GetReportDocumentUseCase portal =
            new GetReportDocumentUseCase(
                    scenario.access,
                    scenario.reports,
                    scenario.versions,
                    scenario.pdfDocuments,
                    scenario.documents,
                    new br.com.felipeblaft.reports.service.OfficialDocumentService(
                            scenario.versions, scenario.pdfDocuments, scenario.documents),
                    scenario.reportAudit) {
                @Override
                public ObtainedDocument review(
                        UUID teacherId, ReportType type, UUID reportId, int number) {
                    calls.add("review:" + teacherId + ":" + number);
                    if (failure != null) {
                        throw failure;
                    }
                    return new ObtainedDocument(new byte[] {1, 2}, "review.pdf");
                }

                @Override
                public ObtainedDocument official(UUID teacherId, ReportType type, UUID reportId) {
                    calls.add("official:" + teacherId);
                    if (failure != null) {
                        throw failure;
                    }
                    return new ObtainedDocument(new byte[] {9}, "official.pdf");
                }
            };

    private final GetChannelDocumentUseCase get =
            new GetChannelDocumentUseCase(repository, portal, scenario.clock);
    private final EmitChannelDocumentUseCase emit =
            new EmitChannelDocumentUseCase(
                    repository,
                    scenario.clock,
                    new RetryPolicy(
                            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)));

    @Test
    void officialGoesThroughSamePortalUseCaseWithSavedOwner() {
        UUID teacher = UUID.randomUUID();
        String token = emit.run(teacher, ReportType.LESSON, UUID.randomUUID(), null, "o.pdf");
        ObtainedDocument obtained = get.run(token).orElseThrow();
        assertThat(obtained.content()).containsExactly(9);
        assertThat(calls).containsExactly("official:" + teacher);
    }

    @Test
    void reviewUsesVersionNumber() {
        UUID teacher = UUID.randomUUID();
        String token = emit.run(teacher, ReportType.LESSON, UUID.randomUUID(), 3, "c.pdf");
        assertThat(get.run(token).orElseThrow().fileName()).isEqualTo("review.pdf");
        assertThat(calls).containsExactly("review:" + teacher + ":3");
    }

    @Test
    void repetitionWithinDeadlineStaysValid() {
        String token =
                emit.run(UUID.randomUUID(), ReportType.LESSON, UUID.randomUUID(), null, "o.pdf");
        assertThat(get.run(token)).isPresent();
        scenario.clock.advance(Duration.ofHours(24));
        assertThat(get.run(token)).isPresent();
    }

    @Test
    void unknownAndExpiredAreNotFoundWithoutCallingPortal() {
        assertThat(get.run("a".repeat(43))).isEmpty();
        String token =
                emit.run(UUID.randomUUID(), ReportType.LESSON, UUID.randomUUID(), null, "o.pdf");
        scenario.clock.advance(Duration.ofHours(25));
        assertThat(get.run(token)).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void documentThatVanishedOrWithoutAssignmentBecomesIndistinguishableNotFound() {
        String token =
                emit.run(UUID.randomUUID(), ReportType.LESSON, UUID.randomUUID(), null, "o.pdf");
        for (ErrorCode code :
                List.of(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        ErrorCode.INVALID_DATA)) {
            failure = new DomainException(code);
            assertThat(get.run(token)).as(code.name()).isEmpty();
        }
    }

    @Test
    void realReadFailurePropagatesNeverPartialBytes() {
        String token =
                emit.run(UUID.randomUUID(), ReportType.LESSON, UUID.randomUUID(), null, "o.pdf");
        failure = new IllegalStateException("disk");
        assertThatThrownBy(() -> get.run(token)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyTokenHashIsQueried() {
        String token =
                emit.run(UUID.randomUUID(), ReportType.LESSON, UUID.randomUUID(), null, "o.pdf");
        assertThat(repository.data).containsKey(ChannelDocument.hashDoToken(token));
        assertThat(repository.data).doesNotContainKey(token);
    }
}
