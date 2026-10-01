package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aprovação explícita da versão apresentada (FR-028 a FR-030): irreversível; grava data, hora,
 * versão e canal e gera o PDF oficial. Numa aprovação concorrente, a linha bloqueada faz a segunda
 * ver o relatório já aprovado ({@code RELATORIO_JA_APROVADO}).
 */
public class AprovarRelatorioAulaCasoDeUso {

    public record Aprovacao(UUID relatorioId, int versaoAprovada, Instant aprovadoEm) {}

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final VersaoRelatorioRepositorio versoes;
    private final GeracaoDocumentoServico documentos;
    private final RelogioPorta relogio;

    public AprovarRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            VersaoRelatorioRepositorio versoes,
            GeracaoDocumentoServico documentos,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.versoes = Objects.requireNonNull(versoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public Aprovacao executar(UUID professorId, UUID relatorioId, int versaoConfirmada, Canal canal) {
        RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
        relatorio.aprovar(versaoConfirmada, canal, relogio.agora());
        relatorios.salvar(relatorio);
        VersaoRelatorio versao =
                versoes.buscar(TipoRelatorio.AULA, relatorio.id(), relatorio.versaoAprovada()).orElseThrow();
        documentos.gerarOficial(relatorio, versao);
        return new Aprovacao(relatorio.id(), relatorio.versaoAprovada(), relatorio.aprovadoEm());
    }
}
