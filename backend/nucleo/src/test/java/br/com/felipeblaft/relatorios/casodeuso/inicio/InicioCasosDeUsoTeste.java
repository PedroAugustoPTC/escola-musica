package br.com.felipeblaft.relatorios.casodeuso.inicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.BuscaAluno.TipoResultado;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dubles.CenarioAcesso;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ObterInicioProfessorCasoDeUso, BuscarAlunosCasoDeUso e ObterResumoAlunoCasoDeUso. */
class InicioCasosDeUsoTeste {

    private final CenarioAcesso c = new CenarioAcesso();
    private final ObterInicioProfessorCasoDeUso inicio =
            new ObterInicioProfessorCasoDeUso(c.alunos, c.emAberto);
    private final BuscarAlunosCasoDeUso buscar = new BuscarAlunosCasoDeUso(c.alunos, c.emAberto);
    private final ObterResumoAlunoCasoDeUso resumo =
            new ObterResumoAlunoCasoDeUso(c.alunos, c.emAberto, c.autorizacao);
    private final Professor clara = c.professor("Clara Souza", "clara@exemplo.test", "K7QM2XPA");
    private final Professor bruno = c.professor("Bruno Lima", "bruno@exemplo.test", "BRUN2XPA");

    private RelatorioEmAberto emAberto(Aluno aluno) {
        return new RelatorioEmAberto(
                TipoRelatorio.AULA,
                UUID.randomUUID(),
                null,
                aluno.id(),
                aluno.nome(),
                StatusRelatorio.AGUARDANDO_CONFERENCIA,
                null,
                null,
                Canal.TELEGRAM,
                c.relogio.agora());
    }

    @Test
    void inicioComSaudacaoTotalAteCincoAlunosERelatoriosEmAberto() {
        Aluno matteo = c.aluno(clara, "Matteo Mafra", "Piano");
        for (String nome : new String[] {"Ana", "Beatriz", "Caio", "Davi", "Olavo"}) {
            c.aluno(clara, nome, "Violão");
        }
        c.aluno(bruno, "Helena", "Flauta");
        c.emAberto.adicionar(clara.id(), emAberto(matteo));
        c.emAberto.definirUltimaAula(clara.id(), matteo.id(), LocalDate.of(2026, 9, 21));

        ObterInicioProfessorCasoDeUso.Inicio resultado = inicio.executar(clara);

        assertThat(resultado.professor().primeiroNome()).isEqualTo("Clara");
        assertThat(resultado.totalAlunos()).isEqualTo(6);
        assertThat(resultado.semAlunos()).isFalse();
        assertThat(resultado.alunos())
                .hasSize(5)
                .extracting(AlunoResumo::nome)
                .doesNotContain("Olavo");
        assertThat(resultado.relatoriosEmAberto()).hasSize(1);
        AlunoResumo primeiro = buscar.executar(clara.id(), "matteo").alunos().getFirst();
        assertThat(primeiro.possuiRelatorioEmAberto()).isTrue();
        assertThat(primeiro.ultimaAulaEm()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(primeiro.instrumento()).isEqualTo("Piano");
    }

    @Test
    void semAlunos() {
        ObterInicioProfessorCasoDeUso.Inicio resultado = inicio.executar(clara);
        assertThat(resultado.semAlunos()).isTrue();
        assertThat(resultado.alunos()).isEmpty();
    }

    @Test
    void buscaTodosEncontradosENenhum() {
        c.aluno(clara, "Matteo Mafra", "Piano");
        c.aluno(clara, "Olavo Torrente", "Violão");
        c.aluno(bruno, "Olívia Prado", "Flauta");

        assertThat(buscar.executar(clara.id(), "").tipo()).isEqualTo(TipoResultado.TODOS);
        BuscarAlunosCasoDeUso.Resultado encontrados = buscar.executar(clara.id(), "ola");
        assertThat(encontrados.tipo()).isEqualTo(TipoResultado.ENCONTRADOS);
        assertThat(encontrados.alunos())
                .extracting(AlunoResumo::nome)
                .containsExactly("Olavo Torrente");
        BuscarAlunosCasoDeUso.Resultado nenhum = buscar.executar(clara.id(), "Olívia");
        assertThat(nenhum.tipo()).isEqualTo(TipoResultado.NENHUM);
        assertThat(nenhum.alunos())
                .extracting(AlunoResumo::nome)
                .containsExactly("Matteo Mafra", "Olavo Torrente");
    }

    @Test
    void correspondenciaExataSoEntreOsAlunosDoProfessor() {
        c.aluno(clara, "Olavo Torrente", "Violão");
        c.aluno(bruno, "Helena Prado", "Flauta");
        assertThat(buscar.correspondenciaExata(clara.id(), "olavo"))
                .map(AlunoResumo::nome)
                .contains("Olavo Torrente");
        assertThat(buscar.correspondenciaExata(clara.id(), "helena")).isEmpty();
    }

    @Test
    void resumoExigeAssociacao() {
        Aluno matteo = c.aluno(clara, "Matteo Mafra", "Piano");
        Aluno helena = c.aluno(bruno, "Helena Prado", "Flauta");
        c.emAberto.adicionar(clara.id(), emAberto(matteo));

        AlunoResumo resultado = resumo.executar(clara.id(), matteo.id());
        assertThat(resultado.nome()).isEqualTo("Matteo Mafra");
        assertThat(resultado.possuiRelatorioEmAberto()).isTrue();

        assertThatThrownBy(() -> resumo.executar(clara.id(), helena.id()))
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(CodigoErro.ALUNO_NAO_ASSOCIADO);
    }

    @Test
    void alunoAssociadoMasRemovidoDoCadastro() {
        UUID fantasma = UUID.randomUUID();
        c.associacoes.associar(clara.id(), fantasma);
        assertThatThrownBy(() -> resumo.executar(clara.id(), fantasma))
                .isInstanceOf(ExcecaoDominio.class);
    }

    @Test
    void relatorioEmAbertoExpoeDetalhesSemTexto() {
        Aluno matteo = c.aluno(clara, "Matteo Mafra", "Piano");
        assertThat(emAberto(matteo).detalhes())
                .containsEntry("canalOrigem", Canal.TELEGRAM)
                .containsEntry("alunoId", matteo.id())
                .containsKeys(
                        "relatorioId",
                        "trabalhoId",
                        "status",
                        "etapa",
                        "periodoChave",
                        "atualizadoEm");
        assertThat(StatusRelatorio.AGUARDANDO_RESPOSTAS.emAberto()).isTrue();
        assertThat(StatusRelatorio.APROVADO.emAberto()).isFalse();
        assertThat(StatusRelatorio.CANCELADO.emAberto()).isFalse();
    }
}
