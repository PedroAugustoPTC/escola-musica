package br.com.felipeblaft.reports.entrada.carga;

import br.com.felipeblaft.reports.configuracao.CatalogoMensagens;
import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile;
import br.com.felipeblaft.reports.usecase.seed.SeedResult;
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

    private final LoadInitialRegistryUseCase casoDeUso;
    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens formatacao;
    private final JsonMapper json =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public CargaComando(
            LoadInitialRegistryUseCase casoDeUso,
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
        SeedFile conteudo;
        try {
            conteudo = json.readValue(Files.readString(Path.of(arquivo)), SeedFile.class);
        } catch (Exception erro) {
            saida.println(
                    "Não foi possível ler o arquivo de carga (JSON inválido ou arquivo ausente).");
            return 1;
        }
        SeedResult resultado = casoDeUso.run(conteudo, simular);
        imprimir(resultado, saida);
        return resultado.success() ? 0 : 1;
    }

    private void imprimir(SeedResult resultado, PrintStream saida) {
        if (!resultado.success()) {
            saida.println("Carga recusada: nada foi gravado.");
            for (SeedResult.SeedError erro : resultado.errors()) {
                saida.printf(
                        "%s[%d].%s: %s — %s%n",
                        erro.section(),
                        erro.index(),
                        erro.field(),
                        erro.code().name(),
                        catalogo.texto(erro.code().messageKey()));
            }
            return;
        }
        saida.println(
                resultado.simulation()
                        ? "Simulação concluída: nada foi gravado."
                        : "Carga concluída.");
        saida.printf(
                "Professores: %d criados, %d atualizados, %d inalterados%n",
                resultado.teachers().created(),
                resultado.teachers().updated(),
                resultado.teachers().unchanged());
        saida.printf(
                "Alunos: %d criados, %d atualizados, %d inalterados%n",
                resultado.students().created(),
                resultado.students().updated(),
                resultado.students().unchanged());
        saida.printf("Associações criadas: %d%n", resultado.createdAssignments());
        if (!resultado.issuedCodes().isEmpty()) {
            saida.println("Códigos emitidos (repasse cada código ao professor fora do sistema):");
            for (SeedResult.IssuedCode codigo : resultado.issuedCodes()) {
                saida.println(ArgumentosComando.linhaCodigo(codigo, formatacao));
            }
        }
    }
}
