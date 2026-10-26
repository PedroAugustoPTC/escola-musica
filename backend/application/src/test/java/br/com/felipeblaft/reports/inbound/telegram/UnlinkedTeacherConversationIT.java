package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Desvinculação e reemissão do código (US6), vistas pelo bot: depois de desvinculada a conta
 * recebe a orientação de entrada; o código antigo responde como código inexistente e o novo
 * vincula. Reemitir o código sem encerrar o vínculo não mexe num cadastro em andamento; encerrar o
 * vínculo o descarta. A reemissão e a desvinculação são do núcleo (S2): o teste as reproduz pelo
 * estado que deixam (código novo no professor, vínculo removido).
 */
@ConversationState({1, 10})
class UnlinkedTeacherConversationIT extends RegistryConversationBase {

    private static final String NEW_CODE = "NEWC2XPA";
    private static final String ENTRY =
            "Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.";

    @Autowired private TeacherRepository teachers;
    @Autowired private DataProtectionPort protection;
    @Autowired private ClockPort clockPort;

    private void reissueCode() {
        var teacher = teachers.searchById(clara.id()).orElseThrow();
        teacher.replaceCode(
                protection.hashAccessCode(AccessCode.of(NEW_CODE)),
                clockPort.now(),
                Duration.ofDays(7));
        teachers.save(teacher);
    }

    private void unlink() {
        jdbc.update("delete from telegram_link");
    }

    @Test
    void afterUnlinkingTheAccountReceivesCodeRequiredAndTheOldCodeIsInvalid() {
        reissueCode();
        unlink();

        send("meus alunos");
        assertThat(lastText()).isEqualTo(ENTRY);

        send(ScenarioIT.CODE_CLARA);
        assertThat(lastText()).isEqualTo("Código inválido ou vencido. Procure o administrador.");
        assertThat(links()).isZero();

        send(NEW_CODE);
        assertThat(lastText()).startsWith("Conta vinculada com sucesso, Clara!");
        assertThat(links()).isEqualTo(1);
    }

    @Test
    void reissuingWithoutEndingTheLinkKeepsARegistrationInProgress() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");

        reissueCode();

        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");
        assertThat(lastText()).startsWith("Qual é o CPF do aluno?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");
        assertThat(links()).isEqualTo(1);
    }

    @Test
    void endingTheLinkDiscardsTheRegistrationInProgress() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");

        reissueCode();
        unlink();

        send("05/03/1990");
        assertThat(lastText()).isEqualTo(ENTRY);
        verify(collect, org.mockito.Mockito.times(1)).run(any(), any());
        verify(confirm, never()).run(any(), any());

        send(NEW_CODE);
        assertThat(persistedFlow()).isEqualTo("NONE");
    }
}
