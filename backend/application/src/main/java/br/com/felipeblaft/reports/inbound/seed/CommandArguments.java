package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import java.util.List;

/** Leitura simples de argumentos no formato {@code --nome valor} ou {@code --nome=valor}. */
final class CommandArguments {

    private CommandArguments() {}

    static String value(List<String> arguments, String name) {
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (argument.equals(name) && i + 1 < arguments.size()) {
                return arguments.get(i + 1);
            }
            if (argument.startsWith(name + "=")) {
                return argument.substring(name.length() + 1);
            }
        }
        return null;
    }

    static String codeLine(IssuedCode code, MessageFormatting formatting) {
        return code.email()
                + " → "
                + code.code()
                + " (válido até "
                + formatting.data(code.validUntil())
                + " "
                + formatting.time(code.validUntil())
                + ")";
    }
}
