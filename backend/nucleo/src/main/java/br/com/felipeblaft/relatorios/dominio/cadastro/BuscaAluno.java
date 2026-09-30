package br.com.felipeblaft.relatorios.dominio.cadastro;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Busca aproximada de aluno entre os associados ao professor (R-17, FR-013): ignora maiúsculas,
 * minúsculas e acentos; aceita parte do nome (trecho contínuo ou início de cada palavra).
 */
public final class BuscaAluno {

    public enum TipoResultado {
        /** Busca vazia: todos os alunos. */
        TODOS,
        /** Uma ou mais correspondências. */
        ENCONTRADOS,
        /** Nenhuma correspondência: a lista traz todos os alunos do professor. */
        NENHUM
    }

    public record Resultado(TipoResultado tipo, List<Aluno> alunos) {

        public Resultado {
            alunos = List.copyOf(alunos);
        }
    }

    private BuscaAluno() {}

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcentos =
                Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcentos.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    public static Resultado buscar(List<Aluno> alunos, String texto) {
        String consulta = normalizar(texto);
        if (consulta.isEmpty()) {
            return new Resultado(TipoResultado.TODOS, alunos);
        }
        List<Aluno> encontrados = alunos.stream().filter(a -> corresponde(a.nome(), consulta)).toList();
        if (encontrados.isEmpty()) {
            return new Resultado(TipoResultado.NENHUM, alunos);
        }
        return new Resultado(TipoResultado.ENCONTRADOS, encontrados);
    }

    static boolean corresponde(String nome, String consultaNormalizada) {
        String alvo = normalizar(nome);
        if (alvo.contains(consultaNormalizada)) {
            return true;
        }
        List<String> palavras = Arrays.asList(alvo.split(" "));
        return Arrays.stream(consultaNormalizada.split(" "))
                .allMatch(termo -> palavras.stream().anyMatch(palavra -> palavra.startsWith(termo)));
    }

    /**
     * Correspondência exata (caso de borda do nome enviado durante a conferência): o texto é igual ao
     * nome completo ou ao primeiro nome de um único aluno.
     */
    public static Optional<Aluno> correspondenciaExata(List<Aluno> alunos, String texto) {
        String consulta = normalizar(texto);
        if (consulta.isEmpty()) {
            return Optional.empty();
        }
        List<Aluno> exatos =
                alunos.stream()
                        .filter(
                                a -> {
                                    String nome = normalizar(a.nome());
                                    return nome.equals(consulta)
                                            || nome.split(" ")[0].equals(consulta);
                                })
                        .toList();
        return exatos.size() == 1 ? Optional.of(exatos.getFirst()) : Optional.empty();
    }
}
