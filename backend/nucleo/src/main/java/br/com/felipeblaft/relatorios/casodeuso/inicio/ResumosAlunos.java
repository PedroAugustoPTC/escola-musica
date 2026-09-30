package br.com.felipeblaft.relatorios.casodeuso.inicio;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Monta os resumos de alunos com última aula e marca "Em aberto" (FR-035). */
record ResumosAlunos(Map<UUID, LocalDate> ultimasAulas, Set<UUID> alunosEmAberto) {

    static ResumosAlunos carregar(
            RelatoriosEmAbertoConsultaPorta consulta,
            UUID professorId,
            List<RelatorioEmAberto> emAberto) {
        return new ResumosAlunos(
                consulta.ultimaAulaPorAluno(professorId),
                emAberto.stream().map(RelatorioEmAberto::alunoId).collect(Collectors.toSet()));
    }

    AlunoResumo resumo(Aluno aluno) {
        return AlunoResumo.de(
                aluno, ultimasAulas.get(aluno.id()), alunosEmAberto.contains(aluno.id()));
    }

    List<AlunoResumo> resumos(List<Aluno> alunos) {
        return alunos.stream().map(this::resumo).toList();
    }
}
