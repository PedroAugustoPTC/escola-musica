package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeacherIdentityTest {

    @Test
    void normalizesNameAndEmail() {
        TeacherIdentity identity = TeacherIdentity.of("  Clara Souza ", "  Clara@School.TEST ");

        assertThat(identity.name()).isEqualTo("Clara Souza");
        assertThat(identity.email()).isEqualTo("clara@school.test");
    }

    @Test
    void acceptsTheNameLimits() {
        assertThat(TeacherIdentity.of("Al", "a@b.co").name()).isEqualTo("Al");
        assertThat(TeacherIdentity.of("a".repeat(120), "a@b.co").name()).hasSize(120);
    }

    @Test
    void refusesANameOutsideTheLimits() {
        for (String name : new String[] {"A", "a".repeat(121), "   ", null}) {
            assertThat(TeacherIdentity.violations(name, "a@b.co"))
                    .containsExactly(new TeacherIdentity.Violation("name", ErrorCode.INVALID_NAME));
        }
    }

    @Test
    void refusesControlCharactersInTheName() {
        assertThat(TeacherIdentity.violations("Ana\u0007 Souza", "a@b.co"))
                .containsExactly(new TeacherIdentity.Violation("name", ErrorCode.INVALID_TEXT));
    }

    @Test
    void refusesMalformedOrTooLongEmail() {
        for (String email :
                new String[] {
                    null,
                    "",
                    "ana",
                    "ana@",
                    "@school.test",
                    "a b@school.test",
                    "a@b",
                    "a".repeat(250) + "@b.co"
                }) {
            assertThat(TeacherIdentity.violations("Ana Souza", email))
                    .as(String.valueOf(email).length() > 40 ? "long" : String.valueOf(email))
                    .containsExactly(
                            new TeacherIdentity.Violation("email", ErrorCode.INVALID_EMAIL));
        }
    }

    @Test
    void accumulatesViolationsWithoutEchoingTheValues() {
        assertThatThrownBy(() -> TeacherIdentity.of("A", "segredo"))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                            assertThat(e.details())
                                    .containsEntry(
                                            "fields",
                                            List.of(
                                                    java.util.Map.of(
                                                            "field",
                                                            "name",
                                                            "code",
                                                            "INVALID_NAME"),
                                                    java.util.Map.of(
                                                            "field",
                                                            "email",
                                                            "code",
                                                            "INVALID_EMAIL")));
                            assertThat(e.details().toString()).doesNotContain("segredo");
                        });
    }

    @Test
    void toStringRevealsNothing() {
        assertThat(TeacherIdentity.of("Clara Souza", "clara@school.test").toString())
                .doesNotContain("Clara")
                .doesNotContain("school");
    }
}
