package br.com.felipeblaft.relatorios.casodeuso.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga.AlunoCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga.ProfessorCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.Contagem;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.ErroCarga;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.dubles.AlunoRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.AssociacaoRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.GeradorCodigoAcessoFixo;
import br.com.felipeblaft.relatorios.dubles.ProfessorRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.ProtecaoDadosFalsa;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import br.com.felipeblaft.relatorios.servico.EmissaoCodigoAcessoServico;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CarregarCadastroInicialCasoDeUsoTeste {

    private static final String CPF_MATTEO = "529.982.247-25";
    private static final String CPF_OLAVO = "123.456.789-09";

    private final RelogioFixo relogio = new RelogioFixo(Instant.parse("2026-09-21T13:00:00Z"));
    private final ProfessorRepositorioEmMemoria professores = new ProfessorRepositorioEmMemoria();
    private final AssociacaoRepositorioEmMemoria associacoes = new AssociacaoRepositorioEmMemoria();
    private final AlunoRepositorioEmMemoria alunos = new AlunoRepositorioEmMemoria(associacoes);
    private final ProtecaoDadosFalsa protecao = new ProtecaoDadosFalsa();
    private final GeradorCodigoAcessoFixo gerador = new GeradorCodigoAcessoFixo("K7QM2XPA", "BRUN2XPA");
    private final CarregarCadastroInicialCasoDeUso casoDeUso =
            new CarregarCadastroInicialCasoDeUso(
                    professores,
                    alunos,
                    associacoes,
                    protecao,
                    new EmissaoCodigoAcessoServico(
                            gerador, protecao, professores, ParametrosNegocio.padrao()),
                    relogio);

    private static ArquivoCarga exemplo() {
        return new ArquivoCarga(
                List.of(
                        new ProfessorCarga("Clara@Exemplo.test", "Clara Souza", true),
                        new ProfessorCarga("bruno@exemplo.test", "Bruno Lima", null)),
                List.of(
                        new AlunoCarga(
                                CPF_MATTEO,
                                "Matteo Mafra",
                                "2014-05-10",
                                "Responsável Fictício",
                                "Piano",
                                List.of("clara@exemplo.test")),
                        new AlunoCarga(
                                CPF_OLAVO,
                                "Olavo Torrente",
                                "1990-02-01",
                                null,
                                " ",
                                List.of("clara@exemplo.test", "CLARA@exemplo.test", "bruno@exemplo.test"))));
    }

    @Test
    void cargaCriaProfessoresAlunosAssociacoesEEmiteCodigos() {
        ResultadoCarga resultado = casoDeUso.executar(exemplo(), false);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.simulacao()).isFalse();
        assertThat(resultado.professores()).isEqualTo(new Contagem(2, 0, 0));
        assertThat(resultado.alunos()).isEqualTo(new Contagem(2, 0, 0));
        assertThat(resultado.associacoesCriadas()).isEqualTo(3);
        assertThat(resultado.codigosEmitidos())
                .extracting(c -> c.email(), c -> c.codigo(), c -> c.validoAte())
                .containsExactly(
                        tuple("clara@exemplo.test", "K7QM2XPA", relogio.agora().plus(Duration.ofDays(7))),
                        tuple("bruno@exemplo.test", "BRUN2XPA", relogio.agora().plus(Duration.ofDays(7))));
        assertThat(resultado.codigosEmitidos().getFirst().toString()).doesNotContain("K7QM2XPA");

        Professor clara = professores.buscarPorEmail("clara@exemplo.test").orElseThrow();
        assertThat(clara.codigoAcessoHash()).isEqualTo("codigo:K7QM2XPA");
        assertThat(professores.buscarPorEmail("bruno@exemplo.test").orElseThrow().ativo()).isTrue();

        Aluno matteo = alunos.buscarPorCpfHash("cpf:52998224725").orElseThrow();
        assertThat(new String(matteo.cpfCifrado())).isEqualTo("cifrado:52742289925");
        assertThat(matteo.nomeResponsavel()).isEqualTo("Responsável Fictício");
        Aluno olavo = alunos.buscarPorCpfHash("cpf:12345678909").orElseThrow();
        assertThat(olavo.instrumento()).isNull();
        assertThat(alunos.listarDoProfessor(clara.id())).extracting(Aluno::nome)
                .containsExactly("Matteo Mafra", "Olavo Torrente");
    }

    @Test
    void segundaExecucaoEhIdempotente() {
        casoDeUso.executar(exemplo(), false);
        int gravacoes = professores.gravacoes();

        ResultadoCarga resultado = casoDeUso.executar(exemplo(), false);

        assertThat(resultado.professores()).isEqualTo(new Contagem(0, 0, 2));
        assertThat(resultado.alunos()).isEqualTo(new Contagem(0, 0, 2));
        assertThat(resultado.associacoesCriadas()).isZero();
        assertThat(resultado.codigosEmitidos()).isEmpty();
        assertThat(professores.gravacoes()).isEqualTo(gravacoes);
        assertThat(associacoes.todas()).hasSize(3);
    }

    @Test
    void atualizaCadastrosExistentesEAssociaProfessorDoBanco() {
        casoDeUso.executar(exemplo(), false);
        ArquivoCarga alteracao =
                new ArquivoCarga(
                        List.of(new ProfessorCarga("clara@exemplo.test", "Clara S. Souza", false)),
                        List.of(
                                new AlunoCarga(
                                        CPF_MATTEO,
                                        "Matteo Mafra",
                                        "2014-05-10",
                                        "Responsável Fictício",
                                        "Violão",
                                        List.of("bruno@exemplo.test"))));

        ResultadoCarga resultado = casoDeUso.executar(alteracao, false);

        assertThat(resultado.professores()).isEqualTo(new Contagem(0, 1, 0));
        assertThat(resultado.alunos()).isEqualTo(new Contagem(0, 1, 0));
        assertThat(resultado.associacoesCriadas()).isEqualTo(1);
        assertThat(professores.buscarPorEmail("clara@exemplo.test").orElseThrow().ativo()).isFalse();
    }

    @Test
    void simulacaoValidaEContaSemGravar() {
        ResultadoCarga resultado = casoDeUso.executar(exemplo(), true);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.simulacao()).isTrue();
        assertThat(resultado.professores()).isEqualTo(new Contagem(2, 0, 0));
        assertThat(resultado.alunos()).isEqualTo(new Contagem(2, 0, 0));
        assertThat(resultado.associacoesCriadas()).isEqualTo(3);
        assertThat(resultado.codigosEmitidos()).isEmpty();
        assertThat(professores.todos()).isEmpty();
        assertThat(alunos.todos()).isEmpty();
        assertThat(associacoes.todas()).isEmpty();
    }

    @Test
    void simulacaoSobreDadosExistentesNaoGrava() {
        casoDeUso.executar(exemplo(), false);
        ArquivoCarga alteracao =
                new ArquivoCarga(
                        List.of(new ProfessorCarga("clara@exemplo.test", "Outro Nome", true)),
                        List.of(
                                new AlunoCarga(
                                        CPF_MATTEO,
                                        "Outro",
                                        "2014-05-10",
                                        "R",
                                        null,
                                        List.of("bruno@exemplo.test"))));
        int gravacoes = professores.gravacoes();

        ResultadoCarga resultado = casoDeUso.executar(alteracao, true);

        assertThat(resultado.professores()).isEqualTo(new Contagem(0, 1, 0));
        assertThat(resultado.alunos()).isEqualTo(new Contagem(0, 1, 0));
        assertThat(resultado.associacoesCriadas()).isEqualTo(1);
        assertThat(professores.gravacoes()).isEqualTo(gravacoes);
        assertThat(associacoes.todas()).hasSize(3);
    }

    @Test
    void qualquerErroImpedeTodaGravacaoEIdentificaLinhaSemOValorDoCpf() {
        ArquivoCarga arquivo =
                new ArquivoCarga(
                        List.of(
                                new ProfessorCarga("clara@exemplo.test", "Clara", true),
                                new ProfessorCarga("CLARA@exemplo.test", " ", true),
                                new ProfessorCarga("sem-arroba", "X", true),
                                new ProfessorCarga(null, "Y", true)),
                        List.of(
                                new AlunoCarga(
                                        CPF_MATTEO,
                                        "Matteo",
                                        "2014-05-10",
                                        "Resp",
                                        null,
                                        List.of("clara@exemplo.test")),
                                new AlunoCarga(
                                        "529.982.247-25",
                                        "Duplicado",
                                        "2000-01-01",
                                        null,
                                        null,
                                        List.of()),
                                new AlunoCarga(
                                        "111.111.111-11",
                                        "",
                                        "2030-01-01",
                                        null,
                                        null,
                                        List.of("ninguem@exemplo.test")),
                                new AlunoCarga(
                                        CPF_OLAVO,
                                        "Menor",
                                        "2015-01-01",
                                        "  ",
                                        null,
                                        java.util.Arrays.asList((String) null)),
                                new AlunoCarga(
                                        "390.533.447-05", "Data ruim", "10/05/2014", null, null, List.of()),
                                new AlunoCarga(
                                        "245.781.963-64", "Sem data", null, null, null, List.of())));

        ResultadoCarga resultado = casoDeUso.executar(arquivo, false);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.erros())
                .extracting(ErroCarga::secao, ErroCarga::indice, ErroCarga::campo, ErroCarga::codigo)
                .containsExactlyInAnyOrder(
                        tuple("professores", 1, "email", CodigoErro.EMAIL_DUPLICADO_NO_ARQUIVO),
                        tuple("professores", 1, "nome", CodigoErro.NOME_OBRIGATORIO),
                        tuple("professores", 2, "email", CodigoErro.EMAIL_INVALIDO),
                        tuple("professores", 3, "email", CodigoErro.EMAIL_INVALIDO),
                        tuple("alunos", 1, "cpf", CodigoErro.CPF_DUPLICADO_NO_ARQUIVO),
                        tuple("alunos", 2, "nome", CodigoErro.NOME_OBRIGATORIO),
                        tuple("alunos", 2, "cpf", CodigoErro.CPF_INVALIDO),
                        tuple("alunos", 2, "dataNascimento", CodigoErro.DATA_NASCIMENTO_INVALIDA),
                        tuple("alunos", 2, "professores[0]", CodigoErro.PROFESSOR_INEXISTENTE),
                        tuple("alunos", 3, "nomeResponsavel", CodigoErro.RESPONSAVEL_OBRIGATORIO),
                        tuple("alunos", 3, "professores[0]", CodigoErro.PROFESSOR_INEXISTENTE),
                        tuple("alunos", 4, "dataNascimento", CodigoErro.DATA_NASCIMENTO_INVALIDA),
                        tuple("alunos", 5, "dataNascimento", CodigoErro.DATA_NASCIMENTO_INVALIDA));
        assertThat(resultado.erros().toString()).doesNotContain("52998224725", "529.982");
        assertThat(resultado.professores()).isEqualTo(new Contagem(0, 0, 0));
        assertThat(resultado.codigosEmitidos()).isEmpty();
        assertThat(professores.todos()).isEmpty();
        assertThat(alunos.todos()).isEmpty();
    }

    @Test
    void professorDaAssociacaoPodeVirDoBanco() {
        casoDeUso.executar(exemplo(), false);
        ArquivoCarga soAluno =
                new ArquivoCarga(
                        null,
                        List.of(
                                new AlunoCarga(
                                        "704.512.836-35",
                                        "Miguel de Castro Torrente",
                                        "2012-03-03",
                                        "Resp",
                                        "Violino",
                                        List.of("clara@exemplo.test"))));

        ResultadoCarga resultado = casoDeUso.executar(soAluno, false);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.alunos()).isEqualTo(new Contagem(1, 0, 0));
        assertThat(resultado.associacoesCriadas()).isEqualTo(1);
        assertThat(new ArquivoCarga(null, null).alunos()).isEmpty();
        assertThat(
                        new AlunoCarga("c", "n", "d", null, null, null).professores())
                .isEmpty();
        assertThat(LocalDate.parse("2012-03-03")).isNotNull();
    }
}
