package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.PermissionMatrix.Rule;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-048: a matriz é negada por padrão; operação ausente equivale a {@code DENY}. */
class PermissionMatrixTest {

    private final UUID id = UUID.randomUUID();
    private final Actor administrator = Actor.administrator(id, false);
    private final Actor teacherWeb = Actor.teacher(id, ActionOrigin.WEB);
    private final Actor teacherTelegram = Actor.teacher(id, ActionOrigin.TELEGRAM);
    private final PermissionMatrix matrix = PermissionMatrix.standard();

    @Test
    void emptyMatrixDeniesEverythingForEveryone() {
        PermissionMatrix empty = new PermissionMatrix(Map.of());
        for (Operation operation : Operation.values()) {
            assertThat(empty.decide(operation, administrator)).isEqualTo(Permission.DENY);
            assertThat(empty.decide(operation, teacherWeb)).isEqualTo(Permission.DENY);
            assertThat(empty.decide(operation, teacherTelegram)).isEqualTo(Permission.DENY);
            assertThat(empty.decide(operation, Actor.system(ActionOrigin.INITIAL_LOAD)))
                    .isEqualTo(Permission.DENY);
            assertThat(empty.decideAnonymous(operation)).isEqualTo(Permission.DENY);
        }
    }

    @Test
    void operationAbsentFromTheMatrixIsDenied() {
        Rule allowAll = new Rule(Permission.ALLOW, Permission.ALLOW, Permission.ALLOW, Set.of());
        PermissionMatrix partial = new PermissionMatrix(Map.of(Operation.TEACHER_LIST, allowAll));
        assertThat(partial.decide(Operation.TEACHER_LIST, administrator))
                .isEqualTo(Permission.ALLOW);
        assertThat(partial.decide(Operation.TEACHER_GET, administrator)).isEqualTo(Permission.DENY);
    }

