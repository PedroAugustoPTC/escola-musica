package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso.VisaoRelatorioAula;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso.VisaoTrabalho;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.AprovacaoDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.ConteudoRelatorioAulaDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.InteracaoDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.RelatorioAulaDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.TrabalhoDto;
import br.com.felipeblaft.relatorios.entrada.web.RelatorioAulaDtos.TrabalhoRefDto;

/** Conversão do relatório de aula e dos trabalhos para os DTOs do contrato. */
final class RelatorioAulaMapeador {

    private RelatorioAulaMapeador() {}

    static TrabalhoRefDto referencia(TrabalhoProcessamento trabalho) {
        return new TrabalhoRefDto(trabalho.id());
    }

    static TrabalhoDto trabalho(VisaoTrabalho visao) {
        TrabalhoProcessamento t = visao.trabalho();
        return new TrabalhoDto(
                t.id(),
                t.tipo().name(),
                t.status().name(),
                t.etapa().name(),
                visao.demorando(),
                t.resultado() == null ? null : t.resultado().name(),
                t.relatorioTipo() == null ? null : t.relatorioTipo().name(),
                t.relatorioId(),
                visao.textoEntendido().orElse(null));
    }

    static ConteudoRelatorioAulaDto conteudo(ConteudoRelatorioAula c) {
        ConteudoRelatorioAula conteudo = c == null ? ConteudoRelatorioAula.vazio() : c;
        return new ConteudoRelatorioAulaDto(
                conteudo.conteudosTrabalhados(),
                conteudo.evolucao(),
                conteudo.dificuldades(),
                conteudo.atividadesPropostas(),
                conteudo.observacoes());
    }

    static InteracaoDto interacao(InteracaoRelatorio i) {
        return new InteracaoDto(
                i.tipo().name(), i.canal().name(), i.meio().name(), i.texto(), i.criadoEm());
    }

    static RelatorioAulaDto relatorio(VisaoRelatorioAula visao) {
        RelatorioAula r = visao.relatorio();
        String etapa =
                visao.trabalhoEmCurso()
                        .map(t -> t.etapa().name())
                        .orElse(r.etapa() == null ? null : r.etapa().name());
        return new RelatorioAulaDto(
                r.id(),
                r.alunoId(),
                visao.alunoNome(),
                visao.professorNome(),
                visao.aula().dataAula(),
                r.status().name(),
                etapa,
                visao.trabalhoEmCurso().map(TrabalhoProcessamento::id).orElse(null),
                r.versaoVigente(),
                r.canalOrigem().name(),
                conteudo(r.conteudo()),
                r.perguntasPendentes(),
                r.rodadasPerguntas(),
                visao.ultimaInteracao().map(RelatorioAulaMapeador::interacao).orElse(null),
                visao.pdfDisponivel(),
                r.aprovadoEm(),
                r.atualizadoEm());
    }

    static AprovacaoDto aprovacao(AprovarRelatorioAulaCasoDeUso.Aprovacao aprovacao) {
        return new AprovacaoDto(
                aprovacao.relatorioId(),
                "APROVADO",
                aprovacao.versaoAprovada(),
                aprovacao.aprovadoEm());
    }
}
