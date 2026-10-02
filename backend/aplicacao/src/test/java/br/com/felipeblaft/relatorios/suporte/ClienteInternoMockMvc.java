package br.com.felipeblaft.relatorios.suporte;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cliente da API interna do canal por MockMvc, com o token de serviço, para o executor simulado.
 */
public class ClienteInternoMockMvc implements ExecutorAcoesSimuladoTeste.ClienteInterno {

    private final MockMvc mvc;
    private final String token;

    public ClienteInternoMockMvc(MockMvc mvc, String token) {
        this.mvc = mvc;
        this.token = token;
    }

    @Override
    public Resposta get(String caminho) throws Exception {
        var resposta =
                mvc.perform(
                                MockMvcRequestBuilders.get(caminho)
                                        .header("X-N8N-Service-Token", token))
                        .andReturn()
                        .getResponse();
        return new Resposta(resposta.getStatus(), resposta.getContentAsByteArray());
    }

    @Override
    public Resposta post(String caminho, String corpoJson) throws Exception {
        var resposta =
                mvc.perform(
                                MockMvcRequestBuilders.post(caminho)
                                        .header("X-N8N-Service-Token", token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(corpoJson))
                        .andReturn()
                        .getResponse();
        return new Resposta(resposta.getStatus(), resposta.getContentAsByteArray());
    }
}
