package br.com.felipeblaft.relatorios.entrada.carga;

import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.CarregarCadastroInicialCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga;
import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comando {@code carga --arquivo <arquivo.json> [--simular]} (contracts/carga-inicial.md). Mostra o
 * resumo sem CPF e, para cada professor novo, a linha {@code email → CÓDIGO (válido até …)}.
 */
@Component
@Profile("carga")
public class CargaComando {

    public static final String NOME = "carga";

    private final CarregarCadastroInicialCasoDeUso casoDeUso;
    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens formatacao;
    private final JsonMapper json =
            JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

    public CargaComando(
            CarregarCadastroInicialCasoDeUso casoDeUso,
            CatalogoMensagens catalogo,
            FormatacaoMensagens formatacao) {
        this.casoDeUso = casoDeUso;
        this.catalogo = catalogo;
        this.formatacao = formatacao;
    }

    /** Devolve o código de saída do processo (0 = sucesso). */
    public int executar(List<String> argumentos, PrintStream saida) {
        String arquivo = ArgumentosComando.valor(argumentos, "--arquivo");
        boolean simular = argumentos.contains("--simular");
        if (arquivo == null) {
            saida.println("Uso: carga --arquivo <arquivo.json> [--simular]");
            return 2;
        }
        ArquivoCarga conteudo;
        try {
            conteudo = json.readValue(Files.readString(Path.of(arquivo)), ArquivoCarga.class);
        } catch (Exception erro) {
            saida.println("Não foi possível ler o arquivo de carga (JSON inválido ou arquivo ausente).");
            return 1;
        }
        ResultadoCarga resultado = casoDeUso.executar(conteudo, simular);
        imprimir(resultado, saida);
        return resultado.sucesso() ? 0 : 1;
    }

    private void imprimir(ResultadoCarga resultado, PrintStream saida) {
        if (!resultado.sucesso()) {
            saida.println("Carga recusada: nada foi gravado.");
            for (ResultadoCarga.ErroCarga erro : resultado.erros()) {
                saida.printf(
                        "%s[%d].%s: %s — %s%n",
                        erro.secao(),
                        erro.indice(),
                        erro.campo(),
                        erro.codigo().name(),
                        catalogo.texto(erro.codigo().chaveMensagem()));
            }
            return;
        }
        saida.println(resultado.simulacao() ? "Simulação concluída: nada foi gravado." : "Carga concluída.");
        saida.printf(
                "Professores: %d criados, %d atualizados, %d inalterados%n",
                resultado.professores().criados(),
                resultado.professores().atualizados(),
                resultado.professores().inalterados());
        saida.printf(
                "Alunos: %d criados, %d atualizados, %d inalterados%n",
                resultado.alunos().criados(),
                resultado.alunos().atualizados(),
                resultado.alunos().inalterados());
        saida.printf("Associações criadas: %d%n", resultado.associacoesCriadas());
        if (!resultado.codigosEmitidos().isEmpty()) {
            saida.println("Códigos emitidos (repasse cada código ao professor fora do sistema):");
            for (ResultadoCarga.CodigoEmitido codigo : resultado.codigosEmitidos()) {
                saida.println(ArgumentosComando.linhaCodigo(codigo, formatacao));
            }
        }
    }
}
