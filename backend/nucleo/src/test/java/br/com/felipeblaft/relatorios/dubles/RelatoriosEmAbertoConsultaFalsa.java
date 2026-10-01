package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consulta programável: o teste define os relatórios em aberto e as últimas aulas. */
public class RelatoriosEmAbertoConsultaFalsa implements RelatoriosEmAbertoConsultaPorta {

    private final Map<UUID, List<RelatorioEmAberto>> porProfessor = new HashMap<>();
    private final Map<UUID, Map<UUID, LocalDate>> ultimasAulas = new HashMap<>();

    public void adicionar(UUID professorId, RelatorioEmAberto relatorio) {
        porProfessor.computeIfAbsent(professorId, k -> new ArrayList<>()).add(relatorio);
    }

    public void definirUltimaAula(UUID professorId, UUID alunoId, LocalDate data) {
        ultimasAulas.computeIfAbsent(professorId, k -> new HashMap<>()).put(alunoId, data);
    }

    @Override
    public List<RelatorioEmAberto> listarDoProfessor(UUID professorId) {
        return List.copyOf(porProfessor.getOrDefault(professorId, List.of()));
    }

    @Override
    public Optional<RelatorioEmAberto> buscarDoAluno(UUID professorId, UUID alunoId) {
        return listarDoProfessor(professorId).stream()
                .filter(r -> r.alunoId().equals(alunoId))
                .filter(
                        r ->
                                r.tipo()
                                        == br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio
                                                .AULA)
                .findFirst();
    }

    @Override
    public Map<UUID, LocalDate> ultimaAulaPorAluno(UUID professorId) {
        return Map.copyOf(ultimasAulas.getOrDefault(professorId, Map.of()));
    }
}
