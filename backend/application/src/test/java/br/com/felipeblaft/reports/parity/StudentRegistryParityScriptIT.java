package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.parity.ChannelDriver.Guardian;
import br.com.felipeblaft.reports.parity.ChannelDriver.NewStudent;
import br.com.felipeblaft.reports.support.AdministrationStackBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Paridade Web ≡ Telegram do cadastro de alunos do professor (SC-003, R-16): o mesmo roteiro
 * (cadastrar, listar, buscar, abrir, editar, inativar, reativar, aluno compartilhado negado, CPF
 * indisponível e bloqueio por sondagem) roda em cada canal, sobre o mesmo ponto de partida, e
 * produz o mesmo resultado de negócio. Pilha real, Postgres real; o banco é refeito entre os dois
 * canais.
 */
class StudentRegistryParityScriptIT extends AdministrationStackBase {

    private static final String CPF_OTHER = "529.982.247-25";
    private static final String CPF_SHARED = "168.995.350-09";
    private static final String CPF_ANA = "111.444.777-35";
    private static final String CPF_DAVI = "390.533.447-05";
    private static final String CPF_FREE = "935.411.347-80";

    @Autowired MessageCatalog catalog;

    /** Ponto de partida: Clara com um aluno compartilhado com Bruno, que tem outro aluno. */
    private String seed() throws Exception {
        Cookie admin = signInAdministrator(insertAdministrator());
        TeacherAccount clara = createTeacher(admin, "Clara Souza", "clara@example.test");
        TeacherAccount bruno = createTeacher(admin, "Bruno Lima", "bruno@example.test");
        registerStudentAsAdministrator(
                admin, adultStudent("Matteo Mafra", CPF_SHARED), clara.id(), bruno.id());
        registerStudentAsAdministrator(admin, adultStudent("Helena Prado", CPF_OTHER), bruno.id());
        return clara.accessCode();
    }

    private TelegramChannelDriver telegram() {
        return new TelegramChannelDriver(
                        text -> {
                            API_BOTS.resetRequests();
                            sendText(USER_CLARA, text);
                            return calls("sendMessage");
                        },
                        catalog,
                        () -> jdbc.queryForObject("select count(*) from telegram_link", Long.class))
                .withTaps(
                        data -> {
                            API_BOTS.resetRequests();
                            sendCallback(USER_CLARA, data);
                            return calls("sendMessage");
                        });
    }

    private static NewStudent adult(String name, String cpf) {
        return new NewStudent(name, "05/03/1990", cpf, "Piano", null);
    }

    private ParityScript script(String code) {
        ParityScript script =
                new ParityScript()
                        .step("sign-in", c -> c.signIn(code))
                        .step("lista inicial", c -> c.listStudents("ACTIVE"))
                        .step(
                                "cadastrar adulto",
                                c -> c.registerStudent(adult("Ana Souza", CPF_ANA)))
                        .step(
                                "cadastrar menor",
                                c ->
                                        c.registerStudent(
                                                new NewStudent(
                                                        "Davi Lima",
                                                        "14/03/2016",
                                                        CPF_DAVI,
                                                        "Violão",
                                                        new Guardian(
                                                                "Marta Lima",
                                                                "(11) 98888-7777",
                                                                "marta.lima@example.test"))))
                        .step("lista com os novos", c -> c.listStudents("ACTIVE"))
                        .step("search", c -> c.searchStudents("ana"))
                        .step("abrir o menor", c -> c.openStudent("Davi Lima"))
                        .step(
                                "editar o instrumento",
                                c -> c.changeInstrument("Ana Souza", "Violão"))
                        .step("abrir o editado", c -> c.openStudent("Ana Souza"))
                        .step("inactivate", c -> c.inactivateStudent("Ana Souza"))
                        .step("lista dos ativos", c -> c.listStudents("ACTIVE"))
                        .step("lista dos inativos", c -> c.listStudents("INACTIVE"))
                        .step("reactivate", c -> c.reactivateStudent("Ana Souza"))
                        .step(
                                "aluno compartilhado negado",
                                c -> c.inactivateStudent("Matteo Mafra"))
                        .step(
                                "CPF de aluno meu",
                                c -> c.registerStudent(adult("Ana Repetida", CPF_ANA)))
                        .step(
                                "CPF indisponível",
                                c -> c.registerStudent(adult("Novo 1", CPF_OTHER)));
        for (int i = 2; i <= 5; i++) {
            String name = "Novo " + i;
            script.step("CPF indisponível " + i, c -> c.registerStudent(adult(name, CPF_OTHER)));
        }
        return script.step(
                        "bloqueada pela sondagem",
                        c -> c.registerStudent(adult("Novo 6", CPF_OTHER)))
                .step(
                        "bloqueada também com CPF livre",
                        c -> c.registerStudent(adult("Novo 7", CPF_FREE)))
                .step("o resto do uso continua", c -> c.listStudents("ACTIVE"))
                .step(
                        "depois do bloqueio",
                        c -> {
                            clock.advance(Duration.ofMinutes(15));
                            return c.registerStudent(adult("Novo 8", CPF_FREE));
                        })
                .step("encerrar", ChannelDriver::end);
    }

    @Test
    void sameScriptSameBusinessResultInBothChannels() throws Exception {
        List<String> web = script(seed()).run(new ChannelWebDriver(mvc, "198.51.100.20"));
        truncateAll();
        List<String> bot = script(seed()).run(telegram());

        assertThat(bot).containsExactlyElementsOf(web);
        assertThat(web)
                .contains(
                        "sign-in = SUCCESS",
                        "lista inicial = [Matteo Mafra]",
                        "cadastrar adulto = SUCCESS",
                        "cadastrar menor = SUCCESS",
                        "lista com os novos = [Ana Souza, Davi Lima, Matteo Mafra]",
                        "search = [Ana Souza]",
                        "abrir o menor = Davi Lima|10|Violão|ACTIVE",
                        "editar o instrumento = SUCCESS",
                        "abrir o editado = Ana Souza|36|Violão|ACTIVE",
                        "inactivate = SUCCESS",
                        "lista dos ativos = [Davi Lima, Matteo Mafra]",
                        "lista dos inativos = [Ana Souza]",
                        "reactivate = SUCCESS",
                        "aluno compartilhado negado = SHARED_STUDENT_ACTION_DENIED",
                        "CPF de aluno meu = STUDENT_ALREADY_YOURS",
                        "CPF indisponível = CPF_UNAVAILABLE",
                        "bloqueada pela sondagem = OPERATION_TEMPORARILY_BLOCKED",
                        "bloqueada também com CPF livre = OPERATION_TEMPORARILY_BLOCKED",
                        "depois do bloqueio = SUCCESS",
                        "encerrar = ENDED");
    }
}
