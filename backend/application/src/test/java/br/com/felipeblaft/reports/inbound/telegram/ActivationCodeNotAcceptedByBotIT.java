package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.ConversationState;
import org.junit.jupiter.api.Test;

/**
 * Código de ativação e senha de administrador enviados ao bot não vinculam nada (US7): só o código
 * de acesso do professor é aceito, e todo outro texto recebe a mesma recusa que um código errado
 * (ou a orientação de entrada, se nem tem cara de código).
 */
@ConversationState({1})
class ActivationCodeNotAcceptedByBotIT extends RegistryConversationBase {

    private static final String ENTRY =
            "Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.";

    @Test
    void anEightCharacterAdministratorPasswordIsJustAWrongCode() {
        send(OTHER_USER, "Admin2026");

        assertThat(lastText()).isEqualTo("Código inválido ou vencido. Procure o administrador.");
        assertThat(links()).isEqualTo(1);
    }

    @Test
    void aTwelveCharacterActivationCodeLinksNothing() {
        send(OTHER_USER, "K7QM2XPAB3NT");
        send(OTHER_USER, "K7QM-2XPA-B3NT");

        assertThat(lastText()).isEqualTo(ENTRY);
        assertThat(links()).isEqualTo(1);
    }

    @Test
    void longPasswordsAndEmailsGetTheEntryOrientationAndNoState() {
        for (String text :
                new String[] {"S3nh@Forte-2026", "admin@example.test", "Senha provisória 12"}) {
            send(OTHER_USER, text);
            assertThat(lastText()).isEqualTo(ENTRY);
        }
        assertThat(links()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from telegram_link where telegram_user_id = ?",
                                Integer.class,
                                OTHER_USER))
                .isZero();
    }

    @Test
    void theTeacherAccessCodeStillLinks() {
        scenario.bruno();

        send(OTHER_USER, "BRUN2XPA");

        assertThat(lastText()).startsWith("Conta vinculada com sucesso, Bruno!");
        assertThat(links()).isEqualTo(2);
    }
}
