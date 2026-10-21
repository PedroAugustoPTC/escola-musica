package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-002: a autorização é decidida no núcleo, pela matriz, para todo ator e toda operação. */
class ActorAuthorizationServiceTest {

    private final ActorAuthorizationService service =
            new ActorAuthorizationService(PermissionMatrix.standard());
    private final UUID id = UUID.randomUUID();
    private final Actor administrator = Actor.administrator(id, false);
    private final Actor restrictedAdministrator = Actor.administrator(id, true);
    private final Actor teacherWeb = Actor.teacher(id, ActionOrigin.WEB);
    private final Actor teacherTelegram = Actor.teacher(id, ActionOrigin.TELEGRAM);

    private void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void returnsThePermissionWhenAllowed() {
        assertThat(service.require(administrator, Operation.TEACHER_REGISTER))
                .isEqualTo(Permission.ALLOW);
        assertThat(service.require(teacherWeb, Operation.STUDENT_LIST))
                .isEqualTo(Permission.ALLOW_OWN_STUDENTS);
        assertThat(service.require(teacherTelegram, Operation.STUDENT_INACTIVATE))
                .isEqualTo(Permission.ALLOW_SOLE_ACTIVE_TEACHER);
        assertThat(service.require(teacherTelegram, Operation.TEACHER_SIGN_IN))
                .isEqualTo(Permission.PUBLIC);
    }

    @Test
    void deniesWithResourceNotFoundWhenTheRoleLacksTheOperation() {
        assertCode(
                () -> service.require(teacherWeb, Operation.TEACHER_REGISTER),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(
                () -> service.require(teacherTelegram, Operation.ADMINISTRATOR_LIST),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(
                () -> service.require(administrator, Operation.LESSON_AND_SEMESTER_REPORTS),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void administratorThroughTelegramIsDeniedEverywhere() {
        Actor onTelegram =
                new Actor(
                        br.com.felipeblaft.reports.domain.access.Role.ADMINISTRATOR,
                        id,
                        ActionOrigin.TELEGRAM,
                        false);
        assertCode(
                () -> service.require(onTelegram, Operation.TEACHER_LIST),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void restrictedAdministratorOnlyChangesPasswordReadsSessionAndSignsOut() {
        assertThat(service.require(restrictedAdministrator, Operation.ADMIN_CHANGE_PASSWORD))
                .isEqualTo(Permission.ALLOW);
        assertThat(service.require(restrictedAdministrator, Operation.ADMIN_SIGN_OUT))
                .isEqualTo(Permission.ALLOW);
        assertThat(service.require(restrictedAdministrator, Operation.ADMIN_GET_SESSION))
                .isEqualTo(Permission.ALLOW);
        assertCode(
                () -> service.require(restrictedAdministrator, Operation.TEACHER_LIST),
                ErrorCode.PASSWORD_CHANGE_REQUIRED);
        assertCode(
                () -> service.require(restrictedAdministrator, Operation.STUDENT_REGISTER),
                ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void theMatrixDecidesBeforeTheRestriction() {
        assertCode(
                () ->
                        service.require(
                                restrictedAdministrator, Operation.LESSON_AND_SEMESTER_REPORTS),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void systemActorDoesOnlyWhatItsOriginAllows() {
        Actor load = Actor.system(ActionOrigin.INITIAL_LOAD);
        Actor startup = Actor.system(ActionOrigin.SYSTEM_STARTUP);
        assertThat(service.require(load, Operation.TEACHER_REGISTER)).isEqualTo(Permission.ALLOW);
        assertThat(service.require(startup, Operation.FIRST_ADMINISTRATOR_BOOTSTRAP))
                .isEqualTo(Permission.ALLOW);
        assertCode(
                () -> service.require(load, Operation.ADMINISTRATOR_CREATE),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(
                () -> service.require(startup, Operation.TEACHER_REGISTER),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void emptyMatrixDeniesEverything() {
        ActorAuthorizationService closed =
                new ActorAuthorizationService(new PermissionMatrix(java.util.Map.of()));
        assertCode(
                () -> closed.require(administrator, Operation.TEACHER_LIST),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> new ActorAuthorizationService(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.require(null, Operation.TEACHER_LIST))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.require(administrator, null))
                .isInstanceOf(NullPointerException.class);
    }
}
