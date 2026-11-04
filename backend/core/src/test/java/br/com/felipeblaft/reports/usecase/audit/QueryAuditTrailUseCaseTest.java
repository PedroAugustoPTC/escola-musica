package br.com.felipeblaft.reports.usecase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase.Command;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Consulta da trilha (FR-045): só o administrador, período de até 90 dias, a própria consulta é
 * evento.
 */
class QueryAuditTrailUseCaseTest {

    private static final String CORRELATION = "req-1";
    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final AuthorshipService authorship = new AuthorshipService(trail, clock);
    private final QueryAuditTrailUseCase query =
            new QueryAuditTrailUseCase(
                    new ActorAuthorizationService(PermissionMatrix.standard()),
                    trail,
                    authorship,
                    new ImmediateTransaction());
    private final Actor admin = ActorFixtures.administratorWeb();
    private final LocalDate today = LocalDate.of(2026, 9, 21);

    private Command period(LocalDate from, LocalDate to) {
        return new Command(
                from, to, null, null, null, null, null, null, PageRequest.first(), CORRELATION);
    }

    private void seed(Operation operation, UUID target) {
        authorship.record(admin, operation, AuthorshipTargetType.STUDENT, target, "req-seed");
    }

    @Test
    @DisplayName("professor recebe RESOURCE_NOT_FOUND e nada é consultado nem gravado")
    void teacherIsRefusedBeforeAnything() {
        seed(Operation.STUDENT_REGISTER, STUDENT);
        int before = trail.records().size();

        assertThatThrownBy(() -> query.run(ActorFixtures.teacherWeb(), period(today, today)))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> query.run(ActorFixtures.teacherTelegram(), period(today, today)))
                .isInstanceOf(DomainException.class);

        assertThat(trail.records()).hasSize(before);
    }

    @Test
    @DisplayName(
            "período ausente ou maior que 90 dias é VALIDATION_FAILED no campo period e vira evento de falha")
    void periodIsRequiredAndLimitedTo90Days() {
        assertThatThrownBy(() -> query.run(admin, period(null, null)))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> query.run(admin, period(today.minusDays(90), today)))
                .isInstanceOf(DomainException.class);

        assertThat(trail.records())
                .extracting(AuthorshipRecord::operation, AuthorshipRecord::result)
                .containsOnly(
                        org.assertj.core.groups.Tuple.tuple(
                                "AUDIT_TRAIL_QUERY", AuthorshipResult.FAILURE));
    }

    @Test
    @DisplayName("noventa dias, inclusive, é aceito")
    void exactlyNinetyDaysIsAccepted() {
        PageResult<AuditTrailEntry> page = query.run(admin, period(today.minusDays(89), today));

        assertThat(page).isNotNull();
    }

    @Test
    @DisplayName("os filtros chegam à porta e a ordem é do mais recente ao mais antigo")
    void filtersAreForwardedNewestFirst() {
        seed(Operation.STUDENT_REGISTER, STUDENT);
        seed(Operation.STUDENT_UPDATE, STUDENT);
        seed(Operation.TEACHER_REGISTER, UUID.randomUUID());

        PageResult<AuditTrailEntry> byStudent =
                query.run(
                        admin,
                        new Command(
                                today,
                                today,
                                null,
                                null,
                                null,
                                null,
                                null,
                                STUDENT,
                                PageRequest.first(),
                                CORRELATION));
        PageResult<AuditTrailEntry> byOperation =
                query.run(
                        admin,
                        new Command(
                                today,
                                today,
                                null,
                                null,
                                null,
                                "TEACHER_REGISTER",
                                null,
                                null,
                                PageRequest.first(),
                                CORRELATION));

        assertThat(byStudent.items())
                .extracting(AuditTrailEntry::operation)
                .containsExactly("STUDENT_UPDATE", "STUDENT_REGISTER");
        assertThat(byOperation.items())
                .extracting(AuditTrailEntry::operation)
                .containsExactly("TEACHER_REGISTER");
    }

    @Test
    @DisplayName(
            "a própria consulta grava AUDIT_TRAIL_QUERY com os filtros tipados, na mesma transação")
    void theQueryIsAnEventWithTypedFilters() {
        query.run(
                admin,
                new Command(
                        today,
                        today,
                        null,
                        null,
                        null,
                        "STUDENT_REGISTER",
                        null,
                        STUDENT,
                        PageRequest.first(),
                        CORRELATION));

        AuthorshipRecord event =
                trail.records().stream()
                        .filter(r -> r.operation().equals("AUDIT_TRAIL_QUERY"))
                        .findFirst()
                        .orElseThrow();
        assertThat(event.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(event.targetType()).isEqualTo(AuthorshipTargetType.AUDIT_TRAIL);
        assertThat(event.readDetail().filterOperation()).isEqualTo("STUDENT_REGISTER");
        assertThat(event.readDetail().filterTargetId()).isEqualTo(STUDENT);
        assertThat(event.readDetail().filterPeriodStart()).isEqualTo(today);
        assertThat(event.readDetail().pageNumber()).isZero();
    }

    @Test
    @DisplayName("se o evento não pode ser gravado, nada é devolvido")
    void nothingIsReturnedWhenTheEventCannotBeRecorded() {
        InMemoryAuditTrail broken =
                new InMemoryAuditTrail() {
                    @Override
                    public synchronized void record(AuthorshipRecord record) {
                        throw new IllegalStateException("sem gravação");
                    }
                };
        QueryAuditTrailUseCase failing =
                new QueryAuditTrailUseCase(
                        new ActorAuthorizationService(PermissionMatrix.standard()),
                        broken,
                        new AuthorshipService(broken, clock),
                        new ImmediateTransaction());

        assertThatThrownBy(() -> failing.run(admin, period(today, today)))
                .isInstanceOf(IllegalStateException.class);
    }
}
