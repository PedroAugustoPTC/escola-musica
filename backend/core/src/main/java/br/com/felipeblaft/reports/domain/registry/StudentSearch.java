package br.com.felipeblaft.reports.domain.registry;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Busca aproximada de aluno entre os associados ao professor (R-17, FR-013): ignora maiúsculas,
 * minúsculas e acentos; aceita parte do nome (trecho contínuo ou início de cada palavra).
 */
public final class StudentSearch {

    public enum ResultType {
        /** Busca vazia: todos os alunos. */
        TODOS,
        /** Uma ou mais correspondências. */
        ENCONTRADOS,
        /** Nenhuma correspondência: a lista traz todos os alunos do professor. */
        NENHUM
    }

    public record Result(ResultType type, List<Student> students) {

        public Result {
            students = List.copyOf(students);
        }
    }

    private StudentSearch() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents =
                Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    public static Result search(List<Student> students, String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return new Result(ResultType.TODOS, students);
        }
        List<Student> found = students.stream().filter(a -> matches(a.name(), query)).toList();
        if (found.isEmpty()) {
            return new Result(ResultType.NENHUM, students);
        }
        return new Result(ResultType.ENCONTRADOS, found);
    }

    static boolean matches(String name, String normalizedQuery) {
        String target = normalize(name);
        if (target.contains(normalizedQuery)) {
            return true;
        }
        List<String> words = Arrays.asList(target.split(" "));
        return Arrays.stream(normalizedQuery.split(" "))
                .allMatch(term -> words.stream().anyMatch(word -> word.startsWith(term)));
    }

    /**
     * Correspondência exata (caso de borda do nome enviado durante a conferência): o texto é igual
     * ao nome completo ou ao primeiro nome de um único aluno.
     */
    public static Optional<Student> exactMatch(List<Student> students, String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return Optional.empty();
        }
        List<Student> exact =
                students.stream()
                        .filter(
                                a -> {
                                    String name = normalize(a.name());
                                    return name.equals(query) || name.split(" ")[0].equals(query);
                                })
                        .toList();
        return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
    }
}
