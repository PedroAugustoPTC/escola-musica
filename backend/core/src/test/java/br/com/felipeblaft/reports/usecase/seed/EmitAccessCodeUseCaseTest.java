package br.com.felipeblaft.reports.usecase.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedAccessCodeGenerator;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmitAccessCodeUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    private final FakeDataProtection protection = new FakeDataProtection();
    private final FixedAccessCodeGenerator generator = new FixedAccessCodeGenerator();
    private final AccessCodeIssuanceService issuance =
            new AccessCodeIssuanceService(
                    generator, protection, teachers, BusinessParameters.defaults());
    private final EmitAccessCodeUseCase useCase =
            new EmitAccessCodeUseCase(teachers, issuance, clock);

    @Test
    void reissuesReplacingHashAndRestartingValidity() {
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, clock.now());
        clara.replaceCode("codigo:ANTIGO22", clock.now(), Duration.ofDays(7));
        teachers.save(clara);
        clock.advance(Duration.ofDays(9));
        generator.schedule("NEVA2345");

        IssuedCode emitted = useCase.run(" Clara@Exemplo.TEST ");

        assertThat(emitted.email()).isEqualTo("clara@exemplo.test");
        assertThat(emitted.code()).isEqualTo("NEVA2345");
        assertThat(emitted.validUntil()).isEqualTo(clock.now().plus(Duration.ofDays(7)));
        Teacher saved = teachers.searchById(clara.id()).orElseThrow();
        assertThat(saved.accessCodeHash()).isEqualTo("codigo:NEVA2345");
        assertThat(saved.validCodeAt(clock.now())).isTrue();
    }

    @Test
    void missingTeacher() {
        assertThatThrownBy(() -> useCase.run("ninguem@exemplo.test"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.PROFESSOR_INEXISTENTE);
    }

    @Test
    void generatesAnotherCodeIfHashAlreadyBelongsToAnotherTeacher() {
        Teacher bruno = Teacher.create("Bruno", "bruno@exemplo.test", true, clock.now());
        bruno.replaceCode("codigo:REPETXDA", clock.now(), Duration.ofDays(7));
        teachers.save(bruno);
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, clock.now());
        teachers.save(clara);
        generator.schedule("REPETXDA", "UNXCA234");

        assertThat(useCase.run("clara@exemplo.test").code()).isEqualTo("UNXCA234");
    }

    @Test
    void sameCodeOfOwnTeacherIsAccepted() {
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, clock.now());
        clara.replaceCode("codigo:MESMA234", clock.now(), Duration.ofDays(7));
        teachers.save(clara);
        generator.schedule("MESMA234");

        assertThat(useCase.run("clara@exemplo.test").code()).isEqualTo("MESMA234");
    }

    @Test
    void givesUpAfterManyCollisions() {
        Teacher bruno = Teacher.create("Bruno", "bruno@exemplo.test", true, clock.now());
        bruno.replaceCode("codigo:REPETXDA", clock.now(), Duration.ofDays(7));
        teachers.save(bruno);
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, clock.now());
        teachers.save(clara);
        for (int i = 0; i < AccessCodeIssuanceService.UNIQUENESS_ATTEMPTS; i++) {
            generator.schedule("REPETXDA");
        }

        assertThatThrownBy(() -> useCase.run("clara@exemplo.test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sequentialGeneratorProducesValidCodes() {
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, clock.now());
        teachers.save(clara);
        assertThat(useCase.run("clara@exemplo.test").code()).hasSize(8);
    }
}
