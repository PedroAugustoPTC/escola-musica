package br.com.felipeblaft.reports.usecase.seed;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import java.time.Instant;
import java.util.Objects;

/**
 * Reemite o código de acesso de um professor (comando {@code emitir-codigo}). Substitui o anterior
 * e reinicia a validade; não encerra sessões nem vínculos (contracts/carga-inicial.md).
 */
public class EmitAccessCodeUseCase {

    private final TeacherRepository teachers;
    private final AccessCodeIssuanceService issuance;
    private final ClockPort clock;

    public EmitAccessCodeUseCase(
            TeacherRepository teachers, AccessCodeIssuanceService issuance, ClockPort clock) {
        this.teachers = Objects.requireNonNull(teachers);
        this.issuance = Objects.requireNonNull(issuance);
        this.clock = Objects.requireNonNull(clock);
    }

    public IssuedCode run(String email) {
        Teacher teacher =
                teachers.searchByEmail(Teacher.normalizeEmail(email))
                        .orElseThrow(() -> new DomainException(ErrorCode.PROFESSOR_INEXISTENTE));
        Instant now = clock.now();
        AccessCode code = issuance.emit(teacher, now);
        teachers.save(teacher);
        return new IssuedCode(teacher.email(), code.value(), teacher.accessCodeExpiresAt());
    }
}
