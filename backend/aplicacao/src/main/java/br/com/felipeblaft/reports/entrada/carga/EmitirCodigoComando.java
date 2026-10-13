package br.com.felipeblaft.reports.entrada.carga;

import br.com.felipeblaft.reports.configuracao.CatalogoMensagens;
import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.usecase.seed.EmitAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import java.io.PrintStream;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Comando {@code emitir-codigo --email <email>}: reemite o código de um professor. */
@Component
@Profile("carga")
public class EmitirCodigoComando {

    public static final String NOME = "emitir-codigo";

    private final EmitAccessCodeUseCase casoDeUso;
    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens formatacao;

    public EmitirCodigoComando(
            EmitAccessCodeUseCase casoDeUso,
            CatalogoMensagens catalogo,
            FormatacaoMensagens formatacao) {
        this.casoDeUso = casoDeUso;
        this.catalogo = catalogo;
        this.formatacao = formatacao;
    }

    public int executar(List<String> argumentos, PrintStream saida) {
        String email = ArgumentosComando.valor(argumentos, "--email");
        if (email == null) {
            saida.println("Uso: emitir-codigo --email <email-do-professor>");
            return 2;
        }
        try {
            IssuedCode codigo = casoDeUso.run(email);
            saida.println(ArgumentosComando.linhaCodigo(codigo, formatacao));
            return 0;
        } catch (DomainException erro) {
            saida.println(catalogo.texto(erro.code().messageKey()));
            return 1;
        }
    }
}
