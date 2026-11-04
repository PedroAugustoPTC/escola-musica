package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.log.CorrelationMdc;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationTest {

    @Test
    void theAdminFilterAndTheTeacherRoutesShareTheSameAttribute() {
        assertThat(AdminSessionFilter.CORRELATION_ATTRIBUTE)
                .isEqualTo(RequestCorrelation.ATTRIBUTE);
    }

    @Test
    void firstReadCreatesAUuidAndTheSameRequestAlwaysSeesTheSameValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String first = RequestCorrelation.of(request);

        assertThat(UUID.fromString(first)).isNotNull();
        assertThat(RequestCorrelation.of(request)).isEqualTo(first);
        assertThat(request.getAttribute(RequestCorrelation.ATTRIBUTE)).isEqualTo(first);
    }

    @Test
    void identifierAssignedByTheFilterIsTheOneTheControllerReads() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String assigned = RequestCorrelation.assign(request);

        assertThat(RequestCorrelation.of(request)).isEqualTo(assigned);
    }

    @Test
    void differentRequestsGetDifferentIdentifiers() {
        assertThat(RequestCorrelation.of(new MockHttpServletRequest()))
                .isNotEqualTo(RequestCorrelation.of(new MockHttpServletRequest()));
    }

    @Test
    void anAttributeThatIsNotTextIsReplaced() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestCorrelation.ATTRIBUTE, 42);

        assertThat(UUID.fromString(RequestCorrelation.of(request))).isNotNull();
    }

    @Test
    void filterPutsTheRequestIdentifierInTheLogContextAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInChain = new String[2];

        new RequestCorrelationFilter()
                .doFilter(
                        request,
                        response,
                        (req, res) -> {
                            seenInChain[0] = MDC.get(CorrelationMdc.KEY);
                            seenInChain[1] = RequestCorrelation.of((MockHttpServletRequest) req);
                        });

        assertThat(seenInChain[0]).isNotNull().isEqualTo(seenInChain[1]);
        assertThat(UUID.fromString(seenInChain[0])).isNotNull();
        assertThat(MDC.get(CorrelationMdc.KEY)).isNull();
    }

    @Test
    void theAdminFilterKeepsTheIdentifierTheCorrelationFilterAlreadyAssigned() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String assigned = RequestCorrelation.assign(request);

        assertThat(RequestCorrelation.of(request)).isEqualTo(assigned);
    }

    @Test
    void identifierOfTheTelegramUpdateGoesToTheLogContextWhileTheScopeIsOpen() {
        try (CorrelationMdc scope = CorrelationMdc.open("123456")) {
            assertThat(MDC.get(CorrelationMdc.KEY)).isEqualTo("123456");
            assertThat(scope).isNotNull();
        }
        assertThat(MDC.get(CorrelationMdc.KEY)).isNull();
    }
}
