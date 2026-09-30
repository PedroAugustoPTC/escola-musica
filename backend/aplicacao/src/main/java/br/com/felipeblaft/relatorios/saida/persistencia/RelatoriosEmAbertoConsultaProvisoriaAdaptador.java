package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Consulta provisória enquanto não existem as tabelas de relatório (substituída por
 * RelatoriosEmAbertoConsultaAdaptador na história de registro de aula).
 */
@Component
class RelatoriosEmAbertoConsultaProvisoriaAdaptador implements RelatoriosEmAbertoConsultaPorta {

    @Override
    public List<RelatorioEmAberto> listarDoProfessor(UUID professorId) {
        return List.of();
    }

    @Override
    public Optional<RelatorioEmAberto> buscarDoAluno(UUID professorId, UUID alunoId) {
        return Optional.empty();
    }

    @Override
    public Map<UUID, LocalDate> ultimaAulaPorAluno(UUID professorId) {
        return Map.of();
    }
}
