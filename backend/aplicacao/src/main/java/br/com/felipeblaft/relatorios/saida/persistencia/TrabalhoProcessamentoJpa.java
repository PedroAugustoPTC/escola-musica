package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.TrabalhoProcessamentoEntidade;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TrabalhoProcessamentoJpa extends JpaRepository<TrabalhoProcessamentoEntidade, UUID> {

    /** Próximo pendente sem disputar com outros workers (R-06). */
    @Query(
            value =
                    "select id from trabalho_processamento where status = 'PENDENTE'"
                            + " order by criado_em limit 1 for update skip locked",
            nativeQuery = true)
    Optional<UUID> proximoPendente();

    @Query(
            """
            select t from TrabalhoProcessamentoEntidade t
            where t.relatorioTipo = :tipo and t.relatorioId = :relatorioId
              and t.status in ('PENDENTE', 'EM_EXECUCAO')
            order by t.criadoEm desc
            """)
    List<TrabalhoProcessamentoEntidade> emCursoDoRelatorio(String tipo, UUID relatorioId);

    @Query(
            """
            select t from TrabalhoProcessamentoEntidade t
            where t.professorId = :professorId and t.alunoId = :alunoId and t.relatorioId is null
              and t.tipo in ('REGISTRO_AULA') and t.status in ('PENDENTE', 'EM_EXECUCAO')
            """)
    List<TrabalhoProcessamentoEntidade> registroEmCurso(UUID professorId, UUID alunoId);

    @Query(
            """
            select t from TrabalhoProcessamentoEntidade t
            where t.relatorioId is null and t.tipo = 'REGISTRO_AULA'
              and t.professorId = :professorId and t.status in ('PENDENTE', 'EM_EXECUCAO')
            order by t.criadoEm desc
            """)
    List<TrabalhoProcessamentoEntidade> registrosEmCursoDoProfessor(UUID professorId);

    @Query(
            """
            select t from TrabalhoProcessamentoEntidade t
            where t.status in ('PENDENTE', 'EM_EXECUCAO') and t.avisoDemoraEnviado = false
              and t.criadoEm <= :limite
            """)
    List<TrabalhoProcessamentoEntidade> demoradosSemAviso(Instant limite);

    List<TrabalhoProcessamentoEntidade> findByStatus(String status);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
            value =
                    "update trabalho_processamento set aviso_demora_enviado = true"
                            + " where id = :id and aviso_demora_enviado = false",
            nativeQuery = true)
    int marcarAvisoDemora(UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
            value =
                    "update trabalho_processamento set parametros ="
                            + " coalesce(parametros, '{}'::jsonb) || jsonb_build_object(:chave, cast(:valor as text))"
                            + " where id = :id",
            nativeQuery = true)
    int definirParametro(UUID id, String chave, String valor);

    @Query(
            value =
                    "select * from trabalho_processamento where tipo = 'CONSOLIDACAO_SEMESTRAL'"
                            + " and status in ('PENDENTE', 'EM_EXECUCAO') and professor_id = :professorId"
                            + " and aluno_id = :alunoId and parametros ->> 'periodoChave' = :periodoChave",
            nativeQuery = true)
    List<TrabalhoProcessamentoEntidade> consolidacaoEmCurso(
            UUID professorId, UUID alunoId, String periodoChave);

    @Query(
            """
            select t from TrabalhoProcessamentoEntidade t
            where t.tipo = 'CONSOLIDACAO_SEMESTRAL' and t.relatorioId is null
              and t.professorId = :professorId and t.status in ('PENDENTE', 'EM_EXECUCAO')
            order by t.criadoEm desc
            """)
    List<TrabalhoProcessamentoEntidade> consolidacoesEmCursoDoProfessor(UUID professorId);
}
