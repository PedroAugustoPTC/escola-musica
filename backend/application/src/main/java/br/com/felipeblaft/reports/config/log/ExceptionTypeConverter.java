package br.com.felipeblaft.reports.config.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Exceção só pelo tipo (a classe e a da causa raiz), sem mensagem nem pilha: a mensagem do
 * Hibernate e do PostgreSQL traz valores dos dados (por exemplo {@code Key (email)=(...)}). Usado
 * no perfil {@code seed}.
 */
public class ExceptionTypeConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        IThrowableProxy error = event.getThrowableProxy();
        if (error == null) {
            return "";
        }
        IThrowableProxy root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String text = " [exceção: " + simpleName(error.getClassName());
        if (root != error) {
            text += "; causa raiz: " + simpleName(root.getClassName());
        }
        return text + "]";
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
