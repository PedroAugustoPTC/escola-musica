package br.com.felipeblaft.relatorios.dominio.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.cadastro.BuscaAluno.TipoResultado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuscaAlunoTeste {

    private static Aluno aluno(String nome) {
        return Aluno.novo(nome, LocalDate.of(2012, 1, 1), new byte[] {1}, "h" + nome, null, null, Instant.EPOCH);
    }

    private final Aluno matteo = aluno("Matteo Mafra");
    private final Aluno olavo = aluno("Olavo Torrente");
    private final Aluno miguel = aluno("Miguel de Castro Torrente");
    private final Aluno helena = aluno("Hélena Ávila");
    private final List<Aluno> alunos = List.of(matteo, olavo, miguel, helena);

    private List<Aluno> buscar(String texto) {
        return BuscaAluno.buscar(alunos, texto).alunos();
    }

    @Test
    void buscaVaziaTrazTodos() {
        BuscaAluno.Resultado resultado = BuscaAluno.buscar(alunos, "   ");
        assertThat(resultado.tipo()).isEqualTo(TipoResultado.TODOS);
        assertThat(resultado.alunos()).hasSize(4);
        assertThat(BuscaAluno.buscar(alunos, null).tipo()).isEqualTo(TipoResultado.TODOS);
    }

    @Test
    void ignoraAcentosMaiusculasEEspacos() {
        assertThat(buscar("HELENA")).containsExactly(helena);
        assertThat(buscar("avila")).containsExactly(helena);
        assertThat(buscar("  olavo   TORRENTE ")).containsExactly(olavo);
    }

    @Test
    void aceitaPrefixoDePalavraESubstring() {
        assertThat(buscar("torr")).containsExactly(olavo, miguel);
        assertThat(buscar("miguel torr")).containsExactly(miguel);
        assertThat(buscar("afra")).containsExactly(matteo);
        assertThat(BuscaAluno.buscar(alunos, "torr").tipo()).isEqualTo(TipoResultado.ENCONTRADOS);
    }

    @Test
    void semCorrespondenciaTrazTodosComoNenhum() {
        BuscaAluno.Resultado resultado = BuscaAluno.buscar(alunos, "Beethoven");
        assertThat(resultado.tipo()).isEqualTo(TipoResultado.NENHUM);
        assertThat(resultado.alunos()).hasSize(4);
    }

    @Test
    void correspondenciaExataPorNomeCompletoOuPrimeiroNome() {
        assertThat(BuscaAluno.correspondenciaExata(alunos, "olavo")).contains(olavo);
        assertThat(BuscaAluno.correspondenciaExata(alunos, "Matteo Mafra")).contains(matteo);
        assertThat(BuscaAluno.correspondenciaExata(alunos, "helena")).contains(helena);
        assertThat(BuscaAluno.correspondenciaExata(alunos, "torrente")).isEmpty();
        assertThat(BuscaAluno.correspondenciaExata(alunos, "o aluno foi bem")).isEmpty();
        assertThat(BuscaAluno.correspondenciaExata(alunos, "")).isEmpty();
    }

    @Test
    void homonimosNaoSaoCorrespondenciaExata() {
        Aluno outroOlavo = aluno("Olavo Souza");
        assertThat(BuscaAluno.correspondenciaExata(List.of(olavo, outroOlavo), "olavo")).isEmpty();
        assertThat(BuscaAluno.normalizar(null)).isEmpty();
    }
}
