package br.com.felipeblaft.reports.architecture.fixture;

import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;

/**
 * Entrada do professor que referencia um caso de uso e um repositório do administrador (apenas
 * autoteste do ArchUnit).
 */
public class TeacherSurfaceFixture {

    private RegisterTeacherUseCase registration;
    private AdministratorRepository administrators;

    public Object both() {
        return registration == null ? administrators : registration;
    }
}
