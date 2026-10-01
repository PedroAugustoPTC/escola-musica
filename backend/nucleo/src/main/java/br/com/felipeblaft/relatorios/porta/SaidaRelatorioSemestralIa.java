package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.AcompanhamentoFamiliar;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.FrequenciaEEstudo;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.Itens;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.LeituraEMemorizacao;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.Musicalidade;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.PlanejamentoProximoSemestre;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral.Tecnica;

/**
 * Saída da IA para o semestral (contracts/portas-provedores.md §6.2). Não há Informações Gerais: se
 * a IA mandar essa chave, ela é descartada na leitura (FR-044).
 */
public record SaidaRelatorioSemestralIa(
        FrequenciaEEstudo frequenciaEEstudo,
        Tecnica tecnica,
        Musicalidade musicalidade,
        LeituraEMemorizacao leituraEMemorizacao,
        Itens pontosDeAtencao,
        Itens estrategiasPedagogicas,
        AcompanhamentoFamiliar acompanhamentoFamiliar,
        PlanejamentoProximoSemestre planejamentoProximoSemestre,
        String parecerFinal,
        Boolean semAlteracaoDeConteudo,
        String pedidoDeAcao) {

    public ConteudoRelatorioSemestral conteudo() {
        return new ConteudoRelatorioSemestral(
                frequenciaEEstudo,
                tecnica,
                musicalidade,
                leituraEMemorizacao,
                pontosDeAtencao,
                estrategiasPedagogicas,
                acompanhamentoFamiliar,
                planejamentoProximoSemestre,
                parecerFinal);
    }

    public static SaidaRelatorioSemestralIa de(
            ConteudoRelatorioSemestral conteudo, boolean semAlteracao, String pedidoDeAcao) {
        return new SaidaRelatorioSemestralIa(
                conteudo.frequenciaEEstudo(),
                conteudo.tecnica(),
                conteudo.musicalidade(),
                conteudo.leituraEMemorizacao(),
                conteudo.pontosDeAtencao(),
                conteudo.estrategiasPedagogicas(),
                conteudo.acompanhamentoFamiliar(),
                conteudo.planejamentoProximoSemestre(),
                conteudo.parecerFinal(),
                semAlteracao,
                pedidoDeAcao);
    }

    public boolean semAlteracao() {
        return Boolean.TRUE.equals(semAlteracaoDeConteudo);
    }

    public boolean pedidoDeAcaoIndicado() {
        return pedidoDeAcao != null && !SaidaRelatorioAulaIa.NENHUM.equals(pedidoDeAcao);
    }
}
