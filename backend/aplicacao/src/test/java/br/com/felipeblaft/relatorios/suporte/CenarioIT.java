package br.com.felipeblaft.relatorios.suporte;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Cpf;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.ProcessadorTrabalhoServico;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
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
    private final FilaTrabalhoPorta fila;
    private final ProcessadorTrabalhoServico processador;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio relatoriosAula;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio versoes;

    @org.springframework.beans.factory.annotation.Autowired
    private br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico documentos;

    private int sequenciaCpf;

    public CenarioIT(
            ProfessorRepositorio professores,
            AlunoRepositorio alunos,
            AssociacaoRepositorio associacoes,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            FilaTrabalhoPorta fila,
            ProcessadorTrabalhoServico processador) {
        this.professores = professores;
        this.alunos = alunos;
        this.associacoes = associacoes;
        this.protecao = protecao;
        this.relogio = relogio;
        this.fila = fila;
        this.processador = processador;
    }

    /**
     * Processa a fila até esvaziar, como o worker faria (os workers ficam desligados nos testes).
     */
    public int processarFila() {
        int processados = 0;
        Optional<TrabalhoProcessamento> proximo;
        while ((proximo = fila.reservarProximo(relogio.agora())).isPresent()) {
            processador.processar(proximo.get());
            processados++;
        }
        return processados;
    }

    public Professor professor(String nome, String email, String codigo) {
        Professor professor = Professor.novo(nome, email, true, relogio.agora());
        professor.substituirCodigo(
                protecao.hashCodigoAcesso(CodigoAcesso.de(codigo)),
                relogio.agora(),
                Duration.ofDays(7));
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

    /** Relatório de aula já aprovado, com aula na data dada (base do semestral e do histórico). */
    public br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula aulaAprovada(
            Professor professor, Aluno aluno, LocalDate data, String conteudo) {
        java.time.Instant agora = relogio.agora();
        br.com.felipeblaft.relatorios.dominio.relatorio.Aula aula =
                br.com.felipeblaft.relatorios.dominio.relatorio.Aula.nova(
                        professor.id(), aluno.id(), data, agora);
        relatoriosAula.salvarAula(aula);
        br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula relatorio =
                new br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula(
                        br.com.felipeblaft.relatorios.dominio.comum.GeradorId.novo(),
                        aula.id(),
                        professor.id(),
                        aluno.id(),
                        "transcrição fictícia",
                        new br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula(
                                java.util.List.of(conteudo),
                                "Evoluiu bem.",
                                java.util.List.of(),
                                java.util.List.of(),
                                ""),
                        java.util.List.of(),
                        0,
                        br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio.APROVADO,
                        null,
                        1,
                        br.com.felipeblaft.relatorios.dominio.comum.Canal.WEB,
                        null,
                        agora,
                        agora,
                        agora,
                        1,
                        br.com.felipeblaft.relatorios.dominio.comum.Canal.WEB,
                        null,
                        0,
                        0);
        relatoriosAula.salvar(relatorio);
        br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio versao =
                br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio.nova(
                        br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio.AULA,
                        relatorio.id(),
                        1,
                        relatorio.conteudo(),
                        br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao
                                .ESTRUTURACAO_INICIAL,
                        null,
                        br.com.felipeblaft.relatorios.dominio.comum.Canal.WEB,
                        "estruturacao-aula.v1",
                        agora);
        versoes.salvar(versao);
        documentos.gerarOficial(relatorio, versao);
        return relatorio;
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
