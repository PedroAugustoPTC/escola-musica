package br.com.felipeblaft.reports.inbound.bootstrap;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.administrator.BootstrapFirstAdministratorUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Primeiro administrador (FR-034, contracts/initial-load.md §4): na subida, lê {@code
 * reports.administrator.bootstrap.*} (variáveis {@code ADMIN_BOOTSTRAP_NAME}, {@code
 * ADMIN_BOOTSTRAP_EMAIL} e {@code ADMIN_BOOTSTRAP_PASSWORD}) e chama o caso de uso {@link
 * BootstrapFirstAdministratorUseCase} com o ator de sistema {@code SYSTEM_STARTUP}. Quem decide é o
 * núcleo: se já existe algum administrador, ignora as variáveis (mesmo inválidas); se não existe e
 * as três vieram vazias, o sistema sobe e avisa em português; se não existe e algum valor é
 * inválido ou falta, <strong>recusa subir</strong> com mensagem que cita a variável, nunca o valor.
 * A senha nunca vai a log.
 *
 * <p>Não roda no perfil {@code seed} (comandos de terminal) nem no {@code test} (os testes chamam
 * {@link #bootstrap()} direto).
 */
@Component
@Profile("!seed & !test")
public class FirstAdministratorBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FirstAdministratorBootstrap.class);

    /** Campo do caso de uso → variável de ambiente que o alimenta. */
    private static final Map<String, String> VARIABLE_OF_FIELD =
            Map.of(
                    "name", "ADMIN_BOOTSTRAP_NAME",
                    "email", "ADMIN_BOOTSTRAP_EMAIL",
                    "password", "ADMIN_BOOTSTRAP_PASSWORD");

    private final BootstrapFirstAdministratorUseCase useCase;
    private final ApplicationProperties.Bootstrap configuration;
    private final MessageCatalog catalog;

    @Autowired
    public FirstAdministratorBootstrap(
            BootstrapFirstAdministratorUseCase useCase,
            ApplicationProperties properties,
            MessageCatalog catalog) {
        this(useCase, properties.administrator().bootstrap(), catalog);
    }

    /** Para os testes, que escolhem os valores sem montar todas as propriedades. */
    FirstAdministratorBootstrap(
            BootstrapFirstAdministratorUseCase useCase,
            ApplicationProperties.Bootstrap configuration,
            MessageCatalog catalog) {
        this.useCase = useCase;
        this.configuration = configuration;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        bootstrap();
    }

    /**
     * Executa a verificação da subida; lança {@link IllegalStateException} para recusar a subida.
     */
    public void bootstrap() {
        String name = text(configuration == null ? null : configuration.name());
        String email = text(configuration == null ? null : configuration.email());
        String password = text(configuration == null ? null : configuration.password());
        boolean allBlank = name.isBlank() && email.isBlank() && password.isBlank();
        try {
            BootstrapFirstAdministratorUseCase.Outcome outcome =
                    useCase.run(
                            Actor.system(ActionOrigin.SYSTEM_STARTUP),
                            new BootstrapFirstAdministratorUseCase.Command(
                                    name, email, new Secret(password)));
            if (outcome == BootstrapFirstAdministratorUseCase.Outcome.CREATED) {
                LOG.info(
                        "Primeiro administrador criado a partir das variáveis ADMIN_BOOTSTRAP_*."
                                + " A senha é provisória e deve ser trocada no primeiro acesso.");
            } else if (!allBlank) {
                LOG.info(
                        "Já existe administrador: as variáveis ADMIN_BOOTSTRAP_* foram ignoradas.");
            }
        } catch (DomainException refusal) {
            if (refusal.code() != ErrorCode.VALIDATION_FAILED) {
                throw refusal;
            }
            if (allBlank) {
                LOG.warn(
                        "Nenhum administrador existe e as variáveis ADMIN_BOOTSTRAP_NAME,"
                                + " ADMIN_BOOTSTRAP_EMAIL e ADMIN_BOOTSTRAP_PASSWORD não foram"
                                + " informadas: ninguém conseguirá entrar na área administrativa."
                                + " Preencha-as no arquivo .env e reinicie o serviço.");
                return;
            }
            throw new IllegalStateException(refusalMessage(refusal, name, email, password));
        }
    }

    /** Cita só a variável e o motivo do catálogo; nunca o valor informado. */
    private String refusalMessage(
            DomainException refusal, String name, String email, String password) {
        List<String> problems = new ArrayList<>();
        Object fields = refusal.details().get("fields");
        if (fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry) {
                    String field = String.valueOf(entry.get("field"));
                    String variable = VARIABLE_OF_FIELD.getOrDefault(field, field);
                    boolean missing =
                            switch (field) {
                                case "name" -> name.isBlank();
                                case "email" -> email.isBlank();
                                case "password" -> password.isBlank();
                                default -> false;
                            };
                    problems.add(
                            missing
                                    ? variable + " não informada"
                                    : variable + " inválida (" + reason(entry.get("code")) + ")");
                }
            }
        }
        if (problems.isEmpty()) {
            problems.add("variáveis ADMIN_BOOTSTRAP_* inválidas");
        }
        return "Não foi possível criar o primeiro administrador: "
                + String.join("; ", problems)
                + ". Corrija o valor no arquivo .env e reinicie o serviço.";
    }

    private String reason(Object code) {
        try {
            ErrorCode errorCode = ErrorCode.valueOf(String.valueOf(code));
            return catalog.contains(errorCode.messageKey())
                    ? catalog.text(errorCode.messageKey())
                    : errorCode.name();
        } catch (IllegalArgumentException unknown) {
            return "valor recusado";
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
