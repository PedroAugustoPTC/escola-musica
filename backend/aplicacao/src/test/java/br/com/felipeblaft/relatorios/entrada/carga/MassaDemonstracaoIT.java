package br.com.felipeblaft.relatorios.entrada.carga;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.carga.CarregarCadastroInicialCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Massa de demonstração do ambiente local (local.sh subir --massa=true). */
class MassaDemonstracaoIT extends IntegracaoBase {

    private static final List<String> ARGUMENTOS =
            List.of("massa", "--arquivo", "../../infra/carga/demonstracao.json");

    @Autowired private CarregarCadastroInicialCasoDeUso carga;
    @Autowired private ProfessorRepositorio professores;
    @Autowired private AlunoRepositorio alunos;
    @Autowired private RelatorioAulaRepositorio relatoriosAula;
    @Autowired private VersaoRelatorioRepositorio versoes;
    @Autowired private RelatorioSemestralRepositorio semestrais;
    @Autowired private GeracaoDocumentoServico documentos;
    @Autowired private TransacaoPorta transacao;
    @Autowired private FormatacaoMensagens formatacao;

    private MassaDemonstracaoComando comando;

    @BeforeEach
    void criarComando() {
        comando =
                new MassaDemonstracaoComando(
                        carga,
                        professores,
                        alunos,
                        relatoriosAula,
                        versoes,
                        semestrais,
                        documentos,
                        transacao,
                        relogio,
                        formatacao,
                        jdbc);
    }

    private String executar() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int codigo =
                comando.executar(ARGUMENTOS, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return codigo + "\n" + bytes.toString(StandardCharsets.UTF_8);
    }

    private Map<String, Integer> statusDe(String tabela) {
        Map<String, Integer> contagem = new java.util.TreeMap<>();
        jdbc.queryForList("select status, count(*) as n from " + tabela + " group by status")
                .forEach(
                        l ->
                                contagem.put(
                                        (String) l.get("status"),
                                        ((Number) l.get("n")).intValue()));
        return contagem;
    }

    @Test
    void criaCadastrosRelatoriosEPdfsEmBancoVazio() {
        String saida = executar();

        assertThat(saida).startsWith("0\n").contains("Massa de demonstração criada");
        assertThat(saida).contains("clara@exemplo.test → ").contains("bruno@exemplo.test → ");
        assertThat(jdbc.queryForObject("select count(*) from professor", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from aluno", Integer.class)).isEqualTo(8);

        Map<String, Integer> aulas = statusDe("relatorio_aula");
        assertThat(aulas.get("APROVADO")).isGreaterThan(30);
        assertThat(aulas)
                .containsEntry("AGUARDANDO_CONFERENCIA", 1)
                .containsEntry("AGUARDANDO_RESPOSTAS", 1);
        assertThat(statusDe("relatorio_semestral"))
                .containsEntry("APROVADO", 1)
                .containsEntry("AGUARDANDO_CONFERENCIA", 1);

        // cada aula aprovada tem PDF de conferência e oficial; o semestral aprovado, os dois também
        Integer oficiais =
                jdbc.queryForObject(
                        "select count(*) from documento_pdf where tipo = 'OFICIAL'", Integer.class);
        assertThat(oficiais).isEqualTo(aulas.get("APROVADO") + 1);
        assertThat(saida)
                .contains(
                        "PDFs gerados: "
                                + jdbc.queryForObject(
                                        "select count(*) from documento_pdf", Integer.class));

        // alunos sem histórico, para testar as telas vazias
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from relatorio_aula r join aluno a on a.id = r.aluno_id"
                                        + " where a.nome in ('Lucas Pimentel', 'Sofia Rangel')",
                                Integer.class))
                .isZero();
    }

    @Test
    void recusaBancoComDados() {
        executar();
        String segunda = executar();

        assertThat(segunda).startsWith("1\n").contains("só é criada em banco vazio");
        assertThat(jdbc.queryForObject("select count(*) from professor", Integer.class))
                .isEqualTo(2);
    }
}
