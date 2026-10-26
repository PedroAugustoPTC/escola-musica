package br.com.felipeblaft.reports.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Consulta de CPF duplicado (FR-019, FR-024): a pesquisa por resumo do CPF é a única leitura de
 * aluno sem escopo; só o cadastro e a edição a usam, porque só eles sabem tratar o resultado sem
 * vazar dado de outro professor (administrador recebe o nome; professor, o código neutro).
 */
class StudentCpfRulesTest {

    private static final String BASE = "br.com.felipeblaft.reports";

    /** Cadastro e edição, com as classes aninhadas e geradas por eles. */
    private static final Set<String> ALLOWED =
            Set.of(
                    RegisterStudentUseCase.class.getName(),
                    UpdateStudentUseCase.class.getName(),
                    // o CPF digitado no Telegram é checado na hora, com as mesmas regras
                    CollectStudentRegistrationFieldUseCase.class.getName());

    private static final JavaClasses CORE =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE);

    private static boolean allowed(JavaClass javaClass) {
        String name = javaClass.getName();
        return ALLOWED.stream().anyMatch(name::startsWith);
    }

    @Test
    void onlyRegisterAndUpdateSearchStudentsByCpfHash() {
        noClasses()
                .that(
                        DescribedPredicate.describe(
                                "não são o cadastro nem a edição de aluno", c -> !allowed(c)))
                .and()
                .resideInAPackage(BASE + ".usecase.registry..")
                .should()
                .callMethodWhere(searchByCpfHash())
                .check(CORE);
    }

    @Test
    void registerAndUpdateDoUseIt() {
        long callers =
                CORE.stream()
                        .filter(StudentCpfRulesTest::allowed)
                        .filter(
                                c ->
                                        c.getMethodCallsFromSelf().stream()
                                                .anyMatch(searchByCpfHash()))
                        .count();
        assertThat(callers).isGreaterThanOrEqualTo(2);
    }

    private static DescribedPredicate<JavaMethodCall> searchByCpfHash() {
        return DescribedPredicate.describe(
                "chamam StudentRepository.searchByCpfHash",
                call ->
                        call.getTargetOwner().isEquivalentTo(StudentRepository.class)
                                && call.getName().equals("searchByCpfHash"));
    }
}
