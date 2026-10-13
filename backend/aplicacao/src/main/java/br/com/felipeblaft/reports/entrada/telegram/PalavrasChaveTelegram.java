package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import java.util.Map;
import java.util.Optional;

/**
 * Comandos e palavras-chave do bot (contracts/telegram-conversa.md §2, FR-037). A comparação ignora
 * maiúsculas, acentos e a barra inicial ({@code /inicio} = {@code início} = {@code menu}).
 */
public final class PalavrasChaveTelegram {

    public enum Comando {
        INICIO,
        REGISTRAR_AULA,
        SEMESTRAL,
        HISTORICO,
        APROVAR,
        CANCELAR,
        CONTINUAR,
        DESCARTAR,
        TENTAR,
        SAIR,
        AJUDA,
        SIM
    }

    private static final Map<String, Comando> PALAVRAS =
            Map.ofEntries(
                    Map.entry("start", Comando.INICIO),
                    Map.entry("inicio", Comando.INICIO),
                    Map.entry("menu", Comando.INICIO),
                    Map.entry("registrar aula", Comando.REGISTRAR_AULA),
                    Map.entry("registrar", Comando.REGISTRAR_AULA),
                    Map.entry("relatorio semestral", Comando.SEMESTRAL),
                    Map.entry("semestral", Comando.SEMESTRAL),
                    Map.entry("historico", Comando.HISTORICO),
                    Map.entry("aprovar", Comando.APROVAR),
                    Map.entry("cancelar", Comando.CANCELAR),
                    Map.entry("continuar", Comando.CONTINUAR),
                    Map.entry("descartar", Comando.DESCARTAR),
                    Map.entry("tentar", Comando.TENTAR),
                    Map.entry("tentar novamente", Comando.TENTAR),
                    Map.entry("sair", Comando.SAIR),
                    Map.entry("ajuda", Comando.AJUDA),
                    Map.entry("help", Comando.AJUDA),
                    Map.entry("sim", Comando.SIM));

    private PalavrasChaveTelegram() {}

    public static String normalizar(String texto) {
        String semBarra = texto == null ? "" : texto.strip();
        if (semBarra.startsWith("/")) {
            semBarra = semBarra.substring(1);
            int arroba = semBarra.indexOf('@');
            if (arroba > 0) {
                semBarra = semBarra.substring(0, arroba);
            }
        }
        return StudentSearch.normalize(semBarra).replaceAll("[.!?]+$", "").strip();
    }

    public static Optional<Comando> identificar(String texto) {
        return Optional.ofNullable(PALAVRAS.get(normalizar(texto)));
    }
}
