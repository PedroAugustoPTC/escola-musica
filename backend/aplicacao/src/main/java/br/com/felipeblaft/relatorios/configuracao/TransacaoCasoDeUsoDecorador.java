package br.com.felipeblaft.relatorios.configuracao;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * Spring (Princípio I). Os casos de uso são classes simples; o proxy é aplicado aos beans cujo
 * nome termina em {@code CasoDeUso}.
 */
@Configuration(proxyBeanMethods = false)
public class TransacaoCasoDeUsoDecorador {

    static final String PACOTE_CASOS_DE_USO = "br.com.felipeblaft.relatorios.casodeuso";

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static TransactionInterceptor transacaoCasoDeUsoInterceptor() {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(new MatchAlwaysTransactionAttributeSource());
        return interceptor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor transacaoCasoDeUsoAdvisor(TransactionInterceptor transacaoCasoDeUsoInterceptor) {
        return new DefaultPointcutAdvisor(new CasoDeUsoPointcut(), transacaoCasoDeUsoInterceptor);
    }

    static final class CasoDeUsoPointcut extends StaticMethodMatcherPointcut {

        @Override
        public boolean matches(Method metodo, Class<?> classeAlvo) {
            return classeAlvo.getName().startsWith(PACOTE_CASOS_DE_USO)
                    && classeAlvo.getSimpleName().endsWith("CasoDeUso")
                    && Modifier.isPublic(metodo.getModifiers())
                    && metodo.getDeclaringClass() != Object.class;
        }
    }
}
