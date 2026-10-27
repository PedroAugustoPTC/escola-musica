package br.com.felipeblaft.reports.inbound.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * A lista de rotas liberadas com a senha provisória tem uma fonte só: o núcleo. O filtro só diz
 * onde cada operação mora na Web; operação nova no núcleo sem rota aqui reprova este teste.
 */
class AdminSessionFilterRestrictedRoutesTest {

    @Test
    void theFilterMapsExactlyTheOperationsTheCoreAllowsWhileRestricted() {
        assertThat(AdminSessionFilter.RESTRICTED_ROUTES.keySet())
                .containsExactlyInAnyOrderElementsOf(
                        ActorAuthorizationService.ALLOWED_WHILE_RESTRICTED);
    }

    @Test
    void everyMappedRouteMatchesItsRequestAndNoOther() {
        for (Operation operation : Operation.values()) {
            var route = AdminSessionFilter.RESTRICTED_ROUTES.get(operation);
            if (route == null) {
                continue;
            }
            var request = new MockHttpServletRequest(route.method().name(), route.path());
            request.setRequestURI(route.path());
            assertThat(route.matches(request)).as(operation.name()).isTrue();
            var other = new MockHttpServletRequest("PATCH", route.path());
            other.setRequestURI(route.path());
            assertThat(route.matches(other)).as(operation.name() + " outro método").isFalse();
            var elsewhere = new MockHttpServletRequest(route.method().name(), "/api/v1/admin/x");
            elsewhere.setRequestURI("/api/v1/admin/x");
            assertThat(route.matches(elsewhere)).as(operation.name() + " outro caminho").isFalse();
        }
    }
}
