package br.com.felipeblaft.relatorios.casodeuso.inicio;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumo de um aluno associado. Aluno de outro professor (ou inexistente) é {@code
 * ALUNO_NAO_ASSOCIADO}, sem revelar nenhum dado (FR-009).
 */
public class ObterResumoAlunoCasoDeUso {

    private final AlunoRepositorio alunos;
    private final RelatoriosEmAbertoConsultaPorta consulta;
    private final AutorizacaoServico autorizacao;

    public ObterResumoAlunoCasoDeUso(
            AlunoRepositorio alunos,
            RelatoriosEmAbertoConsultaPorta consulta,
            AutorizacaoServico autorizacao) {
        this.alunos = Objects.requireNonNull(alunos);
        this.consulta = Objects.requireNonNull(consulta);
        this.autorizacao = Objects.requireNonNull(autorizacao);
    }

    public AlunoResumo executar(UUID professorId, UUID alunoId) {
        autorizacao.exigirAssociacao(professorId, alunoId);
        Aluno aluno =
                alunos.buscarPorId(alunoId)
                        .orElseThrow(() -> new ExcecaoDominio(CodigoErro.ALUNO_NAO_ASSOCIADO));
        return new AlunoResumo(
                aluno.id(),
                aluno.nome(),
                aluno.instrumento(),
                consulta.ultimaAulaPorAluno(professorId).get(alunoId),
                consulta.buscarDoAluno(professorId, alunoId).isPresent());
    }
}
