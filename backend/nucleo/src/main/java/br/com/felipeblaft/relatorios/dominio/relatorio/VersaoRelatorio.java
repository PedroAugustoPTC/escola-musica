package br.com.felipeblaft.relatorios.dominio.relatorio;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fotografia imutável do conteúdo de um relatório numa versão (Princípio III, FR-027): nunca é
 * atualizada nem apagada.
 */
public record VersaoRelatorio(
        UUID id,
        TipoRelatorio relatorioTipo,
        UUID relatorioId,
        int numero,
        ConteudoVersao conteudo,
        OrigemVersao origem,
        UUID interacaoId,
        Canal canal,
        String promptVersao,
        Instant criadaEm) {

    public VersaoRelatorio {
        Objects.requireNonNull(id);
        Objects.requireNonNull(relatorioTipo);
        Objects.requireNonNull(relatorioId);
        Objects.requireNonNull(conteudo);
        Objects.requireNonNull(origem);
        Objects.requireNonNull(canal);
        Objects.requireNonNull(criadaEm);
        if (numero < 1) {
            throw new IllegalArgumentException("versão começa em 1");
        }
    }

    public static VersaoRelatorio nova(
            TipoRelatorio tipo,
            UUID relatorioId,
            int numero,
            ConteudoVersao conteudo,
            OrigemVersao origem,
            UUID interacaoId,
            Canal canal,
            String promptVersao,
            Instant agora) {
        return new VersaoRelatorio(
                GeradorId.novo(),
                tipo,
                relatorioId,
                numero,
                conteudo,
                origem,
                interacaoId,
                canal,
                promptVersao,
                agora);
    }
}
