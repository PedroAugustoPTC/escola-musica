package br.com.felipeblaft.relatorios.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/** Nomenclatura e dependências dos adaptadores (Princípios I e VI). */
class RegrasAdaptadoresTeste {

    private static final String BASE = "br.com.felipeblaft.relatorios";

    private final JavaClasses aplicacao =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                    .importPackages(BASE);

    @Test
    void controladoresRestTerminamEmControlador() {
        classes()
                .that()
                .resideInAPackage(BASE + ".entrada.web..")
                .and()
                .areAnnotatedWith(RestController.class)
                .should()
                .haveSimpleNameEndingWith("Controlador")
                .allowEmptyShould(true)
                .check(aplicacao);
    }

    @Test
    void implementacoesDePortasTerminamEmAdaptador() {
        classes()
                .that()
                .resideInAPackage(BASE + ".saida..")
                .and()
                .implement(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "porta ou repositório do núcleo",
                                classe ->
                                        classe.getPackageName().equals(BASE + ".porta")
                                                && classe.isInterface()))
                .should()
                .haveSimpleNameEndingWith("Adaptador")
                .check(aplicacao);
    }

    @Test
    void objetosDeBordaDaWebTerminamEmDtoRequisicaoOuResposta() {
        classes()
                .that()
                .resideInAPackage(BASE + ".entrada.web")
                .and()
                .areRecords()
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("Dto")
                .orShould()
                .haveSimpleNameEndingWith("Requisicao")
                .orShould()
                .haveSimpleNameEndingWith("Resposta")
                .allowEmptyShould(true)
                .check(aplicacao);
    }

    @Test
    void entradaNaoAcessaSaidaDiretamente() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".entrada..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".saida..")
                .check(aplicacao);
    }

}
