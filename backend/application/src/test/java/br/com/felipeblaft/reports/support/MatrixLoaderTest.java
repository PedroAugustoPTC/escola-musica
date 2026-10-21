package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.support.MatrixLoader.Matrix;
import br.com.felipeblaft.reports.support.MatrixLoader.Route;
import br.com.felipeblaft.reports.support.MatrixLoader.Row;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatrixLoaderTest {

    private static final String MINIMAL =
            """
            {
              "version": 1,
              "operations": [
                { "operation": "ADMIN_SIGN_IN", "administratorWeb": "PUBLIC", "teacherWeb": "PUBLIC",
                  "teacherTelegram": "DENY", "webRoutes": ["POST /admin/session"], "system": [] },
                { "operation": "STUDENT_REGISTER", "administratorWeb": "ALLOW",
                  "teacherWeb": "ALLOW_OWN_STUDENTS", "teacherTelegram": "ALLOW_OWN_STUDENTS",
                  "webRoutes": ["POST /admin/students", "/home/{studentId}/**"],
                  "system": ["INITIAL_LOAD"], "note": "uma observação" }
              ]
            }
            """;

    @Test
    void loadsTheRealContractWithEveryOperationOfTheCoreExactlyOnce() {
        Matrix matrix = MatrixLoader.load();

        assertThat(matrix.rows()).extracting(Row::operation).doesNotHaveDuplicates();
        assertThat(matrix.rows())
                .extracting(Row::operation)
                .containsExactlyInAnyOrder(Operation.values());
    }

    @Test
    void realContractCarriesTheKnownPermissions() {
        Matrix matrix = MatrixLoader.load();

        Row signIn = matrix.row(Operation.ADMIN_SIGN_IN);
        assertThat(signIn.administratorWeb()).isEqualTo(Permission.PUBLIC);
        assertThat(signIn.teacherWeb()).isEqualTo(Permission.PUBLIC);
        assertThat(signIn.teacherTelegram()).isEqualTo(Permission.DENY);
        assertThat(signIn.webRoutes()).containsExactly(new Route("POST", "/admin/session"));

        Row inactivate = matrix.row(Operation.STUDENT_INACTIVATE);
        assertThat(inactivate.teacherWeb()).isEqualTo(Permission.ALLOW_SOLE_ACTIVE_TEACHER);
        assertThat(inactivate.teacherTelegram()).isEqualTo(Permission.ALLOW_SOLE_ACTIVE_TEACHER);
        assertThat(inactivate.webRoutes()).hasSize(2);

        Row bootstrap = matrix.row(Operation.FIRST_ADMINISTRATOR_BOOTSTRAP);
        assertThat(bootstrap.webRoutes()).isEmpty();
        assertThat(bootstrap.system()).containsExactly(ActionOrigin.SYSTEM_STARTUP);

        assertThat(matrix.row(Operation.LESSON_AND_SEMESTER_REPORTS).administratorWeb())
                .isEqualTo(Permission.DENY);
    }

    @Test
    void parsesRowsRoutesAndSystemOrigins() {
        Matrix matrix = MatrixLoader.parse(MINIMAL);

        assertThat(matrix.rows()).hasSize(2);
        Row register = matrix.row(Operation.STUDENT_REGISTER);
        assertThat(register.administratorWeb()).isEqualTo(Permission.ALLOW);
        assertThat(register.teacherWeb()).isEqualTo(Permission.ALLOW_OWN_STUDENTS);
        assertThat(register.system()).isEqualTo(Set.of(ActionOrigin.INITIAL_LOAD));
        assertThat(register.note()).isEqualTo("uma observação");
        assertThat(matrix.row(Operation.ADMIN_SIGN_IN).note()).isNull();
    }

    @Test
    void routeWithoutMethodMatchesAnyMethod() {
        Route route = Route.parse("/home/{studentId}/**");

        assertThat(route.method()).isNull();
        assertThat(route.anyMethod()).isTrue();
        assertThat(route.path()).isEqualTo("/home/{studentId}/**");
        assertThat(Route.parse("DELETE /admin/session").anyMethod()).isFalse();
    }

    @Test
    void routeSubstitutesItsPathVariables() {
        Route route = Route.parse("PUT /admin/students/{studentId}/teachers/{teacherId}");

        assertThat(route.hasVariables()).isTrue();
        assertThat(route.pathWith(Map.of("studentId", "S1", "teacherId", "T1")))
                .isEqualTo("/admin/students/S1/teachers/T1");
        assertThat(Route.parse("GET /admin/session").hasVariables()).isFalse();
    }

    @Test
    void routeWithoutValueForAVariableIsRefused() {
        Route route = Route.parse("GET /admin/teachers/{teacherId}");

        assertThatThrownBy(() -> route.pathWith(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teacherId");
    }

    @Test
    void filtersOperationsDeniedToAnyTeacherChannel() {
        Matrix matrix = MatrixLoader.parse(MINIMAL);

        assertThat(matrix.operationsDeniedToAnyTeacherChannel())
                .containsExactly(Operation.ADMIN_SIGN_IN);
        assertThat(matrix.operationsWhere(row -> row.administratorWeb() == Permission.ALLOW))
                .containsExactly(Operation.STUDENT_REGISTER);
    }

    @Test
    void realContractDeniesTheAdministrationToBothTeacherChannels() {
        Matrix matrix = MatrixLoader.load();

        assertThat(matrix.operationsDeniedToAnyTeacherChannel())
                .contains(
                        Operation.TEACHER_REGISTER,
                        Operation.ADMINISTRATOR_CREATE,
                        Operation.ASSIGNMENT_ASSIGN,
                        Operation.ASSIGNMENT_PREVIEW_REMOVAL)
                .doesNotContain(Operation.STUDENT_LIST, Operation.STUDENT_REGISTER);
    }

    @Test
    void rowOfAbsentOperationIsRefused() {
        Matrix matrix = MatrixLoader.parse(MINIMAL);

        assertThatThrownBy(() -> matrix.row(Operation.TEACHER_LIST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEACHER_LIST");
    }

    @Test
    void unknownOperationIsRefusedWithItsName() {
        String json =
                """
                { "operations": [ { "operation": "FLY_TO_MOON", "administratorWeb": "ALLOW",
                  "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": [], "system": [] } ] }
                """;

        assertThatThrownBy(() -> MatrixLoader.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLY_TO_MOON");
    }

    @Test
    void unknownPermissionIsRefused() {
        String json =
                """
                { "operations": [ { "operation": "ADMIN_SIGN_IN", "administratorWeb": "MAYBE",
                  "teacherWeb": "DENY", "teacherTelegram": "DENY", "webRoutes": [], "system": [] } ] }
                """;

        assertThatThrownBy(() -> MatrixLoader.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAYBE");
    }

    @Test
    void duplicateOperationIsRefused() {
        String line =
                "{ \"operation\": \"ADMIN_SIGN_IN\", \"administratorWeb\": \"PUBLIC\","
                        + " \"teacherWeb\": \"PUBLIC\", \"teacherTelegram\": \"DENY\","
                        + " \"webRoutes\": [], \"system\": [] }";
        String json = "{ \"operations\": [" + line + "," + line + "] }";

        assertThatThrownBy(() -> MatrixLoader.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN_SIGN_IN");
    }

    @Test
    void missingFieldIsRefused() {
        String json = "{ \"operations\": [ { \"operation\": \"ADMIN_SIGN_IN\" } ] }";

        assertThatThrownBy(() -> MatrixLoader.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administratorWeb");
    }

    @Test
    void missingFileIsReportedWithItsPath() {
        Path absent = Path.of("missing-dir", "permission-matrix.json");

        assertThatThrownBy(() -> MatrixLoader.load(absent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permission-matrix.json");
    }
}
