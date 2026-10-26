package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.service.ActivationCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Fiação da feature 005 (T025): o contexto sobe com os adaptadores-stub e cada caso de uso e
 * serviço do núcleo dos contextos de administrador, professor e cadastro de aluno é exatamente um
 * bean. A lista de classes vem do próprio código do núcleo, de modo que um caso de uso novo sem
 * bean reprova aqui.
 */
class UseCaseWiringTest extends IntegrationBase {

    private static final String BASE = "br.com.felipeblaft.reports";

    private static final List<String> USE_CASE_PACKAGES =
            List.of(
                    BASE + ".usecase.administrator",
                    BASE + ".usecase.teacher",
                    BASE + ".usecase.registry");

    @Autowired ApplicationContext context;

    private static List<Class<?>> coreClasses(boolean services) {
        return new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(BASE)
                        .stream()
                        .filter(JavaClass::isTopLevelClass)
                        .filter(javaClass -> !javaClass.isInterface() && !javaClass.isEnum())
                        // utilitário estático (construtor privado) não é bean
                        .filter(
                                javaClass ->
                                        javaClass.getConstructors().stream()
                                                .anyMatch(
                                                        constructor ->
                                                                constructor
                                                                        .getModifiers()
                                                                        .contains(
                                                                                JavaModifier
                                                                                        .PUBLIC)))
                        .filter(
                                javaClass ->
                                        services
                                                ? javaClass
                                                                .getPackageName()
                                                                .equals(BASE + ".service")
                                                        && javaClass
                                                                .getSimpleName()
                                                                .endsWith("Service")
                                                : javaClass.getSimpleName().endsWith("UseCase")
                                                        && (USE_CASE_PACKAGES.contains(
                                                                        javaClass.getPackageName())
                                                                || javaClass
                                                                        .getFullName()
                                                                        .equals(
                                                                                RotateCpfKeysUseCase
                                                                                        .class
                                                                                        .getName())))
                        .<Class<?>>map(JavaClass::reflect)
                        .collect(Collectors.toList());
    }

    @Test
    void everyAdministrationUseCaseIsExactlyOneBean() {
        List<Class<?>> useCases = coreClasses(false);
        assertThat(useCases).hasSizeGreaterThanOrEqualTo(30);
        for (Class<?> useCase : useCases) {
            assertThat(context.getBeanNamesForType(useCase))
                    .as("beans de %s", useCase.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    void everyCoreServiceIsExactlyOneBean() {
        List<Class<?>> services = coreClasses(true);
        assertThat(services).hasSizeGreaterThanOrEqualTo(15);
        for (Class<?> service : services) {
            assertThat(context.getBeanNamesForType(service))
                    .as("beans de %s", service.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    void foundationServicesUseTheStandardMatrix() {
        assertThat(context.getBean(PermissionMatrix.class).rules())
                .isEqualTo(PermissionMatrix.standard().rules());
        assertThat(context.getBean(ActorAuthorizationService.class)).isNotNull();
        assertThat(context.getBean(AuthorshipService.class)).isNotNull();
        assertThat(context.getBean(StudentAccessService.class)).isNotNull();
        assertThat(context.getBean(ActivationCodeIssuanceService.class)).isNotNull();
        assertThat(context.getBean(SessionRevocationService.class)).isNotNull();
        assertThat(context.getBean(TelegramLinkTerminationService.class)).isNotNull();
    }

    @Test
    void administrationUseCasesRunInsideATransaction() {
        for (Class<?> useCase : coreClasses(false)) {
            Object bean = context.getBean(useCase);
            if (UseCaseTransactionDecorator.NO_TRANSACTION.contains(useCase.getSimpleName())) {
                assertThat(AopUtils.isAopProxy(bean))
                        .as(
                                "%s controla a própria transação e não leva o proxy do decorador",
                                useCase.getSimpleName())
                        .isFalse();
                continue;
            }
            assertThat(AopUtils.isAopProxy(bean))
                    .as(
                            "%s deve rodar numa transação (proxy do decorador)",
                            useCase.getSimpleName())
                    .isTrue();
        }
    }
}
