package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado e conteúdo vigente do relatório de aula, iguais nos dois canais (FR-002). Relatório de
 * outro professor é {@code RECURSO_NAO_ENCONTRADO}.
 */
public class ObterRelatorioAulaCasoDeUso {

    public record VisaoRelatorioAula(
            RelatorioAula relatorio,
            Aula aula,
            String alunoNome,
            String professorNome,
            Optional<InteracaoRelatorio> ultimaInteracao,
            Optional<TrabalhoProcessamento> trabalhoEmCurso,
            boolean pdfDisponivel) {}

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final AlunoRepositorio alunos;
    private final ProfessorRepositorio professores;
    private final InteracaoRelatorioRepositorio interacoes;
    private final VersaoRelatorioRepositorio versoes;
    private final DocumentoPdfRepositorio documentos;
    private final FilaTrabalhoPorta fila;

    public ObterRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            AlunoRepositorio alunos,
            ProfessorRepositorio professores,
            InteracaoRelatorioRepositorio interacoes,
            VersaoRelatorioRepositorio versoes,
            DocumentoPdfRepositorio documentos,
            FilaTrabalhoPorta fila) {
        this.acesso = Objects.requireNonNull(acesso);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.alunos = Objects.requireNonNull(alunos);
        this.professores = Objects.requireNonNull(professores);
        this.interacoes = Objects.requireNonNull(interacoes);
        this.versoes = Objects.requireNonNull(versoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.fila = Objects.requireNonNull(fila);
    }

    public VisaoRelatorioAula executar(UUID professorId, UUID relatorioId) {
        RelatorioAula relatorio = acesso.relatorioAula(professorId, relatorioId);
        Aula aula = relatorios.buscarAula(relatorio.aulaId()).orElseThrow();
        String aluno = alunos.buscarPorId(relatorio.alunoId()).orElseThrow().nome();
        String professor = professores.buscarPorId(relatorio.professorId()).orElseThrow().nome();
        boolean pdf =
                relatorio.versaoVigente() > 0
                        && versoes.buscar(TipoRelatorio.AULA, relatorio.id(), relatorio.versaoVigente())
                                .flatMap(v -> documentos.buscar(v.id(), TipoDocumento.CONFERENCIA))
                                .isPresent();
        return new VisaoRelatorioAula(
                relatorio,
                aula,
                aluno,
                professor,
                interacoes.ultima(TipoRelatorio.AULA, relatorio.id()),
                fila.buscarEmCursoDoRelatorio(TipoRelatorio.AULA, relatorio.id()),
                pdf);
    }
}
