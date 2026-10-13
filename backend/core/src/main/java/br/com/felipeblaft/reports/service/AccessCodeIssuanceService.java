package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AccessCodeGeneratorPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.time.Instant;
import java.util.Objects;

/**
 * Gera um código novo e único para o professor, guarda só o hash e reinicia a validade (R-12). O
 * código em claro é devolvido apenas a quem chamou, para a saída do terminal.
 */
public class AccessCodeIssuanceService {

    public static final int UNIQUENESS_ATTEMPTS = 10;

    private final AccessCodeGeneratorPort generator;
    private final DataProtectionPort protection;
    private final TeacherRepository teachers;
    private final BusinessParameters parameters;

    public AccessCodeIssuanceService(
            AccessCodeGeneratorPort generator,
            DataProtectionPort protection,
            TeacherRepository teachers,
            BusinessParameters parameters) {
        this.generator = Objects.requireNonNull(generator);
        this.protection = Objects.requireNonNull(protection);
        this.teachers = Objects.requireNonNull(teachers);
        this.parameters = Objects.requireNonNull(parameters);
    }

    /** Substitui o código do professor (sem gravar) e devolve o novo código em claro. */
    public AccessCode emit(Teacher teacher, Instant now) {
        for (int attempt = 0; attempt < UNIQUENESS_ATTEMPTS; attempt++) {
            AccessCode code = generator.generate();
            String hash = protection.hashAccessCode(code);
            boolean inUse =
                    teachers.searchByAccessCodeHash(hash)
                            .filter(other -> !other.id().equals(teacher.id()))
                            .isPresent();
            if (!inUse) {
                teacher.replaceCode(hash, now, parameters.codeValidity());
                return code;
            }
        }
        throw new IllegalStateException("não foi possível gerar um código de acesso único");
    }
}
