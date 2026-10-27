package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
}
