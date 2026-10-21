package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;

/**
 * Cadastra um professor ativo ({@code TEACHER_REGISTER}), com código de acesso de 8 caracteres e 7
 * dias exibido uma única vez; e-mail único sem diferenciar caixa nem espaços, inclusive entre
 * inativos. Serve também à carga inicial (ator de sistema). Esqueleto da Fundação; a regra é do
 * fluxo W-B.
 */
public class RegisterTeacherUseCase {

    public record Command(String name, String email, String correlationId) {

        @Override
        public String toString() {
            return "RegisterTeacherUseCase.Command";
        }
    }

    public RegisterTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {}

    public TeacherWithCode run(Actor actor, Command command) {
        throw new UnsupportedOperationException("RegisterTeacherUseCase: fluxo W-B");
    }
}
