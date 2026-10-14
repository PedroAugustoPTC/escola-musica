package br.com.felipeblaft.reports.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/** Nomenclatura e dependências dos adaptadores (Princípios I e VI). */
class AdapterRulesTest {

    private static final String BASE = "br.com.felipeblaft.reports";

    private final JavaClasses application =
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
                                classe ->
                                        classe.getPackageName().equals(BASE + ".port")
                                                && classe.isInterface()))
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
}
