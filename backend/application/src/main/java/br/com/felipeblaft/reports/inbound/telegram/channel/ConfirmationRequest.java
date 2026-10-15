package br.com.felipeblaft.reports.inbound.telegram.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Corpo de {@code POST /confirmacoes} ({@code #/$defs/RequisicaoConfirmacao}). É um corpo
 * <em>recebido</em>: campos desconhecidos são ignorados (evolução aditiva).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmationRequest(String version, List<ActionResultDto> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionResultDto(
            String actionId, String outcome, String reasonCode, Long messageId) {}
}
