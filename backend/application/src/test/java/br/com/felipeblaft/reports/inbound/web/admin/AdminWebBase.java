package br.com.felipeblaft.reports.inbound.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.administrator.ActivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ChangeProvisionalPasswordUseCase;
import br.com.felipeblaft.reports.usecase.administrator.CreateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import br.com.felipeblaft.reports.usecase.administrator.InactivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ListAdministratorsUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ReactivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignInAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignOutAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ValidateAdministratorSessionUseCase;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.PreviewAssignmentRemovalUseCase;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RemoveTeacherFromStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import br.com.felipeblaft.reports.usecase.teacher.GetTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.InactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ListTeachersUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReissueAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.teacher.UnlinkTeacherTelegramUseCase;
import br.com.felipeblaft.reports.usecase.teacher.UpdateTeacherUseCase;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Base dos testes da camada Web da administração (feature 005): MockMvc contra o contexto real
 * (cadeias de segurança, CSRF, tratamento de erros) com <b>todos</b> os casos de uso da área
 * administrativa substituídos por dublês, para provar rota, status, corpo, cabeçalhos, cookies e
 * mapeamento de erros sem regra de negócio. Um só conjunto de dublês mantém um só contexto em cache
 * para todas as suítes que herdam desta classe. A requisição e a resposta são validadas contra
 * {@code contracts/api-administration.openapi.yaml}.
 */
@AutoConfigureMockMvc
public abstract class AdminWebBase extends IntegrationBase {

    public static final String CONTRACT_RELATIVE =
            "specs/005-admin-registrations-access/contracts/api-administration.openapi.yaml";
    public static final String ADMIN_COOKIE = "admin_session";
    public static final String ADMIN_TOKEN = "admin-token-1";
    public static final String BASE = "/api/v1/admin";

    private static final OpenApiInteractionValidator VALIDATOR = createValidator();

    @Autowired protected MockMvc mvc;
    @Autowired protected ScenarioIT scenario;

    @MockitoBean protected ValidateAdministratorSessionUseCase validateSession;
    @MockitoBean protected SignInAdministratorUseCase signIn;
    @MockitoBean protected GetAdministratorSessionUseCase getSession;
    @MockitoBean protected SignOutAdministratorUseCase signOut;
    @MockitoBean protected ActivateAdministratorUseCase activate;
    @MockitoBean protected ChangeProvisionalPasswordUseCase changePassword;
    @MockitoBean protected ListAdministratorsUseCase listAdministrators;
    @MockitoBean protected CreateAdministratorUseCase createAdministrator;
    @MockitoBean protected InactivateAdministratorUseCase inactivateAdministrator;
    @MockitoBean protected ReactivateAdministratorUseCase reactivateAdministrator;

    @MockitoBean protected ListTeachersUseCase listTeachers;
    @MockitoBean protected GetTeacherUseCase getTeacher;
    @MockitoBean protected RegisterTeacherUseCase registerTeacher;
    @MockitoBean protected UpdateTeacherUseCase updateTeacher;
    @MockitoBean protected InactivateTeacherUseCase inactivateTeacher;
    @MockitoBean protected ReactivateTeacherUseCase reactivateTeacher;
    @MockitoBean protected ReissueAccessCodeUseCase reissueCode;
    @MockitoBean protected UnlinkTeacherTelegramUseCase unlinkTelegram;

    @MockitoBean protected ListStudentsUseCase listStudents;
    @MockitoBean protected GetStudentUseCase getStudent;
    @MockitoBean protected RegisterStudentUseCase registerStudent;
    @MockitoBean protected UpdateStudentUseCase updateStudent;
    @MockitoBean protected InactivateStudentUseCase inactivateStudent;
    @MockitoBean protected ReactivateStudentUseCase reactivateStudent;
    @MockitoBean protected AssignTeacherToStudentUseCase assignTeacher;
    @MockitoBean protected RemoveTeacherFromStudentUseCase removeTeacher;
    @MockitoBean protected PreviewAssignmentRemovalUseCase previewRemoval;

    protected final UUID administratorId = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    protected Instant sessionExpiry;

    @BeforeEach
    void configureDefaultSessions() {
        sessionExpiry = clock.now().plusSeconds(7200);
        // token desconhecido: sessão inexistente (o caso de uso real responde assim)
        willThrow(new DomainException(ErrorCode.SESSION_EXPIRED)).given(validateSession).run(any());
        authenticate(ADMIN_TOKEN, false);
    }

    /** O token passa a identificar um administrador com a sessão fixa de duas horas. */
    protected void authenticate(String token, boolean passwordChangeRequired) {
        willReturn(
                        new ValidateAdministratorSessionUseCase.Result(
                                Actor.administrator(administratorId, passwordChangeRequired),
                                sessionExpiry))
                .given(validateSession)
                .run(new Secret(token));
    }

    /** A sessão do token deixa de valer (revogada, encerrada ou vencida). */
    protected void revoke(String token, ErrorCode reason) {
        willThrow(new DomainException(reason)).given(validateSession).run(new Secret(token));
    }

    protected static Cookie adminCookie(String token) {
        return new Cookie(ADMIN_COOKIE, token);
    }

    /** Requisição de administrador autenticado com o token de CSRF. */
    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B asAdmin(B request) {
        return request.cookie(adminCookie(ADMIN_TOKEN)).with(csrf().asHeader());
    }

    protected static ResultMatcher followsContract() {
        return OpenApiValidationMatchers.openApi().isValid(VALIDATOR);
    }

    private static OpenApiInteractionValidator createValidator() {
        return OpenApiInteractionValidator.createForSpecificationUrl(
                        contractPath().toUri().toString())
                .withLevelResolver(
                        LevelResolver.create()
                                .withLevel(
                                        "validation.request.security.missing",
                                        ValidationReport.Level.IGNORE)
                                // allOf do contrato (AdministratorStudentInput): o validador trata cada parte
                                // como fechada e recusa campos que a outra parte define
                                .withLevel(
                                        "validation.request.body.schema.additionalProperties",
                                        ValidationReport.Level.IGNORE)
                                .withLevel(
                                        "validation.request.parameter.header.missing",
                                        ValidationReport.Level.IGNORE)
                                .build())
                .build();
    }

    /** Caminho do contrato (fonte de verdade); {@code CONTRACT_ADMIN_API} o sobrescreve. */
    public static Path contractPath() {
        String configured = System.getenv("CONTRACT_ADMIN_API");
        if (configured != null && Files.exists(Path.of(configured))) {
            return Path.of(configured);
        }
        return Path.of("../..", CONTRACT_RELATIVE).toAbsolutePath().normalize();
    }
}
