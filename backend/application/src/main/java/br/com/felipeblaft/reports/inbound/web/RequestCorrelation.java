package br.com.felipeblaft.reports.inbound.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Identificador de correlação da requisição Web, usado na autoria (FR-040): um só mecanismo para as
 * rotas do professor e as da área administrativa. O filtro administrativo o carimba na entrada; as
 * demais rotas o recebem na primeira leitura. Dentro da mesma requisição o valor é sempre o mesmo.
 */
public final class RequestCorrelation {

    /** Constante literal: usada em anotações ({@code @RequestAttribute}). */
    public static final String ATTRIBUTE =
            "br.com.felipeblaft.reports.inbound.web.RequestCorrelation.id";

    private RequestCorrelation() {}

    /** Define um identificador novo para a requisição e o devolve. */
    public static String assign(HttpServletRequest request) {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        return id;
    }

    /** O identificador da requisição; se ainda não há um, define. */
    public static String of(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String id ? id : assign(request);
    }
}
