package br.com.felipeblaft.reports.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/** Nomenclatura e dependências dos adaptadores (Princípios I e VI). */
class AdapterRulesTest {

    private static final String BASE = "br.com.felipeblaft.reports";

    private static final JavaClasses application =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                    .importPackages(BASE);

    @Test
    void restControllersEndWithController() {
        classes()
                .that()
                .resideInAPackage(BASE + ".inbound.web..")
                .and()
                .areAnnotatedWith(RestController.class)
                .should()
                .haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true)
                .check(application);
    }

    @Test
    void portImplementationsEndWithAdapter() {
        classes()
                .that()
                .resideInAPackage(BASE + ".outbound..")
                .and()
                .implement(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "porta ou repositório do núcleo",
                                javaClass ->
                                        javaClass.getPackageName().equals(BASE + ".port")
                                                && javaClass.isInterface()))
                .should()
                .haveSimpleNameEndingWith("Adapter")
                .check(application);
    }

    @Test
    void webBoundaryObjectsEndWithDtoRequestOrResponse() {
        classes()
                .that()
                .resideInAPackage(BASE + ".inbound.web")
                .and()
                .areRecords()
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("Dto")
                .orShould()
                .haveSimpleNameEndingWith("Request")
                .orShould()
                .haveSimpleNameEndingWith("Response")
                .allowEmptyShould(true)
                .check(application);
    }

    /** Feature 002 (FR-033): a nova porta de entrada segue a mesma nomenclatura do portal. */
    @Test
    void internalChannelControllersEndWithController() {
        classes()
                .that()
                .resideInAPackage(BASE + ".inbound.telegram.channel..")
                .and()
                .areAnnotatedWith(RestController.class)
                .should()
                .haveSimpleNameEndingWith("Controller")
                .check(application);
    }

    @Test
    void internalChannelBoundaryObjectsEndWithDtoRequestOrResponse() {
        classes()
                .that()
                .resideInAPackage(BASE + ".inbound.telegram.channel")
                .and()
                .areRecords()
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("Dto")
                .orShould()
                .haveSimpleNameEndingWith("Request")
                .orShould()
                .haveSimpleNameEndingWith("Response")
                .check(application);
    }

    @Test
    void internalServiceAreaDoesNotDependOnOutbound() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        BASE + ".inbound.internal..", BASE + ".inbound.telegram.channel..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".outbound..")
                .check(application);
    }

    @Test
    void inboundDoesNotAccessOutboundDirectly() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".inbound..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".outbound..")
                .check(application);
    }

    // ---- Feature 005 (T026): ator de sistema e fronteiras do professor ------------------------

    private static final String FIXTURE_PACKAGE = BASE + ".architecture.fixture";

    /** A aplicação com o núcleo (que vem como dependência, às vezes em jar), sem os testes. */
    private static final JavaClasses withCore =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE);

    /** Classes de apoio que violam as regras de propósito (autotestes), só dos testes. */
    private static final JavaClasses fixtures =
            new ClassFileImporter().importPackages(FIXTURE_PACKAGE);

    /**
     * Operação da matriz (contracts/permission-matrix.json) dos casos de uso de administrador,
     * professor e cadastro de aluno. A permissão de cada operação vem da {@link PermissionMatrix},
     * que o teste de deriva do núcleo compara com o contrato; aqui só se liga o caso de uso à
     * operação. A mesma ligação existe no teste de arquitetura do núcleo (que a confere por
     * inteiro).
     */
    private static final Map<String, Operation> OPERATION_OF =
            Map.ofEntries(
                    Map.entry("SignInAdministratorUseCase", Operation.ADMIN_SIGN_IN),
                    Map.entry("ActivateAdministratorUseCase", Operation.ADMIN_ACTIVATE),
                    Map.entry("GetAdministratorSessionUseCase", Operation.ADMIN_GET_SESSION),
                    Map.entry("SignOutAdministratorUseCase", Operation.ADMIN_SIGN_OUT),
                    Map.entry("ChangeProvisionalPasswordUseCase", Operation.ADMIN_CHANGE_PASSWORD),
                    Map.entry("ListAdministratorsUseCase", Operation.ADMINISTRATOR_LIST),
                    Map.entry("CreateAdministratorUseCase", Operation.ADMINISTRATOR_CREATE),
                    Map.entry("InactivateAdministratorUseCase", Operation.ADMINISTRATOR_INACTIVATE),
                    Map.entry("ReactivateAdministratorUseCase", Operation.ADMINISTRATOR_REACTIVATE),
                    Map.entry(
                            "BootstrapFirstAdministratorUseCase",
                            Operation.FIRST_ADMINISTRATOR_BOOTSTRAP),
                    Map.entry("ListTeachersUseCase", Operation.TEACHER_LIST),
                    Map.entry("GetTeacherUseCase", Operation.TEACHER_GET),
                    Map.entry("RegisterTeacherUseCase", Operation.TEACHER_REGISTER),
                    Map.entry("UpdateTeacherUseCase", Operation.TEACHER_UPDATE),
                    Map.entry("InactivateTeacherUseCase", Operation.TEACHER_INACTIVATE),
                    Map.entry("ReactivateTeacherUseCase", Operation.TEACHER_REACTIVATE),
                    Map.entry("ReissueAccessCodeUseCase", Operation.TEACHER_REISSUE_ACCESS_CODE),
                    Map.entry("UnlinkTeacherTelegramUseCase", Operation.TEACHER_UNLINK_TELEGRAM),
                    Map.entry("AssignTeacherToStudentUseCase", Operation.ASSIGNMENT_ASSIGN),
                    Map.entry("RemoveTeacherFromStudentUseCase", Operation.ASSIGNMENT_REMOVE),
                    Map.entry(
                            "PreviewAssignmentRemovalUseCase",
                            Operation.ASSIGNMENT_PREVIEW_REMOVAL),
                    Map.entry("ListStudentsUseCase", Operation.STUDENT_LIST),
                    Map.entry("GetStudentUseCase", Operation.STUDENT_GET),
                    Map.entry("RegisterStudentUseCase", Operation.STUDENT_REGISTER),
                    Map.entry("UpdateStudentUseCase", Operation.STUDENT_UPDATE),
                    Map.entry("InactivateStudentUseCase", Operation.STUDENT_INACTIVATE),
                    Map.entry("ReactivateStudentUseCase", Operation.STUDENT_REACTIVATE),
                    // cadastro do professor no Telegram: serve a STUDENT_REGISTER e STUDENT_UPDATE
                    Map.entry("StartStudentRegistrationUseCase", Operation.STUDENT_REGISTER),
                    Map.entry("CollectStudentRegistrationFieldUseCase", Operation.STUDENT_REGISTER),
                    Map.entry("ConfirmStudentRegistrationUseCase", Operation.STUDENT_REGISTER),
                    Map.entry("CancelStudentRegistrationUseCase", Operation.STUDENT_REGISTER),
                    // feature 006: visão da escola, só do administrador na Web
                    Map.entry("QuerySchoolHistoryUseCase", Operation.SCHOOL_HISTORY_QUERY),
                    // feature 007: trilha de auditoria, só do administrador (a retenção também
                    // verifica)
                    Map.entry("QueryAuditTrailUseCase", Operation.AUDIT_TRAIL_QUERY),
                    Map.entry("VerifyAuditChainUseCase", Operation.AUDIT_TRAIL_VERIFY),
                    Map.entry(
                            "GetSchoolReportDocumentUseCase",
                            Operation.SCHOOL_REPORT_DOCUMENT_OPEN));

    /** Casos de uso da visão da escola (feature 006), fora dos pacotes de administração. */
    private static final Set<String> SCHOOL_USE_CASES =
            Set.of(
                    "QuerySchoolHistoryUseCase",
                    "GetSchoolReportDocumentUseCase",
                    "QueryAuditTrailUseCase",
                    "VerifyAuditChainUseCase");

    /**
     * Casos de uso desses pacotes sem operação na matriz: validam a credencial do administrador.
     */
    private static final Set<String> WITHOUT_OPERATION =
            Set.of("ValidateAdministratorSessionUseCase");

    @Test
    void systemActorIsOnlyCreatedAtStartupAndInitialLoad() {
        systemActorRule(
                        DescribedPredicate.describe(
                                "estão fora de inbound.seed, inbound.bootstrap e da própria carga inicial",
                                javaClass ->
                                        !javaClass
                                                        .getFullName()
                                                        .equals(
                                                                LoadInitialRegistryUseCase.class
                                                                        .getName())
                                                && !javaClass
                                                        .getPackageName()
                                                        .startsWith(BASE + ".inbound.seed")
                                                && !javaClass
                                                        .getPackageName()
                                                        .startsWith(BASE + ".inbound.bootstrap")))
                .check(withCore);
    }

    @Test
    void scheduledSystemActorsAreOnlyCreatedByTheirTasks() {
        noClasses()
                .that()
                .doNotHaveFullyQualifiedName(Actor.class.getName())
                .and()
                .haveSimpleNameNotEndingWith("Task")
                .should()
                .callMethodWhere(
                        DescribedPredicate.describe(
                                "chamam Actor.retention ou Actor.alertEvaluation",
                                (JavaMethodCall call) ->
                                        call.getTargetOwner().isEquivalentTo(Actor.class)
                                                && (call.getName().equals("retention")
                                                        || call.getName()
                                                                .equals("alertEvaluation"))))
                .check(withCore);
    }

    @Test
    void systemActorCreatedOutsideTheAllowedPackagesIsCaught() {
        assertThat(
                        systemActorRule(
                                        DescribedPredicate.describe(
                                                "estão fora de inbound.seed e inbound.bootstrap",
                                                javaClass -> true))
                                .evaluate(fixtures)
                                .hasViolation())
                .isTrue();
    }

    private static ArchRule systemActorRule(DescribedPredicate<JavaClass> outsideAllowed) {
        return noClasses()
                .that(outsideAllowed)
                .and()
                .doNotHaveFullyQualifiedName(Actor.class.getName())
                .should()
                .callMethodWhere(
                        DescribedPredicate.describe(
                                "chamam Actor.system",
                                (JavaMethodCall call) ->
                                        call.getTargetOwner().isEquivalentTo(Actor.class)
                                                && call.getName().equals("system")));
    }

    @Test
    void everyAdministrationUseCaseIsKnown() {
        Set<String> found =
                withCore.stream()
                        .filter(AdapterRulesTest::isAdministrationUseCase)
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toSet());
        Set<String> known = new HashSet<>(OPERATION_OF.keySet());
        known.addAll(WITHOUT_OPERATION);
        assertThat(found)
                .as("todo caso de uso novo precisa ser ligado a uma Operation da matriz")
                .isEqualTo(known);
    }

    @Test
    void forbiddenSetFollowsThePermissionMatrix() {
        Set<String> forbidden = forbiddenForTeacherSurfaces();
        // administrador, professor (cadastro e acesso) e carga: nunca na entrada do professor
        assertThat(forbidden)
                .contains(
                        BASE + ".usecase.administrator.SignInAdministratorUseCase",
                        BASE + ".usecase.administrator.ValidateAdministratorSessionUseCase",
                        BASE + ".usecase.teacher.RegisterTeacherUseCase",
                        BASE + ".usecase.registry.AssignTeacherToStudentUseCase",
                        BASE + ".usecase.registry.RemoveTeacherFromStudentUseCase",
                        BASE + ".usecase.registry.PreviewAssignmentRemovalUseCase",
                        BASE + ".usecase.seed.LoadInitialRegistryUseCase");
        // o que o professor pode fazer sobre os próprios alunos continua permitido
        assertThat(forbidden)
                .doesNotContain(
                        BASE + ".usecase.registry.ListStudentsUseCase",
                        BASE + ".usecase.registry.GetStudentUseCase",
                        BASE + ".usecase.registry.RegisterStudentUseCase",
                        BASE + ".usecase.registry.UpdateStudentUseCase",
                        BASE + ".usecase.registry.InactivateStudentUseCase",
                        BASE + ".usecase.registry.ReactivateStudentUseCase",
                        BASE + ".usecase.registry.StartStudentRegistrationUseCase");
    }

    @Test
    void teacherSurfacesDoNotReachAdministratorUseCasesNorRepositories() {
        teacherSurfaceRule(teacherSurfaces(), forbiddenForTeacherSurfaces()).check(withCore);
    }

    @Test
    void schoolUseCasesAreForbiddenForTheTeacherSurfaces() {
        assertThat(forbiddenForTeacherSurfaces())
                .contains(
                        BASE + ".usecase.history.QuerySchoolHistoryUseCase",
                        BASE + ".usecase.document.GetSchoolReportDocumentUseCase");
    }

    /**
     * O Telegram não ganhou acesso à visão da escola (feature 006): nem pela consulta nem pelo PDF.
     */
    @Test
    void telegramDoesNotDependOnTheSchoolHistoryPortsNorTheOfficialDocumentService() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".inbound.telegram..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(BASE + ".port.ApprovedReportQueryPort")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(BASE + ".service.OfficialDocumentService")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(BASE + ".usecase.history.QuerySchoolHistoryUseCase")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(BASE + ".usecase.document.GetSchoolReportDocumentUseCase")
                .allowEmptyShould(true)
                .check(withCore);
    }

    @Test
    void teacherSurfaceReachingAdministratorCodeIsCaught() {
        assertThat(
                        teacherSurfaceRule(
                                        JavaClass.Predicates.resideInAPackage(FIXTURE_PACKAGE),
                                        forbiddenForTeacherSurfaces())
                                .evaluate(fixtures)
                                .hasViolation())
                .isTrue();
    }

    /** O Telegram e o controlador de "Meus alunos" (a classe nasce com a Web do professor). */
    private static DescribedPredicate<JavaClass> teacherSurfaces() {
        return DescribedPredicate.describe(
                "inbound.telegram ou inbound.web.MyStudentController",
                javaClass ->
                        javaClass.getPackageName().startsWith(BASE + ".inbound.telegram")
                                || javaClass
                                        .getFullName()
                                        .equals(BASE + ".inbound.web.MyStudentController"));
    }

    private static ArchRule teacherSurfaceRule(
            DescribedPredicate<JavaClass> surface, Set<String> forbiddenUseCases) {
        return noClasses()
                .that(surface)
                .should()
                .dependOnClassesThat(
                        DescribedPredicate.describe(
                                "são casos de uso negados ao professor ou repositórios do administrador",
                                javaClass ->
                                        forbiddenUseCases.contains(javaClass.getFullName())
                                                || javaClass.isAssignableTo(
                                                        AdministratorRepository.class)
                                                || javaClass.isAssignableTo(
                                                        AdministratorSessionRepository.class)))
                .allowEmptyShould(true);
    }

    /**
     * Casos de uso que o professor, na Web ou no Telegram, não pode alcançar: os de operação negada
     * a ele pela matriz (administrador, professor e associação), os sem operação (sessão do
     * administrador) e os de sistema da carga inicial (pacote {@code usecase.seed}).
     */
    private static Set<String> forbiddenForTeacherSurfaces() {
        Set<String> forbidden = new HashSet<>();
        for (JavaClass javaClass : withCore) {
            String name = javaClass.getSimpleName();
            if (isAdministrationUseCase(javaClass)) {
                Operation operation = OPERATION_OF.get(name);
                if (operation == null || deniedToTeacher(operation)) {
                    forbidden.add(javaClass.getFullName());
                }
            } else if (javaClass.isTopLevelClass()
                    && name.endsWith("UseCase")
                    && javaClass.getPackageName().startsWith(BASE + ".usecase.seed")) {
                forbidden.add(javaClass.getFullName());
            }
        }
        return forbidden;
    }

    private static boolean deniedToTeacher(Operation operation) {
        PermissionMatrix.Rule rule = PermissionMatrix.standard().rules().get(operation);
        return rule.teacherTelegram() == Permission.DENY || rule.teacherWeb() == Permission.DENY;
    }

    private static boolean isAdministrationUseCase(JavaClass javaClass) {
        List<String> packages = List.of(".administrator", ".teacher", ".registry");
        return javaClass.isTopLevelClass()
                && javaClass.getSimpleName().endsWith("UseCase")
                && (SCHOOL_USE_CASES.contains(javaClass.getSimpleName())
                        || packages.stream()
                                .anyMatch(
                                        suffix ->
                                                javaClass
                                                        .getPackageName()
                                                        .startsWith(BASE + ".usecase" + suffix)));
    }
}
