package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.audit.QueryAuditTrailUseCase;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * A trilha de auditoria é só Web (feature 007, FR-080): no Telegram, os pedidos de auditoria
 * recebem a negativa neutra da 005 (FR-037) seguida do menu do professor, sem citar Web nem
 * administrador, e nenhum caso de uso da feature é chamado.
 */
class AdminRequestsNeutralDenialIT extends RegistryConversationBase {

    private static final List<String> ATTEMPTS = List.of(
                    "audit",
                    "audit trail",
                    "/audit",
                    "apagar aluno",
                    "eliminar",
                    "excluir dados");

    private static final List<String> FORBIDDEN =
            List.of("web", "administra" + "dor", "administrativa", "portal", "secre" + "taria");

    @MockitoSpyBean QueryAuditTrailUseCase queryAuditTrail;
    @MockitoSpyBean VerifyAuditChainUseCase verifyAuditChain;

    @MockitoSpyBean RegisterErasureRequestUseCase registerErasure;
    @MockitoSpyBean ConfirmErasureUseCase confirmErasure;

    @BeforeEach
    void students() {
        scenario.student(clara, "Matteo Mafra", "Piano");
    }

    @Test
    @ConversationState({10})
    void linkedTeacherGetsTheNeutralDenialFollowedByTheTeacherMenu() {
        for (String attempt : ATTEMPTS) {
            send(attempt);

            String reply = lastText();
            assertThat(reply.replace(attempt, "").toLowerCase(Locale.ROOT))
                    .doesNotContain(FORBIDDEN.toArray(String[]::new));
            assertThat(buttonData(lastMessage())).contains("v1:home:");
        }
        verifyNoInteractions(queryAuditTrail, verifyAuditChain, registerErasure, confirmErasure);
    }
}
