package br.com.felipeblaft.reports.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageAndSecretTest {

    @Test
    void secretNeverShowsItsValue() {
        Secret secret = new Secret("very-secret-password-123");
        assertThat(secret.toString()).doesNotContain("very-secret-password-123");
        assertThat(secret.value()).isEqualTo("very-secret-password-123");
        assertThatThrownBy(() -> new Secret(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void pageRequestAppliesDefaultsAndComputesOffset() {
        assertThat(PageRequest.of(null, null)).isEqualTo(new PageRequest(0, 20));
        assertThat(PageRequest.first()).isEqualTo(new PageRequest(0, 20));
        assertThat(PageRequest.of(2, 50).offset()).isEqualTo(100);
        assertThat(new PageRequest(0, 100).size()).isEqualTo(100);
    }

    @Test
    void pageRequestRejectsOutOfRangeValuesWithTheFieldName() {
        assertThatThrownBy(() -> new PageRequest(-1, 20))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> {
                            assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                            assertThat(e.details()).containsEntry("field", "page");
                        });
        assertThatThrownBy(() -> new PageRequest(0, 0))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.details()).containsEntry("field", "size"));
        assertThatThrownBy(() -> new PageRequest(0, 101)).isInstanceOf(DomainException.class);
    }

    @Test
    void sliceReturnsTheRequestedPageAndTheTotal() {
        List<Integer> all = List.of(1, 2, 3, 4, 5);
        PageResult<Integer> first = PageResult.slice(all, new PageRequest(0, 2));
        assertThat(first.items()).containsExactly(1, 2);
        assertThat(first.total()).isEqualTo(5);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(PageResult.slice(all, new PageRequest(2, 2)).items()).containsExactly(5);
        assertThat(PageResult.slice(all, new PageRequest(9, 2)).items()).isEmpty();
        assertThat(PageResult.slice(List.<Integer>of(), PageRequest.first()).total()).isZero();
    }

    @Test
    void mapKeepsThePositionAndTotal() {
        PageResult<String> mapped =
                PageResult.slice(List.of(1, 2, 3), new PageRequest(0, 2)).map(n -> "n" + n);
        assertThat(mapped.items()).containsExactly("n1", "n2");
        assertThat(mapped.total()).isEqualTo(3);
        assertThat(mapped.size()).isEqualTo(2);
    }
}
