package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterInicioProfessorCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;

/** Conversão dos resultados de início e alunos para os DTOs do contrato. */
final class InicioMapeador {

    private InicioMapeador() {}

    static ProfessorResumoDto professor(Professor professor) {
        return new ProfessorResumoDto(professor.id(), professor.nome());
    }

    static AlunoResumoDto aluno(AlunoResumo aluno) {
        return new AlunoResumoDto(
                aluno.id(),
                aluno.nome(),
                aluno.instrumento(),
                aluno.ultimaAulaEm(),
                aluno.possuiRelatorioEmAberto());
    }

    static RelatorioEmAbertoDto emAberto(RelatorioEmAberto r) {
        return new RelatorioEmAbertoDto(
                r.tipo().name(),
                r.relatorioId(),
                r.trabalhoId(),
                r.alunoId(),
                r.alunoNome(),
                r.status() == null ? null : r.status().name(),
                r.etapa() == null ? null : r.etapa().name(),
                r.periodoChave(),
                r.canalOrigem().name(),
                r.atualizadoEm());
    }

    static InicioDto inicio(ObterInicioProfessorCasoDeUso.Inicio inicio, int limiteAudioMb) {
        return new InicioDto(
                professor(inicio.professor()),
                inicio.totalAlunos(),
                inicio.alunos().stream().map(InicioMapeador::aluno).toList(),
                inicio.relatoriosEmAberto().stream().map(InicioMapeador::emAberto).toList(),
                limiteAudioMb);
    }

    static BuscaAlunosDto busca(BuscarAlunosCasoDeUso.Resultado resultado) {
        return new BuscaAlunosDto(
                resultado.tipo().name(),
                resultado.alunos().stream().map(InicioMapeador::aluno).toList());
    }
}
