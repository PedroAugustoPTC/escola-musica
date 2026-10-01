package br.com.felipeblaft.relatorios.paridade;

import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * O roteiro de paridade pelo bot: updates enviados ao webhook e leitura das mensagens enviadas à
 * API de bots simulada. Os textos são traduzidos de volta para os códigos do catálogo.
 */
public class CanalTelegramDriver implements CanalDriver {

    /** Envia um texto como o professor e devolve as mensagens do bot produzidas por ele. */
    public interface Conversa {
        List<JsonNode> enviar(String texto) throws Exception;
    }

    private static final Pattern TOTAL = Pattern.compile("(\\d+) aluno\\(s\\) associado\\(s\\)");
    private static final Pattern ALUNO =
            Pattern.compile("^• ([^·\\n]+?)(?: ·.*)?$", Pattern.MULTILINE);

    private final Conversa conversa;
    private final CatalogoMensagens catalogo;
    private final Supplier<Long> vinculos;

    public CanalTelegramDriver(
            Conversa conversa, CatalogoMensagens catalogo, Supplier<Long> vinculos) {
        this.conversa = conversa;
        this.catalogo = catalogo;
        this.vinculos = vinculos;
    }

    @Override
    public String nome() {
        return "Telegram";
    }

    private String ultimoTexto(List<JsonNode> mensagens) {
        return mensagens.getLast().get("text").asString();
    }

    /** Código do catálogo cujo texto (até o primeiro parâmetro) inicia a mensagem. */
    String codigoDoTexto(String texto) {
        String melhor = null;
        int maior = 0;
        for (String chave : catalogo.chaves()) {
            String modelo = catalogo.modelo(chave);
            int parametro = modelo.indexOf('{');
            String prefixo = parametro >= 0 ? modelo.substring(0, parametro) : modelo;
            if (prefixo.length() > maior && !prefixo.isBlank() && texto.startsWith(prefixo)) {
                melhor = chave;
                maior = prefixo.length();
            }
        }
        return melhor;
    }

    @Override
    public String entrar(String codigo) throws Exception {
        String texto = ultimoTexto(conversa.enviar(codigo));
        if (texto.startsWith(catalogo.texto("VINCULO_CRIADO").substring(0, 20))) {
            return "SUCESSO";
        }
        return codigoDoTexto(texto);
    }

    @Override
    public ResumoInicio inicio() throws Exception {
        String texto = ultimoTexto(conversa.enviar("início"));
        Matcher total = TOTAL.matcher(texto);
        int quantidade = total.find() ? Integer.parseInt(total.group(1)) : 0;
        List<String> nomes = new ArrayList<>();
        String alunos = texto.substring(texto.indexOf("aluno(s) associado(s)"));
        Matcher aluno = ALUNO.matcher(alunos);
        while (aluno.find()) {
            nomes.add(aluno.group(1).strip());
        }
        return new ResumoInicio(quantidade, nomes);
    }

    @Override
    public ResultadoBusca buscar(String texto) throws Exception {
        JsonNode mensagem = conversa.enviar(texto).getLast();
        String resposta = mensagem.get("text").asString();
        String codigo = codigoDoTexto(resposta);
        List<String> nomes = new ArrayList<>();
        if ("ALUNO_SELECIONADO".equals(codigo)) {
            Matcher m = Pattern.compile(", com (.+?)\\. Envie").matcher(resposta);
            if (m.find()) {
                nomes.add(m.group(1));
            }
            return new ResultadoBusca("ENCONTRADOS", nomes);
        }
        for (JsonNode linha : mensagem.path("reply_markup").path("inline_keyboard")) {
            for (JsonNode botao : linha) {
                if (botao.get("callback_data").asString().startsWith("v1:aluno:")) {
                    nomes.add(botao.get("text").asString().replace(" · Em aberto", ""));
                }
            }
        }
        String tipo = "NENHUM_ALUNO_ENCONTRADO".equals(codigo) ? "NENHUM" : "ENCONTRADOS";
        return new ResultadoBusca(tipo, nomes);
    }

    @Override
    public String encerrar() throws Exception {
        conversa.enviar("sair");
        conversa.enviar("sim");
        return vinculos.get() == 0 ? "ENCERRADO" : "ATIVO";
    }
}
