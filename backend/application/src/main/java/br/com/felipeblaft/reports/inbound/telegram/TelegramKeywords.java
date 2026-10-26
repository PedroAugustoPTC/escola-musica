package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import java.util.Map;
import java.util.Optional;

/**
 * Comandos e palavras-chave do bot (contracts/telegram-conversa.md §2, FR-037). A comparação ignora
 * maiúsculas, acentos e a barra inicial ({@code /inicio} = {@code início} = {@code menu}).
 */
public final class TelegramKeywords {

    public enum Command {
        HOME,
        REGISTER_LESSON,
        SEMESTER,
        HISTORY,
        APPROVE,
        CANCEL,
        CONTINUE,
        DISCARD,
        RETRY,
        SIGN_OUT,
        HELP,
        YES,
        /** "meus alunos" / "alunos": abre a lista (feature 005). */
        MY_STUDENTS,
        /** "cadastrar aluno" / "novo aluno": inicia o cadastro guiado. */
        REGISTER_STUDENT,
        /** "pular": só nas etapas opcionais (instrumento). */
        SKIP,
        /** "voltar": só na lista e no cartão do aluno. */
        BACK
    }

    private static final Map<String, Command> WORDS =
            Map.ofEntries(
                    Map.entry("start", Command.HOME),
                    Map.entry("inicio", Command.HOME),
                    Map.entry("menu", Command.HOME),
                    Map.entry("registrar aula", Command.REGISTER_LESSON),
                    Map.entry("registrar", Command.REGISTER_LESSON),
                    Map.entry("relatorio semestral", Command.SEMESTER),
                    Map.entry("semestral", Command.SEMESTER),
                    Map.entry("historico", Command.HISTORY),
                    Map.entry("aprovar", Command.APPROVE),
                    Map.entry("cancelar", Command.CANCEL),
                    Map.entry("continuar", Command.CONTINUE),
                    Map.entry("descartar", Command.DISCARD),
                    Map.entry("tentar", Command.RETRY),
                    Map.entry("tentar novamente", Command.RETRY),
                    Map.entry("sair", Command.SIGN_OUT),
                    Map.entry("ajuda", Command.HELP),
                    Map.entry("help", Command.HELP),
                    Map.entry("sim", Command.YES),
                    Map.entry("meus alunos", Command.MY_STUDENTS),
                    Map.entry("alunos", Command.MY_STUDENTS),
                    Map.entry("cadastrar aluno", Command.REGISTER_STUDENT),
                    Map.entry("novo aluno", Command.REGISTER_STUDENT),
                    Map.entry("pular", Command.SKIP),
                    Map.entry("voltar", Command.BACK));

    private TelegramKeywords() {}

    public static String normalize(String text) {
        String withoutSlash = text == null ? "" : text.strip();
        if (withoutSlash.startsWith("/")) {
            withoutSlash = withoutSlash.substring(1);
            int at = withoutSlash.indexOf('@');
            if (at > 0) {
                withoutSlash = withoutSlash.substring(0, at);
            }
        }
        return StudentSearch.normalize(withoutSlash).replaceAll("[.!?]+$", "").strip();
    }

    public static Optional<Command> identify(String text) {
        return Optional.ofNullable(WORDS.get(normalize(text)));
    }
}
