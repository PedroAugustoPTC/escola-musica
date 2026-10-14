package br.com.felipeblaft.reports.inbound.telegram.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Corpo de {@code POST /confirmacoes} ({@code #/$defs/RequisicaoConfirmacao}). É um corpo
 * <em>recebido</em>: campos desconhecidos são ignorados (evolução aditiva).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmationRequest(String versao, List<ActionResultDto> resultados) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionResultDto(
            String acaoId, String situacao, String codigoMotivo, Long mensagemId) {}
}
