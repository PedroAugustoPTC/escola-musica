package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancelar (ou descartar na retomada) um relatório em aberto (FR-031): deixa de aparecer para o
 * professor, fica registrado como cancelado e o resultado de um trabalho em curso é descartado.
 */
public class CancelarRelatorioAulaCasoDeUso {

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public CancelarRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public void cancelar(UUID professorId, UUID relatorioId) {
        executar(professorId, relatorioId, false);
    }

    /** "Descartar e começar um novo" (retomada): também vale para relatório em processamento. */
    public void descartar(UUID professorId, UUID relatorioId) {
        executar(professorId, relatorioId, true);
    }

    private void executar(UUID professorId, UUID relatorioId, boolean descarte) {
        RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
        Instant agora = relogio.agora();
        relatorio.cancelar(agora, descarte);
        relatorios.salvar(relatorio);
        fila.buscarEmCursoDoRelatorio(TipoRelatorio.AULA, relatorioId)
                .ifPresent(
                        trabalho -> {
                            trabalho.descartar(agora);
                            fila.atualizar(trabalho);
                        });
    }
}
