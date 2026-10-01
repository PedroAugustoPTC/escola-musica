package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Relatório de aula (ou registro ainda em transcrição) em aberto para o aluno, com canal de origem
 * e última alteração (FR-034): base do aviso "Você tem um relatório em aberto" nos dois canais.
 */
public class ObterRelatorioEmAbertoCasoDeUso {

    private final AutorizacaoServico autorizacao;
    private final RelatoriosEmAbertoConsultaPorta consulta;

    public ObterRelatorioEmAbertoCasoDeUso(
            AutorizacaoServico autorizacao, RelatoriosEmAbertoConsultaPorta consulta) {
        this.autorizacao = Objects.requireNonNull(autorizacao);
        this.consulta = Objects.requireNonNull(consulta);
    }

    public Optional<RelatorioEmAberto> executar(UUID professorId, UUID alunoId) {
        autorizacao.exigirAssociacao(professorId, alunoId);
        return consulta.buscarDoAluno(professorId, alunoId);
    }
}
