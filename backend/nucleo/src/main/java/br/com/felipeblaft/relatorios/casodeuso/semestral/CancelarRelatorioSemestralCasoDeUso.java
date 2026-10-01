package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AcessoSemestralServico;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancelar o semestral em aberto (FR-031) ou descartar uma consolidação ainda em curso (retomada):
 * o resultado posterior do trabalho é ignorado.
 */
public class CancelarRelatorioSemestralCasoDeUso {

    private final AcessoSemestralServico acesso;
    private final RelatorioSemestralRepositorio semestrais;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public CancelarRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            RelatorioSemestralRepositorio semestrais,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.semestrais = Objects.requireNonNull(semestrais);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public void cancelar(UUID professorId, UUID relatorioId) {
        RelatorioSemestral relatorio = acesso.relatorioParaAtualizar(professorId, relatorioId);
        Instant agora = relogio.agora();
        relatorio.cancelar(agora);
        semestrais.salvar(relatorio);
        fila.buscarEmCursoDoRelatorio(TipoRelatorio.SEMESTRAL, relatorioId)
                .ifPresent(
                        trabalho -> {
                            trabalho.descartar(agora);
                            fila.atualizar(trabalho);
                        });
    }

    /** Descarta uma consolidação ainda em curso (sem semestral criado). */
    public void descartarConsolidacao(UUID professorId, UUID trabalhoId) {
        TrabalhoProcessamento trabalho =
                fila.buscarPorId(trabalhoId)
                        .filter(t -> t.professorId().equals(professorId))
                        .filter(t -> t.tipo() == TipoTrabalho.CONSOLIDACAO_SEMESTRAL)
                        .orElseThrow(() -> new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO));
        if (!trabalho.descartar(relogio.agora())) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        fila.atualizar(trabalho);
    }
}
