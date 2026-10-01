package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso.VisaoRelatorioAula;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.RelatorioFoco;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
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
 * Apresenta a etapa atual de um relatório de aula no chat (sequência 5.2): processamento,
 * perguntas, conferência com o PDF, falha ou situação final. Ao apresentar perguntas ou
 * conferência, põe o relatório em foco e grava a versão apresentada na conversa (FR-027b, FR-033).
 */
@Component
public class ApresentacaoRelatorioAulaTelegram {

    private final ObterRelatorioAulaCasoDeUso obterRelatorio;
    private final ObterDocumentoRelatorioCasoDeUso obterDocumento;
    private final ApresentadorTelegram apresentador;

    public ApresentacaoRelatorioAulaTelegram(
            ObterRelatorioAulaCasoDeUso obterRelatorio,
            ObterDocumentoRelatorioCasoDeUso obterDocumento,
            ApresentadorTelegram apresentador) {
        this.obterRelatorio = obterRelatorio;
        this.obterDocumento = obterDocumento;
        this.apresentador = apresentador;
    }

    public VisaoRelatorioAula obter(UUID professorId, UUID relatorioId) {
        return obterRelatorio.executar(professorId, relatorioId);
    }

    /** Apresenta a etapa atual; {@code prefixo} (já escapado) vem antes, se houver. */
    public List<SaidaTelegram> apresentar(
            long chatId,
            UUID professorId,
            UUID relatorioId,
            ConversaTelegram conversa,
            String prefixo) {
        return apresentar(chatId, professorId, obter(professorId, relatorioId), conversa, prefixo);
    }

    public List<SaidaTelegram> apresentar(
            long chatId,
            UUID professorId,
            VisaoRelatorioAula visao,
            ConversaTelegram conversa,
            String prefixo) {
        RelatorioAula relatorio = visao.relatorio();
        if (relatorio.status() == StatusRelatorio.APROVADO
                || relatorio.status() == StatusRelatorio.CANCELADO) {
            conversa.limparFoco();
            String codigo =
                    relatorio.status() == StatusRelatorio.APROVADO
                            ? "RELATORIO_JA_APROVADO"
                            : "RELATORIO_CANCELADO_ERRO";
            return List.of(
                    new Mensagem(
                            chatId,
                            comPrefixo(prefixo, apresentador.texto(codigo)),
                            apresentador.acoesInicio()));
        }
        conversa.focar(
                new RelatorioFoco(TipoRelatorio.AULA, relatorio.id()), relatorio.versaoVigente());
        if (emProcessamento(visao)) {
            return List.of(
                    new Mensagem(chatId, comPrefixo(prefixo, processando(visao)), Teclado.NENHUM));
        }
        return switch (relatorio.status()) {
            case AGUARDANDO_RESPOSTAS -> List.of(perguntas(chatId, relatorio, prefixo));
            case AGUARDANDO_CONFERENCIA -> conferencia(chatId, professorId, visao, prefixo);
            default -> List.of(falha(chatId, prefixo));
        };
    }

    public static boolean emProcessamento(VisaoRelatorioAula visao) {
        return visao.trabalhoEmCurso().isPresent()
                || visao.relatorio().etapa() != null
                || visao.relatorio().status() == StatusRelatorio.EM_PROCESSAMENTO;
    }

    public String processando(VisaoRelatorioAula visao) {
        String etapa =
                visao.trabalhoEmCurso()
                        .map(t -> t.etapa().name())
                        .or(() -> Optional.ofNullable(visao.relatorio().etapa()).map(Enum::name))
                        .orElse("ESTRUTURANDO");
        return apresentador.texto(
                "PROCESSAMENTO_EM_ANDAMENTO",
                Map.of("etapa", apresentador.formatacao().etapa(etapa)));
    }

    public Mensagem perguntas(long chatId, RelatorioAula relatorio, String prefixo) {
        StringBuilder numeradas = new StringBuilder();
        List<String> perguntas = relatorio.perguntasPendentes();
        for (int i = 0; i < perguntas.size(); i++) {
            numeradas.append('\n').append(i + 1).append(". ").append(perguntas.get(i));
        }
        String texto =
                apresentador.texto("PERGUNTAS_PENDENTES", Map.of("perguntasNumeradas", numeradas))
                        + "\n\n<i>"
                        + apresentador.texto("LEMBRETE_FOCO")
                        + "</i>";
        return new Mensagem(
                chatId,
                comPrefixo(prefixo, texto),
                Teclado.de(
                        apresentador.botao("BOTAO_CANCELAR_RELATORIO", "canc"),
                        apresentador.botao("BOTAO_REGISTRAR_AULA", "reg")));
    }

