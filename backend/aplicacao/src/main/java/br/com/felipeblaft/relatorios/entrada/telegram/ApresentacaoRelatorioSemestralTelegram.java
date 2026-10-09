package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso.VisaoRelatorioSemestral;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral.Campo;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral.Secao;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.RelatorioFoco;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Documento;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apresenta o semestral no chat (sequência 5.4): conferência com "N relatórios de aula foram
 * considerados", o aviso de relatórios em aberto, as 10 seções em texto, o PDF e as mesmas ações da
 * conferência de aula. Põe o semestral em foco com a versão apresentada.
 */
@Component
public class ApresentacaoRelatorioSemestralTelegram {

    private final ObterRelatorioSemestralCasoDeUso obterRelatorio;
    private final ObterDocumentoRelatorioCasoDeUso obterDocumento;
    private final ApresentadorTelegram apresentador;
    private final ApresentacaoRelatorioAulaTelegram apresentacaoAula;
    private final SecoesRelatorioSemestral secoes;

    public ApresentacaoRelatorioSemestralTelegram(
            ObterRelatorioSemestralCasoDeUso obterRelatorio,
            ObterDocumentoRelatorioCasoDeUso obterDocumento,
            ApresentadorTelegram apresentador,
            ApresentacaoRelatorioAulaTelegram apresentacaoAula,
            SecoesRelatorioSemestral secoes) {
        this.obterRelatorio = obterRelatorio;
        this.obterDocumento = obterDocumento;
        this.apresentador = apresentador;
        this.apresentacaoAula = apresentacaoAula;
        this.secoes = secoes;
    }

    public VisaoRelatorioSemestral obter(UUID professorId, UUID relatorioId) {
        return obterRelatorio.executar(professorId, relatorioId);
    }

    public static boolean emProcessamento(VisaoRelatorioSemestral visao) {
        return visao.trabalhoEmCurso().isPresent() || visao.relatorio().etapa() != null;
    }

    public String processando(VisaoRelatorioSemestral visao) {
        String etapa =
                visao.trabalhoEmCurso()
                        .map(t -> t.etapa().name())
                        .or(() -> Optional.ofNullable(visao.relatorio().etapa()).map(Enum::name))
                        .orElse("APLICANDO_ALTERACAO");
        return apresentador.texto(
                "PROCESSAMENTO_EM_ANDAMENTO",
                Map.of("etapa", apresentador.formatacao().etapa(etapa)));
    }

    public List<SaidaTelegram> apresentar(
            long chatId,
            UUID professorId,
            UUID relatorioId,
            ConversaTelegram conversa,
            String prefixo) {
        VisaoRelatorioSemestral visao = obter(professorId, relatorioId);
        RelatorioSemestral relatorio = visao.relatorio();
        if (!relatorio.status().emAberto()) {
            conversa.limparFoco();
            String codigo =
                    relatorio.status() == StatusRelatorio.APROVADO
                            ? "RELATORIO_JA_APROVADO"
                            : "RELATORIO_CANCELADO_ERRO";
            return List.of(
                    new Mensagem(
                            chatId,
                            ApresentacaoRelatorioAulaTelegram.comPrefixo(
                                    prefixo, apresentador.texto(codigo)),
                            apresentador.acoesInicio()));
        }
        conversa.focar(
                new RelatorioFoco(TipoRelatorio.SEMESTRAL, relatorio.id()),
                relatorio.versaoVigente());
        if (emProcessamento(visao)) {
            return List.of(
                    new Mensagem(
                            chatId,
                            ApresentacaoRelatorioAulaTelegram.comPrefixo(
                                    prefixo, processando(visao)),
                            Teclado.NENHUM));
        }
        List<SaidaTelegram> saidas = new ArrayList<>();
        saidas.add(
                new Mensagem(
                        chatId,
                        ApresentacaoRelatorioAulaTelegram.comPrefixo(
                                prefixo, textoConferencia(relatorio)),
                        Teclado.NENHUM));
        documentoConferencia(chatId, professorId, relatorio)
                .ifPresentOrElse(
                        saidas::add,
                        () -> saidas.add(apresentador.simples(chatId, "CONFERENCIA_PDF_GERANDO")));
        saidas.add(apresentacaoAula.acoesConferencia(chatId, relatorio.versaoVigente(), null));
        return saidas;
    }

    public Optional<SaidaTelegram> documentoConferencia(
            long chatId, UUID professorId, RelatorioSemestral relatorio) {
        try {
            ObterDocumentoRelatorioCasoDeUso.DocumentoObtido documento =
                    obterDocumento.conferencia(
                            professorId,
                            TipoRelatorio.SEMESTRAL,
                            relatorio.id(),
                            relatorio.versaoVigente());
            return Optional.of(
                    new Documento(
                            chatId,
                            documento.conteudo(),
                            documento.nomeArquivo(),
                            null,
                            ReferenciaDocumento.conferencia(
                                    professorId,
                                    TipoRelatorio.SEMESTRAL,
                                    relatorio.id(),
                                    relatorio.versaoVigente()),
                            apresentador.avisoPdfIndisponivel(chatId, "pdf", null)));
        } catch (ExcecaoDominio semDocumento) {
            return Optional.empty();
        }
    }

    public String textoConferencia(RelatorioSemestral relatorio) {
        StringBuilder texto = new StringBuilder();
        texto.append("<b>").append(apresentador.texto("CONFERENCIA_TITULO")).append("</b>\n");
        texto.append(apresentador.texto("CABECALHO_ALUNO"))
                .append(": ")
                .append(ApresentadorTelegram.escapar(relatorio.informacoesGerais().nome()))
                .append('\n');
        texto.append(apresentador.texto("CABECALHO_PERIODO"))
                .append(": ")
                .append(
                        ApresentadorTelegram.escapar(
                                secoes.rotuloPeriodo(relatorio.periodo().chave())))
                .append('\n');
        texto.append(
                apresentador.texto(
                        "CONFERENCIA_VERSAO", Map.of("versao", relatorio.versaoVigente())));
        texto.append("\n\n")
                .append(
                        apresentador.texto(
                                "AULAS_CONSIDERADAS",
                                Map.of("quantidade", relatorio.quantidadeAulasConsideradas())));
        if (relatorio.quantidadeRelatoriosEmAberto() > 0) {
            texto.append('\n')
                    .append(
                            apresentador.texto(
                                    "HA_RELATORIOS_EM_ABERTO",
                                    Map.of(
                                            "quantidade",
                                            relatorio.quantidadeRelatoriosEmAberto())));
        }
        for (Secao secao : secoes.secoes(relatorio.informacoesGerais(), relatorio.conteudo())) {
            texto.append("\n\n<b>")
                    .append(secao.getNumero())
                    .append(". ")
                    .append(ApresentadorTelegram.escapar(secao.getTitulo()))
                    .append("</b>");
            for (Campo campo : secao.getCampos()) {
                texto.append('\n');
                if (campo.getRotulo() != null) {
                    texto.append("<i>")
                            .append(ApresentadorTelegram.escapar(campo.getRotulo()))
                            .append("</i>: ");
                }
                if (campo.vazio()) {
                    texto.append(apresentador.texto("SECAO_VAZIA"));
                } else if (campo.getItens().isEmpty()) {
                    texto.append(ApresentadorTelegram.escapar(campo.getTexto()));
                } else {
                    for (String item : campo.getItens()) {
                        texto.append("\n• ").append(ApresentadorTelegram.escapar(item));
                    }
                }
            }
        }
        return texto.toString();
    }
}