    @Test
    void standardMatrixHasARuleForEveryOperation() {
        assertThat(matrix.rules().keySet()).containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void rulesAreImmutableAfterConstruction() {
        assertThatThrownBy(() -> matrix.rules().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        EnumSet<ActionOrigin> origins = EnumSet.of(ActionOrigin.INITIAL_LOAD);
        Rule rule = new Rule(Permission.ALLOW, Permission.DENY, Permission.DENY, origins);
        origins.clear();
        assertThat(rule.system()).containsExactly(ActionOrigin.INITIAL_LOAD);
        assertThatThrownBy(() -> new Rule(null, Permission.DENY, Permission.DENY, Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void administratorManagesTeachersAndAdministratorsOnlyThroughTheWeb() {
        assertThat(matrix.decide(Operation.TEACHER_REGISTER, administrator))
                .isEqualTo(Permission.ALLOW);
        assertThat(matrix.decide(Operation.TEACHER_REGISTER, teacherWeb))
                .isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.TEACHER_REGISTER, teacherTelegram))
                .isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.ADMINISTRATOR_CREATE, teacherTelegram))
                .isEqualTo(Permission.DENY);
    }

    @Test
    void administratorIsNeverAllowedThroughTelegram() {
        Actor administratorOnTelegram =
                new Actor(Role.ADMINISTRATOR, id, ActionOrigin.TELEGRAM, false);
        for (Operation operation : Operation.values()) {
            assertThat(matrix.decide(operation, administratorOnTelegram))
                    .as(operation.name())
                    .isEqualTo(Permission.DENY);
        }
    }

    @Test
    void teacherActsOnOwnStudentsAndOnlySoleActiveTeacherChangesTheirSituation() {
        assertThat(matrix.decide(Operation.STUDENT_REGISTER, teacherWeb))
                .isEqualTo(Permission.ALLOW_OWN_STUDENTS);
        assertThat(matrix.decide(Operation.STUDENT_LIST, teacherTelegram))
                .isEqualTo(Permission.ALLOW_OWN_STUDENTS);
        assertThat(matrix.decide(Operation.STUDENT_INACTIVATE, teacherWeb))
                .isEqualTo(Permission.ALLOW_SOLE_ACTIVE_TEACHER);
        assertThat(matrix.decide(Operation.STUDENT_REACTIVATE, teacherTelegram))
                .isEqualTo(Permission.ALLOW_SOLE_ACTIVE_TEACHER);
        assertThat(matrix.decide(Operation.STUDENT_INACTIVATE, administrator))
                .isEqualTo(Permission.ALLOW);
    }

    @Test
    void administratorNeverTouchesReports() {
        assertThat(matrix.decide(Operation.LESSON_AND_SEMESTER_REPORTS, administrator))
                .isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.LESSON_AND_SEMESTER_REPORTS, teacherWeb))
                .isEqualTo(Permission.ALLOW_OWN_STUDENTS);
    }

    @Test
    void schoolHistoryOperationsAreOnlyForTheAdministratorOnTheWeb() {
        for (Operation operation :
                Set.of(
                        Operation.SCHOOL_HISTORY_QUERY,
                        Operation.SCHOOL_REPORT_DOCUMENT_OPEN,
                        Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD)) {
            assertThat(matrix.decide(operation, administrator))
                    .as(operation.name())
                    .isEqualTo(Permission.ALLOW);
            assertThat(matrix.decide(operation, teacherWeb))
                    .as(operation.name())
                    .isEqualTo(Permission.DENY);
            assertThat(matrix.decide(operation, teacherTelegram))
                    .as(operation.name())
                    .isEqualTo(Permission.DENY);
            assertThat(matrix.rules().get(operation).system()).as(operation.name()).isEmpty();
            for (ActionOrigin origin :
                    EnumSet.of(ActionOrigin.SYSTEM_STARTUP, ActionOrigin.INITIAL_LOAD)) {
                assertThat(matrix.decide(operation, Actor.system(origin)))
                        .as(operation.name())
                        .isEqualTo(Permission.DENY);
            }
            assertThat(matrix.decideAnonymous(operation)).isEqualTo(Permission.DENY);
        }
    }

    @Test
    void systemActorOnlyDoesWhatTheMatrixListsForItsOrigin() {
        Actor startup = Actor.system(ActionOrigin.SYSTEM_STARTUP);
        Actor load = Actor.system(ActionOrigin.INITIAL_LOAD);
        assertThat(matrix.decide(Operation.FIRST_ADMINISTRATOR_BOOTSTRAP, startup))
                .isEqualTo(Permission.ALLOW);
        assertThat(matrix.decide(Operation.FIRST_ADMINISTRATOR_BOOTSTRAP, load))
                .isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.TEACHER_REGISTER, load)).isEqualTo(Permission.ALLOW);
        assertThat(matrix.decide(Operation.TEACHER_REGISTER, startup)).isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.ASSIGNMENT_REMOVE, load)).isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.ADMINISTRATOR_CREATE, load)).isEqualTo(Permission.DENY);
    }

    @Test
    void anonymousCallerOnlyReachesEntryOperations() {
        assertThat(matrix.decideAnonymous(Operation.ADMIN_SIGN_IN)).isEqualTo(Permission.PUBLIC);
        assertThat(matrix.decideAnonymous(Operation.ADMIN_ACTIVATE)).isEqualTo(Permission.PUBLIC);
        assertThat(matrix.decideAnonymous(Operation.TEACHER_SIGN_IN)).isEqualTo(Permission.PUBLIC);
        assertThat(matrix.decideAnonymous(Operation.TEACHER_LIST)).isEqualTo(Permission.DENY);
        assertThat(matrix.decideAnonymous(Operation.STUDENT_LIST)).isEqualTo(Permission.DENY);
        assertThat(matrix.decideAnonymous(Operation.FIRST_ADMINISTRATOR_BOOTSTRAP))
                .isEqualTo(Permission.DENY);
    }

    @Test
    void teacherSignInIsPublicInBothChannelsButAdministratorSignInIsNotOnTelegram() {
        assertThat(matrix.decide(Operation.TEACHER_SIGN_IN, teacherTelegram))
                .isEqualTo(Permission.PUBLIC);
        assertThat(matrix.decide(Operation.ADMIN_SIGN_IN, teacherTelegram))
                .isEqualTo(Permission.DENY);
        assertThat(matrix.decide(Operation.ADMIN_SIGN_IN, teacherWeb)).isEqualTo(Permission.PUBLIC);
    }

    @Test
    void permissionAllowedIsEverythingButDeny() {
        for (Permission permission : Permission.values()) {
            assertThat(permission.allowed()).isEqualTo(permission != Permission.DENY);
        }
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> matrix.decide(null, administrator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> matrix.decide(Operation.TEACHER_LIST, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> matrix.decideAnonymous(null))
                .isInstanceOf(NullPointerException.class);
    }
}
