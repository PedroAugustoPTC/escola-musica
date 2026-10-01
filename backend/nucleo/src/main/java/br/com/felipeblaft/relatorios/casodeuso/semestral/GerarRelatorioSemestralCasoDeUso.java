package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso.Previa;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gerar o semestral (FR-042, FR-046, FR-034): confere as pré-condições na ordem de data-model.md §9
 * e enfileira a consolidação. Sem aulas aprovadas, nada é criado e a IA não é chamada.
 */
public class GerarRelatorioSemestralCasoDeUso {

    private final ObterPreviaSemestralCasoDeUso previa;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public GerarRelatorioSemestralCasoDeUso(
            ObterPreviaSemestralCasoDeUso previa, FilaTrabalhoPorta fila, RelogioPorta relogio) {
        this.previa = Objects.requireNonNull(previa);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public TrabalhoProcessamento executar(
            UUID professorId,
            UUID alunoId,
            String periodoChave,
            boolean confirmarMesmoComEmAberto,
            Canal canal) {
        Previa atual = previa.executar(professorId, alunoId, periodoChave);
        exigirPodeGerar(atual, confirmarMesmoComEmAberto);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.CONSOLIDACAO_SEMESTRAL,
                        professorId,
                        alunoId,
                        TipoRelatorio.SEMESTRAL,
                        null,
                        canal,
                        Meio.TEXTO,
                        EtapaTrabalho.CONSOLIDANDO,
                        relogio.agora());
        trabalho.definirParametro(TrabalhoProcessamento.PERIODO_CHAVE, atual.periodo().chave());
        if (confirmarMesmoComEmAberto) {
            trabalho.definirParametro(TrabalhoProcessamento.CONFIRMAR_EM_ABERTO, "true");
        }
        try {
            fila.enfileirar(trabalho);
        } catch (ConflitoConcorrenciaExcecao conflito) {
            exigirPodeGerar(previa.executar(professorId, alunoId, periodoChave), true);
            throw new ExcecaoDominio(CodigoErro.RELATORIO_EM_ABERTO);
        }
        return trabalho;
    }

    static void exigirPodeGerar(Previa previa, boolean confirmarMesmoComEmAberto) {
        if (previa.semestralAprovadoId().isPresent()) {
            throw new ExcecaoDominio(CodigoErro.SEMESTRAL_JA_APROVADO);
        }
        if (previa.semestralEmAberto().isPresent()) {
            throw new ExcecaoDominio(
                    CodigoErro.RELATORIO_EM_ABERTO,
                    Map.of("relatorioEmAberto", previa.semestralEmAberto().get().detalhes()));
        }
        if (previa.aulasAprovadas() == 0) {
            throw new ExcecaoDominio(CodigoErro.SEM_AULAS_APROVADAS_NO_PERIODO);
        }
        if (previa.relatoriosAulaEmAberto() > 0 && !confirmarMesmoComEmAberto) {
            throw new ExcecaoDominio(
                    CodigoErro.HA_RELATORIOS_EM_ABERTO,
                    Map.of("quantidade", previa.relatoriosAulaEmAberto()));
        }
    }
}
