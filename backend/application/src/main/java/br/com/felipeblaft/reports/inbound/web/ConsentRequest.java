package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.privacy.ConsentInput;

/**
 * Corpo do consentimento do responsável (schema {@code ConsentInput} de {@code
 * api-privacy-operations.openapi.yaml}), igual no cadastro, na edição e no registro avulso, na Web
 * do professor e na do administrador. Sem validação aqui: o núcleo valida. O {@code toString} não
 * revela o nome de quem consentiu.
 */
public record ConsentRequest(
        String consentingName,
        String relationship,
        String consentedOn,
        Integer termVersion,
        String collectionMethod) {

    /** Converte para o tipo do núcleo; corpo ausente vira {@code null}. */
    public static ConsentInput input(ConsentRequest request) {
        return request == null
                ? null
                : new ConsentInput(
                        request.consentingName(),
                        request.relationship(),
                        request.consentedOn(),
                        request.termVersion(),
                        request.collectionMethod());
    }

    @Override
    public String toString() {
        return "ConsentRequest";
    }
}
