package br.com.felipeblaft.relatorios.entrada.carga;

import br.com.felipeblaft.relatorios.casodeuso.carga.EmitirCodigoAcessoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.CodigoEmitido;
import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import java.io.PrintStream;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Comando {@code emitir-codigo --email <email>}: reemite o código de um professor. */
@Component
@Profile("carga")
public class EmitirCodigoComando {

    public static final String NOME = "emitir-codigo";

    private final EmitirCodigoAcessoCasoDeUso casoDeUso;
    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens formatacao;

    public EmitirCodigoComando(
            EmitirCodigoAcessoCasoDeUso casoDeUso,
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
            CodigoEmitido codigo = casoDeUso.executar(email);
            saida.println(ArgumentosComando.linhaCodigo(codigo, formatacao));
            return 0;
        } catch (ExcecaoDominio erro) {
            saida.println(catalogo.texto(erro.codigo().chaveMensagem()));
            return 1;
        }
    }
}
