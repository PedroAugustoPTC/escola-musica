package br.com.felipeblaft.reports.parity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
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
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr(origin);
                                                    return r;
                                                })
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + code + "\"}"))
                        .andReturn()
                        .getResponse();
        if (response.getStatus() == 200) {
            Cookie cookie = response.getCookie("session");
            session = new Cookie(cookie.getName(), cookie.getValue());
            return "SUCCESS";
        }
        return JSON.readTree(response.getContentAsString()).get("code").asString();
    }

    private JsonNode fetchJson(String path) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get(path).cookie(session)).andReturn().getResponse();
        return JSON.readTree(response.getContentAsString());
    }

    @Override
    public HomeSummary start() throws Exception {
        JsonNode start = fetchJson("/api/v1/home");
        List<String> names = new ArrayList<>();
        start.get("students").forEach(a -> names.add(a.get("name").asString()));
        return new HomeSummary(start.get("totalStudents").asInt(), names);
    }

    @Override
    public SearchResult search(String text) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/api/v1/students").param("search", text).cookie(session))
                        .andReturn()
                        .getResponse();
        JsonNode search = JSON.readTree(response.getContentAsString());
        List<String> names = new ArrayList<>();
        search.get("students").forEach(a -> names.add(a.get("name").asString()));
        return new SearchResult(search.get("result").asString(), names);
    }

    @Override
    public String end() throws Exception {
        mvc.perform(delete("/api/v1/session").cookie(session).with(csrf().asHeader()));
        int status =
                mvc.perform(get("/api/v1/home").cookie(session))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        return status == 401 ? "ENDED" : "ACTIVE";
    }

    // ---- "Meus alunos"

    private MockHttpServletResponse mine(HttpMethod method, String path, Object body)
            throws Exception {
        var builder =
                request(method, "/api/v1/my-students" + path)
                        .cookie(session)
                        .with(csrf().asHeader());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    private String outcome(MockHttpServletResponse response) throws Exception {
        if (response.getStatus() / 100 == 2) {
            return "SUCCESS";
        }
        return JSON.readTree(response.getContentAsString()).get("code").asString();
    }

    /** Item da lista (todas as situações) com o nome dado. */
    private JsonNode itemNamed(String name) throws Exception {
        MockHttpServletResponse response = mine(HttpMethod.GET, "?status=ALL&size=100", null);
        for (JsonNode item : JSON.readTree(response.getContentAsString()).get("items")) {
            if (item.get("name").asString().equals(name)) {
                return item;
            }
        }
        throw new IllegalStateException("aluno fora da lista: " + name);
    }

    @Override
    public String registerStudent(NewStudent student) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", student.name());
        body.put(
                "birthDate",
                LocalDate.parse(student.birthDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .toString());
        body.put("cpf", student.cpf());
        if (student.instrument() != null) {
            body.put("instrument", student.instrument());
        }
        if (student.guardian() != null) {
            body.put(
                    "guardian",
                    Map.of(
                            "name", student.guardian().name(),
                            "phone", student.guardian().phone(),
                            "email", student.guardian().email()));
            body.put(
                    "consent",
                    Map.of(
                            "consentingName", "Responsável Fictício",
                            "relationship", "MOTHER",
                            "consentedOn", "2025-01-02",
                            "termVersion", 1,
                            "collectionMethod", "DIGITAL"));
        }
        return outcome(mine(HttpMethod.POST, "", body));
    }

    private List<String> names(String query) throws Exception {
        MockHttpServletResponse response = mine(HttpMethod.GET, query, null);
        List<String> names = new ArrayList<>();
        JSON.readTree(response.getContentAsString())
                .get("items")
                .forEach(item -> names.add(item.get("name").asString()));
        return names;
    }

    @Override
    public List<String> listStudents(String status) throws Exception {
        return names("?status=" + status);
    }

    @Override
    public List<String> searchStudents(String text) throws Exception {
        return names("?search=" + text);
    }

    @Override
    public String openStudent(String name) throws Exception {
        String id = itemNamed(name).get("id").asString();
        JsonNode card = JSON.readTree(mine(HttpMethod.GET, "/" + id, null).getContentAsString());
        return card.get("name").asString()
                + "|"
                + card.get("age").asInt()
                + "|"
                + (card.hasNonNull("instrument") ? card.get("instrument").asString() : "-")
                + "|"
                + (card.get("active").asBoolean() ? "ACTIVE" : "INACTIVE");
    }

    @Override
    public String changeInstrument(String name, String instrument) throws Exception {
        String id = itemNamed(name).get("id").asString();
        return outcome(mine(HttpMethod.PATCH, "/" + id, Map.of("instrument", instrument)));
    }

    @Override
    public String inactivateStudent(String name) throws Exception {
        String id = itemNamed(name).get("id").asString();
        return outcome(mine(HttpMethod.POST, "/" + id + "/inactivation", null));
    }

    @Override
    public String reactivateStudent(String name) throws Exception {
        String id = itemNamed(name).get("id").asString();
        return outcome(mine(HttpMethod.POST, "/" + id + "/reactivation", null));
    }
}