    /** Cabeçalho e seções em texto, o PDF como documento e as ações com o lembrete de foco. */
    public List<SaidaTelegram> conferencia(
            long chatId, UUID professorId, VisaoRelatorioAula visao, String prefixo) {
        RelatorioAula relatorio = visao.relatorio();
        List<SaidaTelegram> saidas = new ArrayList<>();
        saidas.add(
                new Mensagem(chatId, comPrefixo(prefixo, textoConferencia(visao)), Teclado.NENHUM));
        documentoConferencia(chatId, professorId, relatorio)
                .ifPresentOrElse(
                        saidas::add,
                        () -> saidas.add(apresentador.simples(chatId, "CONFERENCIA_PDF_GERANDO")));
        saidas.add(acoesConferencia(chatId, relatorio.versaoVigente(), null));
        return saidas;
    }

    public Optional<SaidaTelegram> documentoConferencia(
            long chatId, UUID professorId, RelatorioAula relatorio) {
        try {
            ObterDocumentoRelatorioCasoDeUso.DocumentoObtido documento =
                    obterDocumento.conferencia(
                            professorId,
                            TipoRelatorio.AULA,
                            relatorio.id(),
                            relatorio.versaoVigente());
            return Optional.of(
                    new Documento(chatId, documento.conteudo(), documento.nomeArquivo(), null));
        } catch (ExcecaoDominio semDocumento) {
            return Optional.empty();
        }
    }

    /** Lembrete de foco com os botões da conferência ({@code aprov} cita a versão apresentada). */
    public Mensagem acoesConferencia(long chatId, int versao, String prefixo) {
        String texto = "<i>" + apresentador.texto("LEMBRETE_FOCO") + "</i>";
        return new Mensagem(
                chatId,
                comPrefixo(prefixo, texto),
                new Teclado(
                        List.of(
                                List.of(
                                        new Botao(
                                                apresentador.rotulo("BOTAO_APROVAR"),
                                                DadosCallbackCodificador.codificar(
                                                        "aprov", Integer.toString(versao)))),
                                List.of(apresentador.botao("BOTAO_SOLICITAR_ALTERACAO", "alt")),
                                List.of(
                                        apresentador.botao("BOTAO_VER_PDF", "pdf"),
                                        apresentador.botao("BOTAO_CANCELAR_RELATORIO", "canc")),
                                List.of(apresentador.botao("BOTAO_REGISTRAR_AULA", "reg")))));
    }

    public String textoConferencia(VisaoRelatorioAula visao) {
        RelatorioAula relatorio = visao.relatorio();
        ConteudoRelatorioAula conteudo = relatorio.conteudo();
        StringBuilder texto = new StringBuilder();
        texto.append("<b>").append(apresentador.texto("CONFERENCIA_TITULO")).append("</b>\n");
        linha(texto, "CABECALHO_ALUNO", visao.alunoNome());
        linha(texto, "CABECALHO_PROFESSOR", visao.professorNome());
        linha(
                texto,
                "CABECALHO_DATA_AULA",
                apresentador.formatacao().data(visao.aula().dataAula()));
        texto.append(
                apresentador.texto(
                        "CONFERENCIA_VERSAO", Map.of("versao", relatorio.versaoVigente())));
        lista(texto, "SECAO_CONTEUDOS_TRABALHADOS", conteudo.conteudosTrabalhados());
        paragrafo(texto, "SECAO_EVOLUCAO", conteudo.evolucao());
        lista(texto, "SECAO_DIFICULDADES", conteudo.dificuldades());
        lista(texto, "SECAO_ATIVIDADES_PROPOSTAS", conteudo.atividadesPropostas());
        paragrafo(texto, "SECAO_OBSERVACOES", conteudo.observacoes());
        return texto.toString();
    }

    public Mensagem falha(long chatId, String prefixo) {
        return new Mensagem(
                chatId,
                comPrefixo(prefixo, apresentador.texto("FALHA_PROCESSAMENTO_OPCOES")),
                Teclado.de(
                        apresentador.botao("BOTAO_TENTAR_NOVAMENTE", "tent"),
                        apresentador.botao("BOTAO_CANCELAR_RELATORIO", "canc")));
    }

    private void linha(StringBuilder texto, String rotulo, String valor) {
        texto.append(apresentador.texto(rotulo))
                .append(": ")
                .append(ApresentadorTelegram.escapar(valor))
                .append('\n');
    }

    private void lista(StringBuilder texto, String secao, List<String> itens) {
        texto.append("\n\n<b>").append(apresentador.texto(secao)).append("</b>");
        if (itens.isEmpty()) {
            texto.append('\n').append(apresentador.texto("SECAO_VAZIA"));
        }
        for (String item : itens) {
            texto.append("\n• ").append(ApresentadorTelegram.escapar(item));
        }
    }

    private void paragrafo(StringBuilder texto, String secao, String valor) {
        texto.append("\n\n<b>")
                .append(apresentador.texto(secao))
                .append("</b>\n")
                .append(
                        valor == null || valor.isBlank()
                                ? apresentador.texto("SECAO_VAZIA")
                                : ApresentadorTelegram.escapar(valor));
    }

    static String comPrefixo(String prefixo, String texto) {
        return prefixo == null || prefixo.isBlank() ? texto : prefixo + "\n\n" + texto;
    }
}
