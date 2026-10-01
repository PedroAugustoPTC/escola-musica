package br.com.felipeblaft.relatorios.configuracao;

import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * As 10 seções do semestral com os rótulos do catálogo (FR-003): a mesma estrutura alimenta o PDF e
 * o texto do bot. A seção 1 (Informações Gerais) vem do sistema; as seções 2 a 10, da IA.
 */
@Component
public class SecoesRelatorioSemestral {

    /** Campo de uma seção: lista de itens ou texto (vazio quando não há informação). */
    public static final class Campo {
        private final String rotulo;
        private final List<String> itens;
        private final String texto;

        Campo(String rotulo, List<String> itens, String texto) {
            this.rotulo = rotulo;
            this.itens = itens == null ? List.of() : itens;
            this.texto = texto == null ? "" : texto;
        }

        public String getRotulo() {
            return rotulo;
        }

        public List<String> getItens() {
            return itens;
        }

        public String getTexto() {
            return texto;
        }

        public boolean vazio() {
            return itens.isEmpty() && texto.isBlank();
        }
    }

    public static final class Secao {
        private final int numero;
        private final String titulo;
        private final List<Campo> campos;

        Secao(int numero, String titulo, List<Campo> campos) {
            this.numero = numero;
            this.titulo = titulo;
            this.campos = campos;
        }

        public int getNumero() {
            return numero;
        }

        public String getTitulo() {
            return titulo;
        }

        public List<Campo> getCampos() {
            return campos;
        }
    }

    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens formatacao;

    public SecoesRelatorioSemestral(CatalogoMensagens catalogo, FormatacaoMensagens formatacao) {
        this.catalogo = catalogo;
        this.formatacao = formatacao;
    }

    public String rotuloPeriodo(String periodoChave) {
        PeriodoSemestral periodo = PeriodoSemestral.de(periodoChave);
        return catalogo.texto(
                "SEMESTRE_ROTULO", Map.of("numero", periodo.semestre(), "ano", periodo.ano()));
    }

    /** Seção 1 (sistema) seguida das seções 2 a 10 (IA). */
    public List<Secao> secoes(InformacoesGerais info, ConteudoRelatorioSemestral conteudo) {
        List<Secao> secoes = new ArrayList<>();
        secoes.add(
                new Secao(
                        1,
                        catalogo.texto("SECAO_INFORMACOES_GERAIS"),
                        List.of(
                                texto("INFO_NOME", info.nome()),
                                texto(
                                        "INFO_IDADE",
                                        catalogo.texto(
                                                "INFO_IDADE_VALOR", Map.of("idade", info.idade()))),
                                texto("INFO_INSTRUMENTO", info.instrumento()),
                                texto("INFO_PROFESSOR", info.professor()),
                                texto(
                                        "INFO_PERIODO",
                                        rotuloPeriodo(info.periodoChave())
                                                + " ("
                                                + formatacao.data(info.periodoInicio())
                                                + " a "
                                                + formatacao.data(info.periodoFim())
                                                + ")"),
                                texto(
                                        "INFO_AULAS_CONSIDERADAS",
                                        Integer.toString(info.quantidadeAulasConsideradas())))));
        secoes.addAll(secoesDoConteudo(conteudo));
        return secoes;
    }

    /** Seções 2 a 10. */
    public List<Secao> secoesDoConteudo(ConteudoRelatorioSemestral c) {
        var f = c.frequenciaEEstudo();
        var t = c.tecnica();
        var m = c.musicalidade();
        var l = c.leituraEMemorizacao();
        var a = c.acompanhamentoFamiliar();
        var p = c.planejamentoProximoSemestre();
        return List.of(
                new Secao(
                        2,
                        catalogo.texto("SECAO_FREQUENCIA_E_ESTUDO"),
                        List.of(
                                texto("CAMPO_FREQUENCIA_AULAS", f.frequenciaAulas()),
                                texto(
                                        "CAMPO_REGULARIDADE_ESTUDO_EM_CASA",
                                        f.regularidadeEstudoEmCasa()),
                                texto("CAMPO_ENGAJAMENTO", f.engajamento()),
                                texto("CAMPO_COMENTARIO_GERAL", f.comentarioGeral()))),
                new Secao(
                        3,
                        catalogo.texto("SECAO_TECNICA"),
                        List.of(
                                lista("CAMPO_CONTEUDOS_TRABALHADOS", t.conteudosTrabalhados()),
                                texto("CAMPO_EVOLUCAO_TECNICA", t.evolucaoTecnica()),
                                lista("CAMPO_PONTOS_A_DESENVOLVER", t.pontosADesenvolver()),
                                lista("CAMPO_ESTUDOS_REALIZADOS", t.estudosRealizados()))),
                new Secao(
                        4,
                        catalogo.texto("SECAO_MUSICALIDADE"),
                        List.of(
                                lista("CAMPO_REPERTORIO_ESTUDADO", m.repertorioEstudado()),
                                texto("CAMPO_SOM", m.som()),
                                texto("CAMPO_RITMO", m.ritmo()),
                                texto("CAMPO_FRASEADO", m.fraseado()),
                                texto("CAMPO_ARTICULACAO", m.articulacao()),
                                texto("CAMPO_ESTILO", m.estilo()),
                                texto("CAMPO_COMENTARIO_MUSICAL", m.comentarioMusical()))),
                new Secao(
                        5,
                        catalogo.texto("SECAO_LEITURA_E_MEMORIZACAO"),
                        List.of(
                                texto("CAMPO_AVALIACAO", l.avaliacao()),
                                texto("CAMPO_COMENTARIO", l.comentario()))),
                new Secao(
                        6,
                        catalogo.texto("SECAO_PONTOS_DE_ATENCAO"),
                        List.of(new Campo(null, c.pontosDeAtencao().itens(), ""))),
                new Secao(
                        7,
                        catalogo.texto("SECAO_ESTRATEGIAS_PEDAGOGICAS"),
                        List.of(new Campo(null, c.estrategiasPedagogicas().itens(), ""))),
                new Secao(
                        8,
                        catalogo.texto("SECAO_ACOMPANHAMENTO_FAMILIAR"),
                        List.of(
                                texto("CAMPO_NIVEL_DE_PRESENCA", a.nivelDePresenca()),
                                texto("CAMPO_OBSERVACAO_DO_PROFESSOR", a.observacaoDoProfessor()))),
                new Secao(
                        9,
                        catalogo.texto("SECAO_PLANEJAMENTO_PROXIMO_SEMESTRE"),
                        List.of(
                                lista("CAMPO_METAS_TECNICAS", p.metasTecnicas()),
                                lista("CAMPO_METAS_MUSICAIS", p.metasMusicais()),
                                lista("CAMPO_HABITOS_DE_ESTUDO", p.habitosDeEstudo()),
                                lista("CAMPO_REPERTORIO_PROPOSTO", p.repertorioProposto()),
                                lista("CAMPO_ESTUDOS_TECNICOS", p.estudosTecnicos()),
                                lista("CAMPO_LEITURA_E_TEORIA", p.leituraETeoria()),
                                texto("CAMPO_PROJETO_DO_SEMESTRE", p.projetoDoSemestre()))),
                new Secao(
                        10,
                        catalogo.texto("SECAO_PARECER_FINAL"),
                        List.of(new Campo(null, List.of(), c.parecerFinal()))));
    }

    private Campo texto(String rotulo, String valor) {
        return new Campo(catalogo.texto(rotulo), List.of(), valor);
    }

    private Campo lista(String rotulo, List<String> itens) {
        return new Campo(catalogo.texto(rotulo), itens, "");
    }
}
