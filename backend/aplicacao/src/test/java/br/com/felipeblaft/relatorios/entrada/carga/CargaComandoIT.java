package br.com.felipeblaft.relatorios.entrada.carga;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("carga")
class CargaComandoIT extends IntegracaoBase {

    private static final String EXEMPLO = Path.of("../../infra/carga/exemplo.json").toString();

    @Autowired CargaComando carga;
    @Autowired EmitirCodigoComando emitirCodigo;

    @TempDir Path temporario;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream saida = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String texto() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void cargaDoExemploCriaCadastrosEEmiteCodigos() {
        int codigo = carga.executar(List.of("carga", "--arquivo", EXEMPLO), saida);

        assertThat(codigo).isZero();
        assertThat(texto())
                .contains("Carga concluída.")
                .contains("Professores: 2 criados, 0 atualizados, 0 inalterados")
                .contains("Alunos: 4 criados, 0 atualizados, 0 inalterados")
                .contains("Associações criadas: 4")
                .containsPattern("clara@exemplo\\.test → [A-HJ-NP-Z2-9]{8} \\(válido até 28/09/2026 10:00\\)")
                .containsPattern("bruno@exemplo\\.test → [A-HJ-NP-Z2-9]{8}")
                .doesNotContain("529.982.247-25", "52998224725");
        assertThat(contar("professor")).isEqualTo(2);
        assertThat(contar("aluno")).isEqualTo(4);
        assertThat(contar("professor_aluno")).isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from professor where codigo_acesso_hash is not null",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void segundaExecucaoEhIdempotente() {
        carga.executar(List.of("carga", "--arquivo", EXEMPLO), saida);
        bytes.reset();

        int codigo = carga.executar(List.of("carga", "--arquivo=" + EXEMPLO), saida);

        assertThat(codigo).isZero();
        assertThat(texto())
                .contains("Professores: 0 criados, 0 atualizados, 2 inalterados")
                .contains("Alunos: 0 criados, 0 atualizados, 4 inalterados")
                .contains("Associações criadas: 0")
                .doesNotContain("→");
        assertThat(contar("aluno")).isEqualTo(4);
    }

    @Test
    void arquivoComErroNaoGravaNada() throws Exception {
        Path arquivo = temporario.resolve("erro.json");
        Files.writeString(
                arquivo,
                """
                {"professores":[{"email":"clara@exemplo.test","nome":"Clara"}],
                 "alunos":[{"cpf":"529.982.247-25","nome":"Matteo","dataNascimento":"2014-05-10",
                            "nomeResponsavel":"R","professores":["clara@exemplo.test"]},
                           {"cpf":"111.111.111-11","nome":"X","dataNascimento":"2000-01-01",
                            "professores":[]}]}
                """);

        int codigo = carga.executar(List.of("carga", "--arquivo", arquivo.toString()), saida);

        assertThat(codigo).isEqualTo(1);
        assertThat(texto())
                .contains("Carga recusada: nada foi gravado.")
                .contains("alunos[1].cpf: CPF_INVALIDO — CPF inválido.")
                .doesNotContain("111.111");
        assertThat(contar("professor")).isZero();
        assertThat(contar("aluno")).isZero();
    }

    @Test
    void simularNaoGrava() {
        int codigo = carga.executar(List.of("carga", "--arquivo", EXEMPLO, "--simular"), saida);

        assertThat(codigo).isZero();
        assertThat(texto())
                .contains("Simulação concluída: nada foi gravado.")
                .contains("Alunos: 4 criados");
        assertThat(contar("professor")).isZero();
        assertThat(contar("aluno")).isZero();
    }

    @Test
    void argumentosEArquivoInvalidos() throws Exception {
        assertThat(carga.executar(List.of("carga"), saida)).isEqualTo(2);
        assertThat(carga.executar(List.of("carga", "--arquivo", "/nao/existe.json"), saida))
                .isEqualTo(1);
        Path invalido = temporario.resolve("invalido.json");
        Files.writeString(invalido, "{ nao é json");
        assertThat(carga.executar(List.of("carga", "--arquivo", invalido.toString()), saida))
                .isEqualTo(1);
        assertThat(texto()).contains("Uso: carga").contains("Não foi possível ler o arquivo");
    }

    @Test
    void emitirCodigoSubstituiOHash() {
        carga.executar(List.of("carga", "--arquivo", EXEMPLO), saida);
        String hashAntes =
                jdbc.queryForObject(
                        "select codigo_acesso_hash from professor where email = 'clara@exemplo.test'",
                        String.class);
        bytes.reset();

        int codigo = emitirCodigo.executar(List.of("emitir-codigo", "--email", "clara@exemplo.test"), saida);

        assertThat(codigo).isZero();
        assertThat(texto()).containsPattern("clara@exemplo\\.test → [A-HJ-NP-Z2-9]{8} \\(válido até");
        String hashDepois =
                jdbc.queryForObject(
                        "select codigo_acesso_hash from professor where email = 'clara@exemplo.test'",
                        String.class);
        assertThat(hashDepois).isNotEqualTo(hashAntes).hasSize(64);
    }

    @Test
    void emitirCodigoParaProfessorInexistente() {
        assertThat(emitirCodigo.executar(List.of("emitir-codigo"), saida)).isEqualTo(2);
        assertThat(emitirCodigo.executar(List.of("emitir-codigo", "--email", "x@y.z"), saida))
                .isEqualTo(1);
        assertThat(texto()).contains("Professor não encontrado");
    }
}
