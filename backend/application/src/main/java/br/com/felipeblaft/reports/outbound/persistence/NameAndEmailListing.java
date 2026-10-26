package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Lista administrativa de contas (professores e administradores): ordem alfabética, busca por nome
 * ou e-mail sem acento nem caixa e paginação, feitas em memória (R-22: uma escola, poucas contas).
 */
final class NameAndEmailListing {

    private NameAndEmailListing() {}

    static <T> PageResult<T> page(
            List<T> all,
            String search,
            Function<T, String> name,
            Function<T, String> email,
            PageRequest page) {
        String term = StudentSearch.normalize(search);
        List<T> ordered =
                all.stream()
                        .filter(
                                item ->
                                        term.isEmpty()
                                                || StudentSearch.normalize(name.apply(item))
                                                        .contains(term)
                                                || StudentSearch.normalize(email.apply(item))
                                                        .contains(term))
                        .sorted(
                                Comparator.comparing(
                                                (T item) ->
                                                        StudentSearch.normalize(name.apply(item)))
                                        .thenComparing(email))
                        .toList();
        return PageResult.slice(ordered, page);
    }
}
