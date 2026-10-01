package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Visão derivada "relatório em aberto" (data-model.md): relatórios de aula não aprovados nem
 * cancelados, registros ainda em transcrição e, quando houver, consultas complementares
 * (semestral).
 */
@Component
public class RelatoriosEmAbertoConsultaAdaptador implements RelatoriosEmAbertoConsultaPorta {

    /** Fonte adicional de relatórios em aberto (ex.: semestrais), registrada por outra história. */
    public interface FonteEmAberto {
        List<RelatorioEmAberto> listarDoProfessor(UUID professorId);
    }

    private final RelatorioAulaJpa relatorios;
    private final FilaTrabalhoPostgresAdaptador fila;
    private final AlunoJpa alunos;
    private final JdbcTemplate jdbc;
    private final List<FonteEmAberto> fontes = new ArrayList<>();

    RelatoriosEmAbertoConsultaAdaptador(
            RelatorioAulaJpa relatorios,
            FilaTrabalhoPostgresAdaptador fila,
            AlunoJpa alunos,
            JdbcTemplate jdbc) {
        this.relatorios = relatorios;
        this.fila = fila;
        this.alunos = alunos;
        this.jdbc = jdbc;
    }

    public void registrarFonte(FonteEmAberto fonte) {
        fontes.add(fonte);
    }

    @Override
    public List<RelatorioEmAberto> listarDoProfessor(UUID professorId) {
        Map<UUID, String> nomes = new HashMap<>();
        List<RelatorioEmAberto> lista = new ArrayList<>();
        for (RelatorioAulaEntidade r : relatorios.listarEmAbertoDoProfessor(professorId)) {
            lista.add(deRelatorio(r, nome(nomes, r.alunoId)));
        }
        for (TrabalhoProcessamento t : fila.registrosEmCursoDoProfessor(professorId)) {
            lista.add(deTrabalho(t, nome(nomes, t.alunoId())));
        }
        for (FonteEmAberto fonte : fontes) {
            lista.addAll(fonte.listarDoProfessor(professorId));
        }
        lista.sort(Comparator.comparing(RelatorioEmAberto::atualizadoEm).reversed());
        return lista;
    }

    @Override
    public Optional<RelatorioEmAberto> buscarDoAluno(UUID professorId, UUID alunoId) {
        Optional<RelatorioAulaEntidade> relatorio = relatorios.buscarEmAberto(professorId, alunoId);
        if (relatorio.isPresent()) {
            return Optional.of(deRelatorio(relatorio.get(), nome(new HashMap<>(), alunoId)));
        }
        return fila.buscarRegistroEmCurso(professorId, alunoId)
                .map(t -> deTrabalho(t, nome(new HashMap<>(), alunoId)));
    }

    @Override
    public Map<UUID, LocalDate> ultimaAulaPorAluno(UUID professorId) {
        Map<UUID, LocalDate> ultimas = new HashMap<>();
        jdbc.query(
                "select a.aluno_id, max(a.data_aula) as ultima from aula a"
                        + " join relatorio_aula r on r.aula_id = a.id"
                        + " where a.professor_id = ? and r.status <> 'CANCELADO' group by a.aluno_id",
                linha -> {
                    Date data = linha.getDate("ultima");
                    ultimas.put(linha.getObject("aluno_id", UUID.class), data.toLocalDate());
                },
                professorId);
        return ultimas;
    }

    private String nome(Map<UUID, String> cache, UUID alunoId) {
        return cache.computeIfAbsent(
                alunoId, id -> alunos.findById(id).map(AlunoEntidade::getNome).orElse(""));
    }

    private static RelatorioEmAberto deRelatorio(RelatorioAulaEntidade r, String alunoNome) {
        return new RelatorioEmAberto(
                TipoRelatorio.AULA,
                r.id,
                null,
                r.alunoId,
                alunoNome,
                StatusRelatorio.valueOf(r.status),
                r.etapaProcessamento == null ? null : etapaTrabalho(r.etapaProcessamento),
                null,
                Canal.valueOf(r.canalOrigem),
                r.atualizadoEm);
    }

    private static EtapaTrabalho etapaTrabalho(String etapaRelatorio) {
        try {
            return EtapaTrabalho.valueOf(etapaRelatorio);
        } catch (IllegalArgumentException semEquivalente) {
            return null;
        }
    }

    private static RelatorioEmAberto deTrabalho(TrabalhoProcessamento t, String alunoNome) {
        return new RelatorioEmAberto(
                TipoRelatorio.AULA,
                null,
                t.id(),
                t.alunoId(),
                alunoNome,
                StatusRelatorio.EM_PROCESSAMENTO,
                t.etapa(),
                null,
                t.canalSolicitacao(),
                t.iniciadoEm() != null ? t.iniciadoEm() : t.criadoEm());
    }
}
