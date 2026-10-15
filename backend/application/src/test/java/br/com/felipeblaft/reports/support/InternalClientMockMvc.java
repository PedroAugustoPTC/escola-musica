package br.com.felipeblaft.reports.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cliente da API interna do canal por MockMvc, com o token de serviço, para o executor simulado.
 */
public class InternalClientMockMvc implements SimulatedActionsExecutorTest.InternalClient {

    private final MockMvc mvc;
    private final String token;

    public InternalClientMockMvc(MockMvc mvc, String token) {
        this.mvc = mvc;
        this.token = token;
    }

    @Override
    public Response get(String path) throws Exception {
        var response =
                mvc.perform(MockMvcRequestBuilders.get(path).header("X-N8N-Service-Token", token))
                        .andReturn()
                        .getResponse();
        return new Response(response.getStatus(), response.getContentAsByteArray());
    }

    @Override
    public Response post(String path, String bodyJson) throws Exception {
        var response =
                mvc.perform(
                                MockMvcRequestBuilders.post(path)
                                        .header("X-N8N-Service-Token", token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(bodyJson))
                        .andReturn()
                        .getResponse();
        return new Response(response.getStatus(), response.getContentAsByteArray());
    }
}
