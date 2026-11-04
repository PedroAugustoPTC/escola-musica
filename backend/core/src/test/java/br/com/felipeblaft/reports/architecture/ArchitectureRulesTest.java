package br.com.felipeblaft.reports.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.architecture.fixture.MutableRecorderFixture;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Regras de camada e de nomenclatura do núcleo (Princípios I e VI). */
class ArchitectureRulesTest {

    private static final String BASE = "br.com.felipeblaft.reports";

    /** Nomes que não podem aparecer no núcleo (Princípio I: nenhum fornecedor, SDK ou modelo). */
    private static final List<String> VENDORS =
            List.of(
                    "groq",
                    "openai",
                    "whisper",
                    "llama",
                    "ffmpeg",
                    "openhtmltopdf",
                    "thymeleaf",
                    "postgres",
                    "spring",
                    "n8n");

    private static final JavaClasses core =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE);

    private static final DescribedPredicate<JavaClass> BEHAVIOR_CLASS =
            DescribedPredicate.describe(
                    "classe de primeiro nível que não é record, enum nem interface",
                    javaClass ->
                            javaClass.isTopLevelClass()
                                    && !javaClass.isRecord()
                                    && !javaClass.isEnum()
                                    && !javaClass.isInterface());

    @Test
    void domainDoesNotDependOnUseCasesServicesNorPorts() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BASE + ".usecase..", BASE + ".service..", BASE + ".port..")
                .check(core);
    }

    @Test
    void portsDoNotDependOnUseCasesNorServices() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".port..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BASE + ".usecase..", BASE + ".service..")
                .check(core);
    }

    @Test
    void servicesDoNotDependOnUseCases() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".service..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".usecase..")
                .check(core);
    }

    @Test
    void useCasesEndWithUseCase() {
        classes()
                .that()
                .resideInAPackage(BASE + ".usecase..")
                .and(BEHAVIOR_CLASS)
                .should()
                .haveSimpleNameEndingWith("UseCase")
                .check(core);
    }

    @Test
    void servicesEndWithService() {
        classes()
                .that()
                .resideInAPackage(BASE + ".service..")
                .and(BEHAVIOR_CLASS)
                .should()
                .haveSimpleNameEndingWith("Service")
                .check(core);
    }

    @Test
    void portsEndWithPortOrRepository() {
        classes()
                .that()
                .resideInAPackage(BASE + ".port..")
                .and()
                .areInterfaces()
                .and()
                .areTopLevelClasses()
                .and(
                        DescribedPredicate.describe(
                                "não são tipos de dados (selados ou entradas)",
                                javaClass ->
                                        !javaClass.reflect().isSealed()
                                                && !javaClass.getSimpleName().endsWith("Input")))
                .should()
                .haveSimpleNameEndingWith("Port")
                .orShould()
                .haveSimpleNameEndingWith("Repository")
                .check(core);
    }

    @Test
    void coreDoesNotMentionVendors() {
        classes().should(doNotMentionVendor()).check(core);
    }

    @Test
    void coreDoesNotDependOnFrameworks() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "com.fasterxml..",
                        "tools.jackson..")
                .check(core);
    }

    // ---- Feature 005 (T026): camadas, autorização, escopo e autoria
    // -------------------------------

    private static final String USE_CASE_PACKAGE = BASE + ".usecase";
    private static final String FIXTURE_PACKAGE = BASE + ".architecture.fixture";

    /**
     * Operação da matriz (contracts/permission-matrix.json) de cada caso de uso dos pacotes {@code
     * usecase.administrator}, {@code usecase.teacher} e {@code usecase.registry}. O que a matriz
     * diz de cada operação vem da própria {@link PermissionMatrix}, que o teste de deriva compara
     * com o contrato; aqui só se liga o caso de uso à operação.
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
                    // cadastro do professor no Telegram: serve a STUDENT_REGISTER e STUDENT_UPDATE,
                    // ambas permitidas ao professor sobre os próprios alunos
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
                    // feature 007: consentimento do responsável
                    Map.entry("RecordConsentUseCase", Operation.CONSENT_RECORD),
                    Map.entry("RevokeConsentUseCase", Operation.CONSENT_REVOKE),
                    Map.entry("PublishConsentTermUseCase", Operation.CONSENT_TERM_PUBLISH),
                    Map.entry("GetCurrentConsentTermUseCase", Operation.CONSENT_TERM_READ),
                    Map.entry("ListConsentTermsUseCase", Operation.CONSENT_TERM_READ),
                    Map.entry("ListPendingConsentsUseCase", Operation.CONSENT_PENDING_READ),
                    Map.entry("ListStudentConsentsUseCase", Operation.CONSENT_READ),
                    Map.entry(
                            "GetSchoolReportDocumentUseCase",
                            Operation.SCHOOL_REPORT_DOCUMENT_OPEN));

    /** Casos de uso da visão da escola (feature 006), fora dos pacotes de administração. */
    private static final Set<String> SCHOOL_USE_CASES =
            Set.of(
                    "QuerySchoolHistoryUseCase",
                    "GetSchoolReportDocumentUseCase",
                    "QueryAuditTrailUseCase",
                    "VerifyAuditChainUseCase",
                    "RecordConsentUseCase",
                    "RevokeConsentUseCase",
                    "PublishConsentTermUseCase",
                    "GetCurrentConsentTermUseCase",
                    "ListConsentTermsUseCase",
                    "ListPendingConsentsUseCase",
                    "ListStudentConsentsUseCase");

    /** Casos de uso desses pacotes sem operação na matriz (validam a credencial, não um ator). */
    private static final Set<String> WITHOUT_OPERATION =
            Set.of("ValidateAdministratorSessionUseCase");

    /**
     * Chamadas de {@code TelegramLinkRepository.remove} fora do serviço de encerramento. Está
     * vazio: todo fim de vínculo passa pelo {@code TelegramLinkTerminationService}; {@link
     * #linkRemovalExemptionsAreStillNeeded()} reprova a isenção que um dia sobrar.
     */
    private static final Set<String> PENDING_LINK_REMOVAL = Set.of();

    private static final DescribedPredicate<JavaClass> ADMINISTRATION_USE_CASES =
            DescribedPredicate.describe(
                    "casos de uso de administrador, professor e cadastro de aluno",
                    javaClass ->
                            javaClass.isTopLevelClass()
                                    && javaClass.getSimpleName().endsWith("UseCase")
                                    && (inPackage(javaClass, ".administrator")
                                            || inPackage(javaClass, ".teacher")
                                            || inPackage(javaClass, ".registry")
                                            || SCHOOL_USE_CASES.contains(
                                                    javaClass.getSimpleName())));

    private static boolean inPackage(JavaClass javaClass, String suffix) {
        return javaClass.getPackageName().startsWith(USE_CASE_PACKAGE + suffix);
    }

    @Test
    void layersInsideTheCoreDoNotKnowTheAdapters() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        BASE + ".usecase..",
                        BASE + ".service..",
                        BASE + ".domain..",
                        BASE + ".port..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BASE + ".inbound..", BASE + ".outbound..")
                .allowEmptyShould(true)
                .check(core);
    }

    @Test
    void domainHasNoSpringNorPersistence() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "org.hibernate..")
                .check(core);
    }

    // -- toda operação com credencial começa pela autorização do ator (FR-002)

    @Test
    void everyAdministrationUseCaseIsMappedToAnOperationOrExplicitlyWithoutOne() {
        Set<String> found =
                core.stream()
                        .filter(ADMINISTRATION_USE_CASES)
                        .map(JavaClass::getSimpleName)
                        .collect(Collectors.toSet());
        Set<String> known = new HashSet<>(OPERATION_OF.keySet());
        known.addAll(WITHOUT_OPERATION);
        assertThat(found)
                .as("todo caso de uso novo precisa ser ligado a uma Operation da matriz")
                .isEqualTo(known);
    }

    @Test
    void everyCredentialedUseCaseDependsOnActorAuthorization() {
        authorizationRule(ADMINISTRATION_USE_CASES).check(core);
    }

    @Test
    void useCaseWithoutAuthorizationIsCaught() {
        assertThat(
                        authorizationRule(JavaClass.Predicates.resideInAPackage(FIXTURE_PACKAGE))
                                .evaluate(fixtureClasses())
                                .hasViolation())
                .isTrue();
    }

    @Test
    void publicAndSystemOperationsAreExemptFromTheAuthorizationRule() {
        assertThat(exemptFromAuthorization("SignInAdministratorUseCase")).isTrue();
        assertThat(exemptFromAuthorization("ActivateAdministratorUseCase")).isTrue();
        assertThat(exemptFromAuthorization("BootstrapFirstAdministratorUseCase")).isTrue();
        assertThat(exemptFromAuthorization("ValidateAdministratorSessionUseCase")).isTrue();
        assertThat(exemptFromAuthorization("ListTeachersUseCase")).isFalse();
        assertThat(exemptFromAuthorization("StartStudentRegistrationUseCase")).isFalse();
        assertThat(exemptFromAuthorization("UnknownUseCase")).isFalse();
    }

    /**
     * Dispensa a autorização quem não tem papel permitido pela matriz (só PUBLIC, DENY ou sistema).
     */
    private static boolean exemptFromAuthorization(String useCase) {
        Operation operation = OPERATION_OF.get(useCase);
        if (operation == null) {
            return WITHOUT_OPERATION.contains(useCase);
        }
        PermissionMatrix.Rule rule = PermissionMatrix.standard().rules().get(operation);
        return !(requiresCredential(rule.administratorWeb())
                || requiresCredential(rule.teacherWeb())
                || requiresCredential(rule.teacherTelegram()));
    }

    private static boolean requiresCredential(Permission permission) {
        return permission != Permission.PUBLIC && permission != Permission.DENY;
    }

    private static ArchRule authorizationRule(DescribedPredicate<JavaClass> scope) {
        return classes()
                .that(scope)
                .and(
                        DescribedPredicate.describe(
                                "exigem credencial pela matriz",
                                javaClass -> !exemptFromAuthorization(javaClass.getSimpleName())))
                .should()
                .dependOnClassesThat()
                .areAssignableTo(ActorAuthorizationService.class);
    }

    // -- escopo de alunos: leitura sem escopo fora do cadastro; com escopo só pelo serviço

    @Test
    void registryUseCasesDoNotReadStudentsOutsideTheScope() {
        scopelessReadRule(JavaClass.Predicates.resideInAPackage(USE_CASE_PACKAGE + ".registry.."))
                .check(core);
    }

    @Test
    void scopelessStudentReadIsCaught() {
        assertThat(
                        scopelessReadRule(JavaClass.Predicates.resideInAPackage(FIXTURE_PACKAGE))
                                .evaluate(fixtureClasses())
                                .hasViolation())
                .isTrue();
    }

    @Test
    void scopedStudentReadsAreOnlyCalledByStudentAccessService() {
        scopedReadRule(StudentAccessService.class).check(core);
    }

    @Test
    void scopedStudentReadFromOutsideTheServiceIsCaught() {
        assertThat(
                        scopedReadRule(StudentAccessService.class)
                                .evaluate(fixtureClasses())
                                .hasViolation())
                .isTrue();
    }

    /**
     * Leituras de aluno que dispensam o {@code StudentScope}; o resumo do CPF (duplicidade) pode.
     */
    private static ArchRule scopelessReadRule(DescribedPredicate<JavaClass> scope) {
        return noClasses()
                .that(scope)
                .should()
                .callMethodWhere(calls(StudentRepository.class, "searchById", "listByTeacher"))
                .allowEmptyShould(true);
    }

    private static ArchRule scopedReadRule(Class<?> onlyCaller) {
        return noClasses()
                .that()
                .doNotHaveFullyQualifiedName(onlyCaller.getName())
                .should()
                .callMethodWhere(calls(StudentRepository.class, "findVisible", "list"));
    }

    // -- autoria: o registro só recebe inserção

    @Test
    void authorshipRecorderOnlyRecords() {
        appendOnlyRule(AuthorshipRecorderPort.class).check(core);
    }

    @Test
    void mutableAuthorshipRecorderIsCaught() {
        assertThat(
                        appendOnlyRule(MutableRecorderFixture.class)
                                .evaluate(fixtureClasses())
                                .hasViolation())
                .isTrue();
    }

    private static ArchRule appendOnlyRule(Class<?> port) {
        return methods()
                .that()
                .areDeclaredIn(port)
                .should()
                .haveNameMatching("record(Independent)?");
    }

    // -- todo fim de vínculo do Telegram passa pelo mesmo caminho

    @Test
    void onlyTheTerminationServiceRemovesTelegramLinks() {
        linkRemovalRule(TelegramLinkTerminationService.class, PENDING_LINK_REMOVAL).check(core);
    }

    @Test
    void linkRemovalOutsideTheTerminationServiceIsCaught() {
        assertThat(
                        linkRemovalRule(TelegramLinkTerminationService.class, Set.of())
                                .evaluate(fixtureClasses())
                                .hasViolation())
                .isTrue();
    }

    @Test
    void linkRemovalExemptionsAreStillNeeded() {
        Set<String> stillRemoving =
                core.stream()
                        .filter(javaClass -> PENDING_LINK_REMOVAL.contains(javaClass.getFullName()))
                        .filter(
                                javaClass ->
                                        javaClass.getMethodCallsFromSelf().stream()
                                                .anyMatch(
                                                        calls(
                                                                TelegramLinkRepository.class,
                                                                "remove")))
                        .map(JavaClass::getFullName)
                        .collect(Collectors.toSet());
        assertThat(stillRemoving)
                .as("isenção sem chamada: remova do PENDING_LINK_REMOVAL")
                .isEqualTo(PENDING_LINK_REMOVAL);
    }

    private static ArchRule linkRemovalRule(Class<?> onlyCaller, Set<String> exempt) {
        return noClasses()
                .that()
                .doNotHaveFullyQualifiedName(onlyCaller.getName())
                .and(
                        DescribedPredicate.describe(
                                "não estão na lista de migração pendente",
                                javaClass -> !exempt.contains(javaClass.getFullName())))
                .should()
                .callMethodWhere(calls(TelegramLinkRepository.class, "remove"));
    }

    private static DescribedPredicate<JavaMethodCall> calls(Class<?> owner, String... names) {
        List<String> accepted = List.of(names);
        return DescribedPredicate.describe(
                "chamam " + owner.getSimpleName() + "." + accepted,
                call ->
                        call.getTargetOwner().isEquivalentTo(owner)
                                && accepted.contains(call.getName()));
    }

    /** Classes de apoio que violam as regras de propósito (autotestes), só dos testes. */
    private static JavaClasses fixtureClasses() {
        return new ClassFileImporter().importPackages(FIXTURE_PACKAGE);
    }

    private static ArchCondition<JavaClass> doNotMentionVendor() {
        return new ArchCondition<>("não citar nome de fornecedor no nome da classe nem do pacote") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String name = javaClass.getFullName().toLowerCase(Locale.ROOT);
                for (String vendor : VENDORS) {
                    if (name.contains(vendor)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        javaClass, javaClass.getFullName() + " cita " + vendor));
                    }
                }
                javaClass.getDirectDependenciesFromSelf().stream()
                        .map(dependency -> dependency.getTargetClass().getBaseComponentType())
                        .filter(target -> !target.isPrimitive())
                        .map(JavaClass::getFullName)
                        .filter(target -> !target.startsWith("java."))
                        .filter(target -> !target.startsWith(BASE))
                        .forEach(
                                target ->
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        javaClass,
                                                        javaClass.getFullName()
                                                                + " depende de "
                                                                + target)));
            }
        };
    }

    /**
     * FR-034, SC-005: todo caso de uso que enfileira trabalho de IA depende da barreira de
     * consentimento. Quem enfileira trabalho sem IA entra na lista, com o motivo.
     */
    private static final Set<String> ENQUEUE_WITHOUT_AI = Set.of();

    @Test
    void everyUseCaseThatEnqueuesAiWorkDependsOnTheConsentGuard() {
        List<String> missing =
                core.stream()
                        .filter(c -> c.getPackageName().startsWith(BASE + ".usecase"))
                        .filter(c -> c.isTopLevelClass() && c.getSimpleName().endsWith("UseCase"))
                        .filter(
                                c ->
                                        c.getMethodCallsFromSelf().stream()
                                                .anyMatch(
                                                        call ->
                                                                call.getTargetOwner()
                                                                                .getName()
                                                                                .equals(
                                                                                        BASE
                                                                                                + ".port.JobQueuePort")
                                                                        && call.getName()
                                                                                .equals("enqueue")))
                        .filter(
                                c ->
                                        c.getDirectDependenciesFromSelf().stream()
                                                .noneMatch(
                                                        d ->
                                                                d.getTargetClass()
                                                                        .getName()
                                                                        .equals(
                                                                                BASE
                                                                                        + ".service.ConsentGuardService")))
                        .map(JavaClass::getSimpleName)
                        .filter(name -> !ENQUEUE_WITHOUT_AI.contains(name))
                        .sorted()
                        .toList();
        assertThat(missing).as("casos de uso que enfileiram IA sem a barreira").isEmpty();
    }
}
