package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RelatorioAulaJpa extends JpaRepository<RelatorioAulaEntidade, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RelatorioAulaEntidade r where r.id = :id")
    Optional<RelatorioAulaEntidade> buscarParaAtualizar(UUID id);

    @Query(
            """
            select r from RelatorioAulaEntidade r
            where r.professorId = :professorId and r.alunoId = :alunoId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
            """)
    Optional<RelatorioAulaEntidade> buscarEmAberto(UUID professorId, UUID alunoId);

    @Query(
            """
            select r from RelatorioAulaEntidade r
            where r.professorId = :professorId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
            order by r.atualizadoEm desc
            """)
    List<RelatorioAulaEntidade> listarEmAbertoDoProfessor(UUID professorId);

    /** Aprovados do par com aula no período, com a data da aula, em ordem cronológica. */
    @Query(
            """
            select r, a.dataAula from RelatorioAulaEntidade r, AulaEntidade a
            where a.id = r.aulaId and r.professorId = :professorId and r.alunoId = :alunoId
              and r.status = 'APROVADO' and a.dataAula between :inicio and :fim
            order by a.dataAula, r.aprovadoEm
            """)
    List<Object[]> aprovadosNoPeriodo(
            UUID professorId, UUID alunoId, java.time.LocalDate inicio, java.time.LocalDate fim);

    @Query(
            """
            select count(r) from RelatorioAulaEntidade r, AulaEntidade a
            where a.id = r.aulaId and r.professorId = :professorId and r.alunoId = :alunoId
              and r.status in ('EM_PROCESSAMENTO', 'FALHA_PROCESSAMENTO', 'AGUARDANDO_RESPOSTAS', 'AGUARDANDO_CONFERENCIA')
              and a.dataAula between :inicio and :fim
            """)
    long contarEmAbertoNoPeriodo(
            UUID professorId, UUID alunoId, java.time.LocalDate inicio, java.time.LocalDate fim);

    @Query(
            "select min(a.dataAula) from AulaEntidade a where a.professorId = :professorId and a.alunoId = :alunoId")
    Optional<java.time.LocalDate> primeiraAula(UUID professorId, UUID alunoId);
}
