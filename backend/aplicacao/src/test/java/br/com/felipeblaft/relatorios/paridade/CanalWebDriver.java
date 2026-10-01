package br.com.felipeblaft.relatorios.paridade;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** O roteiro de paridade pelo portal: chamadas à API com MockMvc, a partir de uma origem fixa. */
public class CanalWebDriver implements CanalDriver {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final String origem;
    private Cookie sessao;

    public CanalWebDriver(MockMvc mvc, String origem) {
        this.mvc = mvc;
        this.origem = origem;
    }

    @Override
    public String nome() {
        return "Web";
    }

    public Cookie sessao() {
        return sessao;
    }

    @Override
    public String entrar(String codigo) throws Exception {
        MockHttpServletResponse resposta =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr(origem);
                                                    return r;
                                                })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + codigo + "\"}"))
                        .andReturn()
                        .getResponse();
        if (resposta.getStatus() == 200) {
            Cookie cookie = resposta.getCookie("sessao");
            sessao = new Cookie(cookie.getName(), cookie.getValue());
            return "SUCESSO";
        }
        return JSON.readTree(resposta.getContentAsString()).get("codigo").asString();
    }

    private JsonNode obter(String caminho) throws Exception {
        MockHttpServletResponse resposta =
                mvc.perform(get(caminho).cookie(sessao)).andReturn().getResponse();
        return JSON.readTree(resposta.getContentAsString());
    }

    @Override
    public ResumoInicio inicio() throws Exception {
        JsonNode inicio = obter("/api/v1/inicio");
        List<String> nomes = new ArrayList<>();
        inicio.get("alunos").forEach(a -> nomes.add(a.get("nome").asString()));
        return new ResumoInicio(inicio.get("totalAlunos").asInt(), nomes);
    }

    @Override
    public ResultadoBusca buscar(String texto) throws Exception {
        MockHttpServletResponse resposta =
                mvc.perform(get("/api/v1/alunos").param("busca", texto).cookie(sessao))
                        .andReturn()
                        .getResponse();
        JsonNode busca = JSON.readTree(resposta.getContentAsString());
        List<String> nomes = new ArrayList<>();
        busca.get("alunos").forEach(a -> nomes.add(a.get("nome").asString()));
        return new ResultadoBusca(busca.get("resultado").asString(), nomes);
    }

    @Override
    public String encerrar() throws Exception {
        mvc.perform(delete("/api/v1/sessao").cookie(sessao).with(csrf().asHeader()));
        int status =
                mvc.perform(get("/api/v1/inicio").cookie(sessao))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        return status == 401 ? "ENCERRADO" : "ATIVO";
    }
}
