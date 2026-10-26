package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.access.EndTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.IdentifyTelegramTeacherUseCase;
import br.com.felipeblaft.reports.usecase.access.LinkTelegramUseCase;
import br.com.felipeblaft.reports.usecase.access.UpdateTelegramConversationUseCase;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * O estado de "Meus alunos" vive no backend (Princípio II): quem responde o update seguinte pode
 * ser outra instância do interpretador (reinício do transporte) e a conversa continua do mesmo
 * ponto, inclusive as posições da lista e o token do resumo.
 */
@ConversationState({10})
class StudentRegistryRestartResilienceIT extends RegistryConversationBase {

    @Autowired private ObjectProvider<TelegramFlow> flows;
    @Autowired private IdentifyTelegramTeacherUseCase identify;
    @Autowired private LinkTelegramUseCase linkUseCase;
    @Autowired private EndTelegramLinkUseCase endLink;
    @Autowired private UpdateTelegramConversationUseCase update;
    @Autowired private GetTeacherHomeUseCase home;
    @Autowired private SearchStudentsUseCase search;
    @Autowired private TelegramPresenter presenter;
    @Autowired private ClockPort clockPort;
    @Autowired private BusinessParameters parameters;

    /** Instância nova do interpretador: nada em memória vem da anterior. */
    private TelegramConversationInterpreter restart() {
        return new TelegramConversationInterpreter(
                identify,
                linkUseCase,
                endLink,
                update,
                home,
                search,
                presenter,
                clockPort,
                parameters,
                flows);
    }

    @Test
    void registrationContinuesAfterTheTransportRestarts() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_BIRTH_DATE");

        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        out = restart().interpret(textUpdate("05/03/1990"));

        assertThat(lastText()).startsWith("Qual é o CPF do aluno?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");

        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        out = restart().interpret(textUpdate(CPF));
        assertThat(lastText()).startsWith("Confira o cadastro:");

        when(confirm.run(any(), any())).thenReturn(view("Ana Souza", 36, null, true, null));
        out = restart().interpret(callbackUpdate("v1:reg-confirm:" + TOKEN));
        assertThat(lastText()).startsWith("Pronto! Ana Souza");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void theListPositionsSurviveTheRestartAndNoNameIsStored() {
        StudentView bia = view("Bia Lima", 12, "Violão", true, "Marta Lima");
        when(list.run(any(), any()))
                .thenReturn(page(0, 2, view("Ana Souza", 36, null, true, null), bia));
        send("meus alunos");
        assertThat(persistedData()).contains(bia.id().toString()).doesNotContain("Bia");

        when(get.run(any(), any())).thenReturn(bia);
        out = restart().interpret(callbackUpdate("v1:reg-open:1"));

        assertThat(lastText()).startsWith("Bia Lima\n12 anos");
        assertThat(persistedStep()).isEqualTo("REGISTRY_CARD");
    }

    private UpdateTelegram textUpdate(String text) {
        return new UpdateTelegram(
                9001,
                new UpdateTelegram.TelegramMessage(
                        900,
                        new UpdateTelegram.TelegramUser(USER, false),
                        new UpdateTelegram.ChatTelegram(USER, "private"),
                        text,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null);
    }

    private UpdateTelegram callbackUpdate(String data) {
        return new UpdateTelegram(
                9002,
                null,
                new UpdateTelegram.CallbackTelegram(
                        "cbr",
                        new UpdateTelegram.TelegramUser(USER, false),
                        new UpdateTelegram.TelegramMessage(
                                55,
                                null,
                                new UpdateTelegram.ChatTelegram(USER, "private"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        data));
    }
}
