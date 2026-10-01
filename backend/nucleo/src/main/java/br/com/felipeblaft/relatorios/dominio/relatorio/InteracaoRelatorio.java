package br.com.felipeblaft.relatorios.dominio.relatorio;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resposta às perguntas ou pedido de alteração, por texto ou áudio (FR-056). O {@code texto} é o
 * digitado ou o transcrito: é o "texto entendido" mostrado ao professor (FR-024a, FR-027a).
 */
public record InteracaoRelatorio(
        UUID id,
        TipoRelatorio relatorioTipo,
        UUID relatorioId,
        TipoInteracao tipo,
        Canal canal,
        Meio meio,
        String texto,
        int versaoBase,
        Integer rodada,
        ResultadoInteracao resultado,
        Integer versaoResultante,
        Instant criadoEm) {

    public InteracaoRelatorio {
        Objects.requireNonNull(id);
        Objects.requireNonNull(relatorioTipo);
        Objects.requireNonNull(relatorioId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(canal);
        Objects.requireNonNull(meio);
        Objects.requireNonNull(texto);
        Objects.requireNonNull(resultado);
        Objects.requireNonNull(criadoEm);
    }

    public static InteracaoRelatorio nova(
            TipoRelatorio relatorioTipo,
            UUID relatorioId,
            TipoInteracao tipo,
            Canal canal,
            Meio meio,
            String texto,
            int versaoBase,
            Integer rodada,
            ResultadoInteracao resultado,
            Integer versaoResultante,
            Instant agora) {
        return new InteracaoRelatorio(
                GeradorId.novo(),
                relatorioTipo,
                relatorioId,
                tipo,
                canal,
                meio,
                texto,
                versaoBase,
                rodada,
                resultado,
                versaoResultante,
                agora);
    }

    @Override
    public String toString() {
        return "InteracaoRelatorio[id=" + id + ", tipo=" + tipo + ", resultado=" + resultado + "]";
    }
}
