package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MessageCatalogTest {

    private static final List<String> FEATURE_KEYS =
            List.of(
                    "INVALID_CREDENTIALS",
                    "INVALID_ACTIVATION",
                    "ACCESS_ENDED",
                    "PASSWORD_CHANGE_REQUIRED",
                    "PASSWORD_CHANGE_NOT_REQUIRED",
                    "PASSWORD_LENGTH",
                    "PASSWORD_EQUALS_IDENTITY",
                    "PASSWORD_SAME_AS_PROVISIONAL",
                    "LAST_ADMINISTRATOR",
                    "VALIDATION_FAILED",
                    "INVALID_NAME",
                    "INVALID_EMAIL",
                    "INVALID_CPF",
                    "INVALID_BIRTH_DATE",
                    "INVALID_PHONE",
                    "INVALID_INSTRUMENT",
                    "INVALID_TEXT",
                    "GUARDIAN_REQUIRED",
                    "DUPLICATE_EMAIL",
                    "DUPLICATE_CPF",
                    "CPF_UNAVAILABLE",
                    "STUDENT_ALREADY_YOURS",
                    "SHARED_STUDENT_ACTION_DENIED",
                    "OPERATION_TEMPORARILY_BLOCKED",
                    "TEACHER_INACTIVE",
                    "STUDENT_INACTIVE",
                    "CONFIRMATION_EXPIRED",
                    "CONFIRMATION_ALREADY_DONE",
                    "CALLBACK_EXPIRED",
                    "OPERATION_NOT_SAVED",
                    "INTERNAL_ERROR",
                    "REGISTRY_MY_STUDENTS",
                    "REGISTRY_EMPTY_FILTER",
                    "REGISTRY_CARD",
                    "REGISTRY_ASK_NAME",
                    "REGISTRY_ASK_BIRTH_DATE",
                    "REGISTRY_ASK_CPF",
                    "REGISTRY_ASK_GUARDIAN_NAME",
                    "REGISTRY_ASK_GUARDIAN_PHONE",
                    "REGISTRY_ASK_GUARDIAN_EMAIL",
                    "REGISTRY_ASK_INSTRUMENT",
                    "REGISTRY_EDIT_FIELD",
                    "REGISTRY_CONFIRM_CREATE",
                    "REGISTRY_CONFIRM_UPDATE",
                    "REGISTRY_CPF_WILL_CHANGE",
                    "REGISTRY_SAVED",
                    "REGISTRY_UPDATED",
                    "REGISTRY_CANCELLED",
                    "REGISTRY_DRAFT_EXPIRED",
                    "CONFIRM_INACTIVATE_STUDENT",
                    "CONFIRM_REACTIVATE_STUDENT",
                    "STUDENT_INACTIVATED",
                    "STUDENT_REACTIVATED",
                    "CPF_MESSAGE_NOT_DELETED",
                    "GUARDIAN_PHONE_LOOKS_LIKE_CPF",
                    "ADMIN_AREA_TITLE",
                    "ADMIN_SIGN_IN_TITLE",
                    "ADMIN_LINK_FROM_SIGN_IN",
                    "ADMIN_HAVE_ACTIVATION_CODE",
                    "ADMIN_SESSION_EXPIRES_AT",
                    "ADMIN_SESSION_EXPIRES_SOON",
                    "ADMIN_SECRET_SHOWN_ONCE",
                    "ADMIN_SECRET_COPIED",
                    "ADMIN_SECRET_ACKNOWLEDGE",
                    "ADMIN_CONFIRM_INACTIVATE_TEACHER",
                    "ADMIN_CONFIRM_REISSUE_CODE",
                    "ADMIN_END_TELEGRAM_OPTION",
                    "ADMIN_CONFIRM_UNLINK_TELEGRAM",
                    "ADMIN_CONFIRM_REMOVE_ASSIGNMENT",
                    "ADMIN_CONFIRM_INACTIVATE_ADMINISTRATOR",
                    "ADMIN_ACTIVATION_PENDING",
                    "ADMIN_ACTIVATED_AT",
                    "ADMIN_FORCE_PASSWORD_TITLE",
                    "MY_STUDENTS_TITLE",
                    "MY_STUDENTS_EMPTY",
                    "MY_STUDENTS_SEARCH_EMPTY",
                    "MY_STUDENTS_GUARDIAN_SECTION",
                    "MY_STUDENTS_CPF_KEEP",
                    "MY_STUDENTS_CONFIRM_INACTIVATE");

    private final MessageCatalog catalog = new MessageCatalog();

    @Test
    void everyErrorCodeHasText() {
        assertThat(
                        Arrays.stream(ErrorCode.values())
                                .map(ErrorCode::messageKey)
                                .filter(key -> !catalog.contains(key)))
                .as("códigos sem texto no catálogo")
                .isEmpty();
    }

    @Test
    void replacesPlaceholdersAndKeepsAccents() {
        assertThat(catalog.text("GREETING", Map.of("name", "Clara"))).isEqualTo("Olá, Clara");
        assertThat(catalog.text("CHANGE_APPLIED", Map.of("version", 2)))
                .isEqualTo("Alteração aplicada. Confira a versão 2.");
        assertThat(catalog.text("UNDERSTOOD_TEXT", Map.of("text", "troque $1 por \\x")))
                .isEqualTo("Entendi: “troque $1 por \\x”");
        assertThat(catalog.text("NO_STUDENT_FOUND", Map.of()))
                .isEqualTo("Nenhum aluno encontrado para \"\".");
        assertThat(catalog.text(ErrorCode.REPORT_CANCELLED, Map.of()))
                .isEqualTo("Este relatório foi cancelado e não pode mais ser alterado.");
        assertThat(catalog.text("EMPTY_SECTION")).isEqualTo("—");
    }

    @Test
    void missingKeyFails() {
        assertThat(catalog.keys()).contains("CODE_REQUIRED", "WEB_RESULT_NOTICE_READY");
        assertThatThrownBy(() -> catalog.text("DOES_NOT_EXIST"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noTextMentionsTheSecretariat() {
        assertThat(catalog.keys())
                .as("textos que citam a secretaria (FR-042)")
                .filteredOn(
                        key ->
                                catalog.model(key)
                                        .toLowerCase(Locale.ROOT)
                                        .contains("secre" + "taria"))
                .isEmpty();
    }

    @Test
    void removedCodesLeaveTheCatalog() {
        assertThat(catalog.contains("EXPIRED_CODE")).isFalse();
        assertThat(catalog.contains("STUDENT_NOT_ASSIGNED")).isFalse();
    }

    @Test
    void teacherTextsFollowTheContract() {
        assertThat(catalog.text("CODE_REQUIRED"))
                .isEqualTo(
                        "Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.");
        assertThat(catalog.text("INVALID_CODE"))
                .isEqualTo("Código inválido ou vencido. Procure o administrador.");
        assertThat(catalog.text("TELEGRAM_ALREADY_LINKED"))
                .isEqualTo(
                        "Esta conta do Telegram já está vinculada a outro professor. Procure o administrador.");
        assertThat(catalog.text("TEACHER_ALREADY_LINKED"))
                .isEqualTo(
                        "Seu cadastro já está vinculado a outra conta do Telegram. Procure o administrador.");
        assertThat(catalog.text("NO_STUDENTS"))
                .isEqualTo("Você ainda não tem alunos. Cadastre o primeiro em “Meus alunos”.");
    }

    @Test
    void newTextsOfTheFeatureAreInTheCatalog() {
        assertThat(catalog.text("DUPLICATE_EMAIL", Map.of("name", "Clara")))
                .isEqualTo("Já existe um cadastro com este e-mail: Clara.");
        assertThat(catalog.text("DUPLICATE_CPF", Map.of("name", "Matteo")))
                .isEqualTo("Este CPF já pertence ao aluno Matteo.");
        assertThat(catalog.text("OPERATION_TEMPORARILY_BLOCKED", Map.of("minutes", 3)))
                .isEqualTo(
                        "Esta operação está indisponível no momento. Tente novamente em 3 minuto(s).");
        assertThat(catalog.text("ADMIN_SESSION_EXPIRES_AT", Map.of("time", "10:30")))
                .isEqualTo("Sua sessão expira às 10:30.");
        assertThat(catalog.text("REGISTRY_SAVED", Map.of("name", "Matteo")))
                .isEqualTo("Pronto! Matteo foi cadastrado(a) e está entre os seus alunos.");
        assertThat(catalog.model("REGISTRY_CARD")).contains("\n");
    }

    @Test
    void everyKeyOfTheContractExists() {
        assertThat(FEATURE_KEYS).filteredOn(key -> !catalog.contains(key)).isEmpty();
    }

    @Test
    void noTextInterpolatesSensitiveData() {
        // placeholders permitidos (contracts/messages.md, seção 6): nunca CPF, senha ou código
        Pattern forbidden = Pattern.compile("\\{(cpf|password|code)}", Pattern.CASE_INSENSITIVE);
        assertThat(catalog.keys())
                .filteredOn(key -> forbidden.matcher(catalog.model(key)).find())
                .isEmpty();
    }
}
