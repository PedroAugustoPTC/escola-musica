package br.com.felipeblaft.relatorios.suporte;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Cpf;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Cria cadastros fictícios com códigos conhecidos para os testes de integração. */
@Component
public class CenarioIT {

    public static final String CODIGO_CLARA = "K7QM2XPA";
    public static final String CODIGO_BRUNO = "BRUN2XPA";

    private final ProfessorRepositorio professores;
    private final AlunoRepositorio alunos;
    private final AssociacaoRepositorio associacoes;
    private final ProtecaoDadosPorta protecao;
    private final RelogioPorta relogio;
    private int sequenciaCpf;

    public CenarioIT(
            ProfessorRepositorio professores,
            AlunoRepositorio alunos,
            AssociacaoRepositorio associacoes,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio) {
        this.professores = professores;
        this.alunos = alunos;
        this.associacoes = associacoes;
        this.protecao = protecao;
        this.relogio = relogio;
    }

    public Professor professor(String nome, String email, String codigo) {
        Professor professor = Professor.novo(nome, email, true, relogio.agora());
        professor.substituirCodigo(
                protecao.hashCodigoAcesso(CodigoAcesso.de(codigo)), relogio.agora(), Duration.ofDays(7));
        professores.salvar(professor);
        return professor;
    }

    public Professor clara() {
        return professor("Clara Souza", "clara@exemplo.test", CODIGO_CLARA);
    }

    public Professor bruno() {
        return professor("Bruno Lima", "bruno@exemplo.test", CODIGO_BRUNO);
    }

    public Aluno aluno(Professor professor, String nome, String instrumento) {
        Cpf cpf = Cpf.de(cpfValido(++sequenciaCpf));
        Aluno aluno =
                Aluno.novo(
                        nome,
                        LocalDate.of(2012, 5, 10),
                        protecao.cifrarCpf(cpf),
                        protecao.hashCpf(cpf),
                        "Responsável Fictício",
                        instrumento,
                        relogio.agora());
        alunos.salvar(aluno);
        associacoes.salvar(ProfessorAluno.nova(professor.id(), aluno.id(), relogio.agora()));
        return aluno;
    }

    /** CPF fictício válido a partir de um número de sequência. */
    static String cpfValido(int n) {
        String base = String.format("%09d", 100_000_000 + n * 7919);
        int d1 = digito(base, 9);
        int d2 = digito(base + d1, 10);
        return base + d1 + d2;
    }

    private static int digito(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
