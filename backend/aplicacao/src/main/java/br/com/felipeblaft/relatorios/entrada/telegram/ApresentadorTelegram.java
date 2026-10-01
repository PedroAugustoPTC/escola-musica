package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.casodeuso.inicio.AlunoResumo;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterInicioProfessorCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Botao;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Mensagem;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram.Teclado;
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

    public Mensagem erro(long chatId, CodigoErro codigo, Map<String, Object> detalhes) {
        return new Mensagem(
                chatId,
                escapar(
                        catalogo.texto(
                                codigo.chaveMensagem(),
                                formatacao.parametrosErro(codigo, detalhes))),
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

    public Teclado acoesInicio() {
        return new Teclado(
                List.of(
                        List.of(botao("BOTAO_REGISTRAR_AULA", "reg")),
                        List.of(botao("BOTAO_RELATORIO_SEMESTRAL", "sem")),
                        List.of(botao("BOTAO_HISTORICO", "hist"))));
    }

    /** Bloco de início (FR-012): saudação, relatórios em aberto, total e até 5 alunos, ações. */
    public Mensagem inicio(
            long chatId, ObterInicioProfessorCasoDeUso.Inicio inicio, String prefixo) {
        StringBuilder texto = new StringBuilder();
        if (prefixo != null) {
            texto.append(prefixo).append("\n\n");
        }
        texto.append("<b>")
                .append(texto("SAUDACAO", Map.of("nome", inicio.professor().primeiroNome())))
                .append("</b>\n")
                .append(texto("INICIO_SUBTITULO"));
        if (inicio.semAlunos()) {
            texto.append("\n\n").append(texto("SEM_ALUNOS"));
            return new Mensagem(chatId, texto.toString(), Teclado.NENHUM);
        }
        texto.append("\n\n<b>").append(texto("INICIO_RELATORIOS_EM_ABERTO")).append("</b>\n");
        if (inicio.relatoriosEmAberto().isEmpty()) {
            texto.append(texto("INICIO_SEM_RELATORIOS_EM_ABERTO"));
        } else {
            for (RelatorioEmAberto aberto : inicio.relatoriosEmAberto()) {
                texto.append("• ").append(escapar(itemEmAberto(aberto))).append('\n');
            }
        }
        texto.append("\n\n<b>")
                .append(texto("INICIO_TOTAL_ALUNOS", Map.of("total", inicio.totalAlunos())))
                .append("</b>");
        for (AlunoResumo aluno : inicio.alunos()) {
            texto.append("\n• ").append(escapar(linhaAluno(aluno)));
        }
        List<List<Botao>> linhas = new ArrayList<>();
        for (RelatorioEmAberto aberto : inicio.relatoriosEmAberto()) {
            if (aberto.tipo() == TipoRelatorio.AULA || aberto.relatorioId() != null) {
                String argumento =
                        aberto.relatorioId() != null
                                ? (aberto.tipo() == TipoRelatorio.AULA ? "a" : "s")
                                        + aberto.relatorioId()
                                : "t" + aberto.trabalhoId();
                linhas.add(
                        List.of(
                                botao(
                                        "BOTAO_CONTINUAR_RELATORIO",
                                        Map.of(
                                                "aluno",
                                                aberto.alunoNome(),
                                                "data",
                                                formatacao.data(aberto.atualizadoEm())),
                                        "foco",
                                        argumento)));
            }
        }
        linhas.addAll(acoesInicio().linhas());
        return new Mensagem(chatId, texto.toString(), new Teclado(linhas));
    }

    public String itemEmAberto(RelatorioEmAberto aberto) {
        String situacao =
                aberto.status() != null
                        ? rotulo("STATUS_" + aberto.status().name())
                        : rotulo("STATUS_EM_PROCESSAMENTO");
        return rotulo(
                "ITEM_EM_ABERTO",
                Map.of(
                        "aluno", aberto.alunoNome(),
                        "tipo", rotulo("TIPO_" + aberto.tipo().name()),
                        "situacao", situacao,
                        "data", formatacao.data(aberto.atualizadoEm()),
                        "hora", formatacao.hora(aberto.atualizadoEm())));
    }

    public String linhaAluno(AlunoResumo aluno) {
        StringBuilder linha = new StringBuilder(aluno.nome());
        if (aluno.instrumento() != null) {
            linha.append(" · ").append(aluno.instrumento());
        }
        if (aluno.possuiRelatorioEmAberto()) {
            linha.append(" · ").append(rotulo("MARCA_EM_ABERTO"));
        }
        return linha.toString();
    }

    /** Lista de alunos como botões {@code aluno:<índice>} (o índice fica guardado na conversa). */
    public Mensagem listaAlunos(long chatId, String cabecalho, List<AlunoResumo> alunos) {
        List<List<Botao>> linhas = new ArrayList<>();
        for (int i = 0; i < alunos.size(); i++) {
            AlunoResumo aluno = alunos.get(i);
            String rotulo =
                    aluno.possuiRelatorioEmAberto()
                            ? aluno.nome() + " · " + rotulo("MARCA_EM_ABERTO")
                            : aluno.nome();
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
