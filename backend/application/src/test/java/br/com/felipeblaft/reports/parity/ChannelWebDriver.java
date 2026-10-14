package br.com.felipeblaft.reports.parity;

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
public class ChannelWebDriver implements ChannelDriver {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final String origin;
    private Cookie session;

    public ChannelWebDriver(MockMvc mvc, String origin) {
        this.mvc = mvc;
        this.origin = origin;
    }

    @Override
    public String name() {
        return "Web";
    }

    public Cookie session() {
        return session;
    }

    @Override
    public String signIn(String code) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr(origin);
                                                    return r;
                                                })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + code + "\"}"))
                        .andReturn()
                        .getResponse();
        if (response.getStatus() == 200) {
            Cookie cookie = response.getCookie("sessao");
            session = new Cookie(cookie.getName(), cookie.getValue());
            return "SUCESSO";
        }
        return JSON.readTree(response.getContentAsString()).get("codigo").asString();
    }

    private JsonNode fetchJson(String path) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get(path).cookie(session)).andReturn().getResponse();
        return JSON.readTree(response.getContentAsString());
    }

    @Override
    public HomeSummary start() throws Exception {
        JsonNode start = fetchJson("/api/v1/inicio");
        List<String> names = new ArrayList<>();
        start.get("alunos").forEach(a -> names.add(a.get("nome").asString()));
        return new HomeSummary(start.get("totalAlunos").asInt(), names);
    }

    @Override
    public SearchResult search(String text) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/api/v1/alunos").param("busca", text).cookie(session))
                        .andReturn()
                        .getResponse();
        JsonNode search = JSON.readTree(response.getContentAsString());
        List<String> names = new ArrayList<>();
        search.get("alunos").forEach(a -> names.add(a.get("nome").asString()));
        return new SearchResult(search.get("resultado").asString(), names);
    }

    @Override
    public String end() throws Exception {
        mvc.perform(delete("/api/v1/sessao").cookie(session).with(csrf().asHeader()));
        int status =
                mvc.perform(get("/api/v1/inicio").cookie(session))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        return status == 401 ? "ENCERRADO" : "ATIVO";
    }
}
