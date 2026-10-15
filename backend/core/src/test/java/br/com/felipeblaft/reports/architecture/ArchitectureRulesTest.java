package br.com.felipeblaft.reports.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Locale;
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

    private final JavaClasses core =
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
}
