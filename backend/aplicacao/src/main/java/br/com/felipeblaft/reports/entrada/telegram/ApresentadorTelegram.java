package br.com.felipeblaft.reports.entrada.telegram;

import br.com.felipeblaft.reports.configuracao.CatalogoMensagens;
import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.reports.entrada.telegram.SaidaTelegram.Teclado;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Monta as mensagens do bot a partir do catálogo único (FR-003): mesmos textos da Web, adaptados ao
 * chat (HTML escapado, listas e botões). Não decide nada; só apresenta.
 */
@Component
public class ApresentadorTelegram {

    public static final int LIMITE_TEXTO = 4096;

    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens formatacao;

    public ApresentadorTelegram(CatalogoMensagens catalogo, FormatacaoMensagens formatacao) {
        this.catalogo = catalogo;
        this.formatacao = formatacao;
    }

    public static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Texto do catálogo, escapado para HTML. */
    public String texto(String codigo, Map<String, ?> parametros) {
        return escapar(catalogo.texto(codigo, parametros));
    }

    public String texto(String codigo) {
        return texto(codigo, Map.of());
    }

    public String rotulo(String codigo, Map<String, ?> parametros) {
        return catalogo.texto(codigo, parametros);
    }

    public String rotulo(String codigo) {
        return catalogo.texto(codigo);
    }

    public FormatacaoMensagens formatacao() {
        return formatacao;
    }

    public Mensagem simples(long chatId, String codigo, Map<String, ?> parametros) {
        return new Mensagem(chatId, texto(codigo, parametros), Teclado.NENHUM);
    }

    public Mensagem simples(long chatId, String codigo) {
        return simples(chatId, codigo, Map.of());
    }

    public Mensagem erro(long chatId, ErrorCode codigo, Map<String, Object> detalhes) {
        return new Mensagem(
                chatId,
                escapar(
                        catalogo.texto(
                                codigo.messageKey(), formatacao.parametrosErro(codigo, detalhes))),
                Teclado.NENHUM);
    }

    public Botao botao(String codigoRotulo, String acao) {
        return new Botao(rotulo(codigoRotulo), DadosCallbackCodificador.codificar(acao));
    }

    public Botao botao(
            String codigoRotulo, Map<String, ?> parametros, String acao, String argumento) {
        return new Botao(
                rotulo(codigoRotulo, parametros),
                DadosCallbackCodificador.codificar(acao, argumento));
    }

    /**
     * Aviso de que o PDF não pôde ser enviado, com o botão que o pede de novo: {@code acao} e
     * {@code argumento} são os da ação que gerou o PDF (FR-007c).
     */
    public Mensagem avisoPdfIndisponivel(long chatId, String acao, String argumento) {
        Botao pedirDeNovo =
                argumento == null
                        ? botao("BOTAO_VER_PDF", acao)
                        : botao("BOTAO_VER_PDF", Map.of(), acao, argumento);
        return new Mensagem(chatId, texto("PDF_INDISPONIVEL_AVISO"), Teclado.de(pedirDeNovo));
    }

    public Teclado acoesInicio() {
        return new Teclado(
                List.of(
                        List.of(botao("BOTAO_REGISTRAR_AULA", "reg")),
                        List.of(botao("BOTAO_RELATORIO_SEMESTRAL", "sem")),
                        List.of(botao("BOTAO_HISTORICO", "hist"))));
    }

