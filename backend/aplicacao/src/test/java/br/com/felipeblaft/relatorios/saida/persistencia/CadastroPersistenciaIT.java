package br.com.felipeblaft.relatorios.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Cpf;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.saida.seguranca.AcessoDecifragem;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CadastroPersistenciaIT extends IntegracaoBase {

    @Autowired ProfessorRepositorio professores;
    @Autowired AlunoRepositorio alunos;
    @Autowired AssociacaoRepositorio associacoes;
    @Autowired ProtecaoDadosPorta protecao;

    private final Instant agora = Instant.parse("2026-09-21T13:00:00.123456Z");

    @Test
    void professorIdaEVolta() {
        Professor clara = Professor.novo("Clara Souza", "clara@exemplo.test", true, agora);
        clara.substituirCodigo(
                protecao.hashCodigoAcesso(CodigoAcesso.de("K7QM2XPA")), agora, Duration.ofDays(7));
        professores.salvar(clara);

        Professor lido = professores.buscarPorEmail("clara@exemplo.test").orElseThrow();
        assertThat(lido.id()).isEqualTo(clara.id());
        assertThat(lido.nome()).isEqualTo("Clara Souza");
        assertThat(lido.codigoAcessoExpiraEm())
                .isEqualTo(agora.plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS));
        assertThat(professores.buscarPorCodigoAcessoHash(clara.codigoAcessoHash()))
                .map(Professor::id)
                .contains(clara.id());
        assertThat(professores.buscarPorId(clara.id())).isPresent();

        lido.atualizarCadastro("Clara S.", false, agora.plusSeconds(1));
        professores.salvar(lido);
        assertThat(professores.buscarPorId(clara.id()).orElseThrow().ativo()).isFalse();
    }

    @Test
    void cpfCifradoNuncaLegivelNoBancoEHmacEstavel() {
        Cpf cpf = Cpf.de("529.982.247-25");
        Aluno matteo =
                Aluno.novo(
                        "Matteo Mafra",
                        LocalDate.of(2014, 5, 10),
                        protecao.cifrarCpf(cpf),
                        protecao.hashCpf(cpf),
                        "Resp",
                        "Piano",
                        agora);
        alunos.salvar(matteo);

        byte[] noBanco =
                jdbc.queryForObject(
                        "select cpf_cifrado from aluno where id = ?", byte[].class, matteo.id());
        assertThat(new String(noBanco, StandardCharsets.ISO_8859_1)).doesNotContain("52998224725");
        assertThat(noBanco.length).isGreaterThan(12 + 11);
        assertThat(protecao.cifrarCpf(cpf)).isNotEqualTo(noBanco);
        assertThat(protecao.hashCpf(Cpf.de("52998224725"))).isEqualTo(matteo.cpfHash()).hasSize(64);
        assertThat(protecao.hashCpf(cpf)).isNotEqualTo(protecao.hashOrigem(Canal.WEB, "52998224725"));
        assertThat(AcessoDecifragem.decifrar(protecao, noBanco))
                .isEqualTo("52998224725");

        String colunas =
                String.join(
                        ",",
                        jdbc.queryForList(
                                "select column_name from information_schema.columns where table_name = 'aluno'",
                                String.class));
        assertThat(colunas).doesNotContain("cpf,").contains("cpf_cifrado", "cpf_hash");

        Aluno lido = alunos.buscarPorCpfHash(matteo.cpfHash()).orElseThrow();
        assertThat(lido.nome()).isEqualTo("Matteo Mafra");
        assertThat(lido.cpfCifrado()).isEqualTo(noBanco);
    }

    @Test
    void associacaoELista() {
        Professor clara = Professor.novo("Clara", "clara@exemplo.test", true, agora);
        Professor bruno = Professor.novo("Bruno", "bruno@exemplo.test", true, agora);
        professores.salvar(clara);
        professores.salvar(bruno);
        Aluno olavo = aluno("Olavo Torrente", "123.456.789-09");
        Aluno matteo = aluno("matteo Mafra", "529.982.247-25");
        Aluno helena = aluno("Helena Prado", "318.264.597-82");
        associacoes.salvar(ProfessorAluno.nova(clara.id(), olavo.id(), agora));
        associacoes.salvar(ProfessorAluno.nova(clara.id(), matteo.id(), agora));
        associacoes.salvar(ProfessorAluno.nova(bruno.id(), helena.id(), agora));

        assertThat(associacoes.existe(clara.id(), olavo.id())).isTrue();
        assertThat(associacoes.existe(clara.id(), helena.id())).isFalse();
        assertThat(alunos.listarDoProfessor(clara.id()))
                .extracting(Aluno::nome)
                .containsExactly("matteo Mafra", "Olavo Torrente");
    }

    private Aluno aluno(String nome, String cpfTexto) {
        Cpf cpf = Cpf.de(cpfTexto);
        Aluno aluno =
                Aluno.novo(
                        nome,
                        LocalDate.of(2000, 1, 1),
                        protecao.cifrarCpf(cpf),
                        protecao.hashCpf(cpf),
                        null,
                        null,
                        agora);
        alunos.salvar(aluno);
        return aluno;
    }

}
