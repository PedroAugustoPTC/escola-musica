package br.com.felipeblaft.reports.entrada.carga;

import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import java.util.List;

/** Leitura simples de argumentos no formato {@code --nome valor} ou {@code --nome=valor}. */
final class ArgumentosComando {

    private ArgumentosComando() {}

    static String valor(List<String> argumentos, String nome) {
        for (int i = 0; i < argumentos.size(); i++) {
            String argumento = argumentos.get(i);
            if (argumento.equals(nome) && i + 1 < argumentos.size()) {
                return argumentos.get(i + 1);
            }
            if (argumento.startsWith(nome + "=")) {
                return argumento.substring(nome.length() + 1);
            }
        }
        return null;
    }

    static String linhaCodigo(IssuedCode codigo, FormatacaoMensagens formatacao) {
        return codigo.email()
                + " → "
                + codigo.code()
                + " (válido até "
                + formatacao.data(codigo.validUntil())
                + " "
                + formatacao.hora(codigo.validUntil())
                + ")";
    }
}
