package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso.Aprovacao;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.AcessoSemestralServico;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import java.util.Objects;
import java.util.UUID;

/** Aprovação do semestral na versão apresentada (FR-043, FR-046): gera o PDF oficial. */
public class AprovarRelatorioSemestralCasoDeUso {

    private final AcessoSemestralServico acesso;
    private final RelatorioSemestralRepositorio semestrais;
    private final VersaoRelatorioRepositorio versoes;
    private final GeracaoDocumentoServico documentos;
    private final RelogioPorta relogio;

    public AprovarRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            RelatorioSemestralRepositorio semestrais,
            VersaoRelatorioRepositorio versoes,
            GeracaoDocumentoServico documentos,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.semestrais = Objects.requireNonNull(semestrais);
        this.versoes = Objects.requireNonNull(versoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public Aprovacao executar(
            UUID professorId, UUID relatorioId, int versaoConfirmada, Canal canal) {
        RelatorioSemestral relatorio = acesso.relatorioParaAtualizar(professorId, relatorioId);
        relatorio.aprovar(versaoConfirmada, canal, relogio.agora());
        semestrais.salvar(relatorio);
        VersaoRelatorio versao =
                versoes.buscar(TipoRelatorio.SEMESTRAL, relatorio.id(), relatorio.versaoAprovada())
                        .orElseThrow();
        documentos.gerarOficial(relatorio, versao);
        return new Aprovacao(relatorio.id(), relatorio.versaoAprovada(), relatorio.aprovadoEm());
    }
}
