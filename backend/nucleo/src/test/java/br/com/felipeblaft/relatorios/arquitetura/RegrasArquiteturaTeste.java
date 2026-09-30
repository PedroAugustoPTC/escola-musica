package br.com.felipeblaft.relatorios.arquitetura;

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
class RegrasArquiteturaTeste {

    private static final String BASE = "br.com.felipeblaft.relatorios";

    /** Nomes que não podem aparecer no núcleo (Princípio I: nenhum fornecedor, SDK ou modelo). */
    private static final List<String> FORNECEDORES =
            List.of(
                    "groq",
                    "openai",
                    "whisper",
                    "llama",
                    "ffmpeg",
                    "openhtmltopdf",
                    "thymeleaf",
                    "postgres",
                    "spring");

    private final JavaClasses nucleo =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE);

    private static final DescribedPredicate<JavaClass> CLASSE_DE_COMPORTAMENTO =
            DescribedPredicate.describe(
                    "classe de primeiro nível que não é record, enum nem interface",
                    classe ->
                            classe.isTopLevelClass()
                                    && !classe.isRecord()
                                    && !classe.isEnum()
                                    && !classe.isInterface());

    @Test
    void dominioNaoDependeDeCasosDeUsoServicosNemPortas() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".dominio..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BASE + ".casodeuso..", BASE + ".servico..", BASE + ".porta..")
                .check(nucleo);
    }

    @Test
    void portasNaoDependemDeCasosDeUsoNemServicos() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".porta..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(BASE + ".casodeuso..", BASE + ".servico..")
                .check(nucleo);
    }

    @Test
    void servicosNaoDependemDeCasosDeUso() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".servico..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".casodeuso..")
                .check(nucleo);
    }

    @Test
    void casosDeUsoTerminamEmCasoDeUso() {
        classes()
                .that()
                .resideInAPackage(BASE + ".casodeuso..")
                .and(CLASSE_DE_COMPORTAMENTO)
                .should()
                .haveSimpleNameEndingWith("CasoDeUso")
                .check(nucleo);
    }

    @Test
    void servicosTerminamEmServico() {
        classes()
                .that()
                .resideInAPackage(BASE + ".servico..")
                .and(CLASSE_DE_COMPORTAMENTO)
                .should()
                .haveSimpleNameEndingWith("Servico")
                .check(nucleo);
    }

    @Test
    void portasTerminamEmPortaOuRepositorio() {
        classes()
                .that()
                .resideInAPackage(BASE + ".porta..")
                .and()
                .areInterfaces()
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("Porta")
                .orShould()
                .haveSimpleNameEndingWith("Repositorio")
                .check(nucleo);
    }

    @Test
    void nucleoNaoCitaFornecedores() {
        classes()
                .should(naoCitarFornecedor())
                .check(nucleo);
    }

    @Test
    void nucleoNaoDependeDeFrameworks() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "com.fasterxml..",
                        "tools.jackson..")
                .check(nucleo);
    }

    private static ArchCondition<JavaClass> naoCitarFornecedor() {
        return new ArchCondition<>("não citar nome de fornecedor no nome da classe nem do pacote") {
            @Override
            public void check(JavaClass classe, ConditionEvents eventos) {
                String nome = classe.getFullName().toLowerCase(Locale.ROOT);
                for (String fornecedor : FORNECEDORES) {
                    if (nome.contains(fornecedor)) {
                        eventos.add(
                                SimpleConditionEvent.violated(
                                        classe, classe.getFullName() + " cita " + fornecedor));
                    }
                }
                classe.getDirectDependenciesFromSelf().stream()
                        .map(dependencia -> dependencia.getTargetClass().getBaseComponentType())
                        .filter(alvo -> !alvo.isPrimitive())
                        .map(JavaClass::getFullName)
                        .filter(alvo -> !alvo.startsWith("java."))
                        .filter(alvo -> !alvo.startsWith(BASE))
                        .forEach(
                                alvo ->
                                        eventos.add(
                                                SimpleConditionEvent.violated(
                                                        classe,
                                                        classe.getFullName()
                                                                + " depende de "
                                                                + alvo)));
            }
        };
    }
}
