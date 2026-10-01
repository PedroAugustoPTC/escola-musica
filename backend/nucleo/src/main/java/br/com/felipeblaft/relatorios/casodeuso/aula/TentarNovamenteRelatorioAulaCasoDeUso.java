package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import java.util.Objects;
import java.util.UUID;

/** Tentar de novo a estruturação sem reenviar o áudio: a transcrição está guardada (FR-022). */
public class TentarNovamenteRelatorioAulaCasoDeUso {

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public TentarNovamenteRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public TrabalhoProcessamento executar(UUID professorId, UUID relatorioId, Canal canal) {
        RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
        relatorio.prepararNovaTentativa(relogio.agora());
        relatorios.salvar(relatorio);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.NOVA_TENTATIVA,
                        professorId,
                        relatorio.alunoId(),
                        TipoRelatorio.AULA,
                        relatorio.id(),
                        canal,
                        Meio.TEXTO,
                        EtapaTrabalho.ESTRUTURANDO,
                        relogio.agora());
        fila.enfileirar(trabalho);
        return trabalho;
    }
}