    /** Bloco de início (FR-012): saudação, relatórios em aberto, total e até 5 alunos, ações. */
    public Mensagem inicio(long chatId, GetTeacherHomeUseCase.Home inicio, String prefixo) {
        StringBuilder texto = new StringBuilder();
        if (prefixo != null) {
            texto.append(prefixo).append("\n\n");
        }
        texto.append("<b>")
                .append(texto("SAUDACAO", Map.of("nome", inicio.teacher().firstName())))
                .append("</b>\n")
                .append(texto("INICIO_SUBTITULO"));
        if (inicio.withoutStudents()) {
            texto.append("\n\n").append(texto("SEM_ALUNOS"));
            return new Mensagem(chatId, texto.toString(), Teclado.NENHUM);
        }
        texto.append("\n\n<b>").append(texto("INICIO_RELATORIOS_EM_ABERTO")).append("</b>\n");
        if (inicio.openReports().isEmpty()) {
            texto.append(texto("INICIO_SEM_RELATORIOS_EM_ABERTO"));
        } else {
            for (OpenReport aberto : inicio.openReports()) {
                texto.append("• ").append(escapar(itemEmAberto(aberto))).append('\n');
            }
        }
        texto.append("\n\n<b>")
                .append(texto("INICIO_TOTAL_ALUNOS", Map.of("total", inicio.totalStudents())))
                .append("</b>");
        for (StudentSummary aluno : inicio.students()) {
            texto.append("\n• ").append(escapar(linhaAluno(aluno)));
        }
        List<List<Botao>> linhas = new ArrayList<>();
        for (OpenReport aberto : inicio.openReports()) {
            if (aberto.type() == ReportType.AULA || aberto.reportId() != null) {
                String argumento =
                        aberto.reportId() != null
                                ? (aberto.type() == ReportType.AULA ? "a" : "s") + aberto.reportId()
                                : "t" + aberto.jobId();
                linhas.add(
                        List.of(
                                botao(
                                        "BOTAO_CONTINUAR_RELATORIO",
                                        Map.of(
                                                "aluno",
                                                aberto.studentName(),
                                                "data",
                                                formatacao.data(aberto.updatedAt())),
                                        "foco",
                                        argumento)));
            }
        }
        linhas.addAll(acoesInicio().linhas());
        return new Mensagem(chatId, texto.toString(), new Teclado(linhas));
    }

    public String itemEmAberto(OpenReport aberto) {
        String situacao =
                aberto.status() != null
                        ? rotulo("STATUS_" + aberto.status().name())
                        : rotulo("STATUS_EM_PROCESSAMENTO");
        return rotulo(
                "ITEM_EM_ABERTO",
                Map.of(
                        "aluno", aberto.studentName(),
                        "tipo", rotulo("TIPO_" + aberto.type().name()),
                        "situacao", situacao,
                        "data", formatacao.data(aberto.updatedAt()),
                        "hora", formatacao.hora(aberto.updatedAt())));
    }

    public String linhaAluno(StudentSummary aluno) {
        StringBuilder linha = new StringBuilder(aluno.name());
        if (aluno.instrument() != null) {
            linha.append(" · ").append(aluno.instrument());
        }
        if (aluno.hasOpenReport()) {
            linha.append(" · ").append(rotulo("MARCA_EM_ABERTO"));
        }
        return linha.toString();
    }

    /** Lista de alunos como botões {@code aluno:<índice>} (o índice fica guardado na conversa). */
    public Mensagem listaAlunos(long chatId, String cabecalho, List<StudentSummary> alunos) {
        List<List<Botao>> linhas = new ArrayList<>();
        for (int i = 0; i < alunos.size(); i++) {
            StudentSummary aluno = alunos.get(i);
            String rotulo =
                    aluno.hasOpenReport()
                            ? aluno.name() + " · " + rotulo("MARCA_EM_ABERTO")
                            : aluno.name();
            linhas.add(
                    List.of(
                            new Botao(
                                    rotulo,
                                    DadosCallbackCodificador.codificar(
                                            "aluno", Integer.toString(i)))));
        }
        linhas.add(List.of(botao("BOTAO_INICIO", "inicio")));
        return new Mensagem(chatId, cabecalho, new Teclado(linhas));
    }

    /**
     * Aviso "Você tem um relatório em aberto" (FR-034) com o canal de origem e a última alteração,
     * e os botões "Continuar de onde parei" / "Descartar e começar um novo".
     */
    public Mensagem avisoEmAberto(long chatId, OpenReport aberto) {
        return new Mensagem(
                chatId,
                erro(
                                chatId,
                                ErrorCode.RELATORIO_EM_ABERTO,
                                Map.of("relatorioEmAberto", aberto.details()))
                        .texto(),
                Teclado.de(botao("BOTAO_CONTINUAR", "cont"), botao("BOTAO_DESCARTAR", "desc")));
    }

    public Mensagem confirmacao(long chatId, String texto, Botao confirmar) {
        return new Mensagem(
                chatId,
                texto,
                new Teclado(List.of(List.of(confirmar), List.of(botao("BOTAO_VOLTAR", "inicio")))));
    }

    /**
     * Divide textos longos em partes de até 4096 caracteres, quebrando em linhas quando possível.
     */
    public static List<String> dividir(String texto) {
        List<String> partes = new ArrayList<>();
        String restante = texto;
        while (restante.length() > LIMITE_TEXTO) {
            int corte = restante.lastIndexOf('\n', LIMITE_TEXTO);
            if (corte <= 0) {
                corte = LIMITE_TEXTO;
            }
            partes.add(restante.substring(0, corte));
            restante = restante.substring(corte).stripLeading();
        }
        partes.add(restante);
        return partes;
    }
}
