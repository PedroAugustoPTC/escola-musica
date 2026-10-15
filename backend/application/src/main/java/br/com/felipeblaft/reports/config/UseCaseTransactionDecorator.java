package br.com.felipeblaft.reports.config;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Envolve cada método público dos casos de uso do núcleo numa transação, sem que o núcleo conheça
 * Spring (Princípio I). Os casos de uso são classes simples; o proxy é aplicado aos beans cujo nome
 * termina em {@code CasoDeUso}.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseTransactionDecorator {

    static final String USE_CASE_PACKAGE = "br.com.felipeblaft.reports.usecase";

    /**
     * Casos de uso que ficam fora de uma transação única: a entrega proativa faz chamada de rede e
     * não pode segurar uma conexão do banco (FR-015); a leitura de PDF por identificador opaco
     * captura a exceção de outro caso de uso (que, dentro da mesma transação, a marcaria como
     * só-reversão e faria o commit falhar). Cada gravação deles é atômica por si só.
     */
    static final Set<String> NO_TRANSACTION =
            Set.of("DeliverPendingActionsUseCase", "GetChannelDocumentUseCase");

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static TransactionInterceptor useCaseTransactionInterceptor() {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(new MatchAlwaysTransactionAttributeSource());
        return interceptor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor useCaseTransactionAdvisor(TransactionInterceptor useCaseTransactionInterceptor) {
        return new DefaultPointcutAdvisor(new UseCasePointcut(), useCaseTransactionInterceptor);
    }

    static final class UseCasePointcut extends StaticMethodMatcherPointcut {

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return targetClass.getName().startsWith(USE_CASE_PACKAGE)
                    && targetClass.getSimpleName().endsWith("UseCase")
                    && !NO_TRANSACTION.contains(targetClass.getSimpleName())
                    && Modifier.isPublic(method.getModifiers())
                    && method.getDeclaringClass() != Object.class;
        }
    }
}
