package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Corpo de {@code POST /confirmacoes} ({@code #/$defs/RequisicaoConfirmacao}). É um corpo
 * <em>recebido</em>: campos desconhecidos são ignorados (evolução aditiva).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmacaoRequisicao(String versao, List<ResultadoAcaoDto> resultados) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoAcaoDto(
            String acaoId, String situacao, String codigoMotivo, Long mensagemId) {}
}
