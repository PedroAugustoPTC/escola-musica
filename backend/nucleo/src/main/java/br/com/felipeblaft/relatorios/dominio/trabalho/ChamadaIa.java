package br.com.felipeblaft.relatorios.dominio.trabalho;

import br.com.felipeblaft.relatorios.dominio.comum.GeradorId;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de custo e tempo de uma chamada de IA (FR-056, R-13). Não guarda prompt, resposta nem
 * transcrição (FR-055). Provedor e modelo vêm da configuração, opacos para o núcleo.
 */
public record ChamadaIa(
        UUID id,
        UUID trabalhoId,
        TipoRelatorio relatorioTipo,
        UUID relatorioId,
        TarefaIa tarefa,
        String provedorModelo,
        BigDecimal segundosAudio,
        Integer tokensEntrada,
        Integer tokensSaida,
        long duracaoMs,
        BigDecimal custoEstimadoUsd,
        boolean sucesso,
        String codigoErro,
        Instant criadoEm) {

    public ChamadaIa {
        Objects.requireNonNull(id);
        Objects.requireNonNull(trabalhoId);
        Objects.requireNonNull(tarefa);
        Objects.requireNonNull(criadoEm);
    }

    /** A mesma chamada vinculada a um relatório. */
    public ChamadaIa comRelatorio(TipoRelatorio tipo, UUID idDoRelatorio) {
        return new ChamadaIa(
                id,
                trabalhoId,
                tipo,
                idDoRelatorio,
                tarefa,
                provedorModelo,
                segundosAudio,
                tokensEntrada,
                tokensSaida,
                duracaoMs,
                custoEstimadoUsd,
                sucesso,
                codigoErro,
                criadoEm);
    }

    public static ChamadaIa nova(
            UUID trabalhoId,
            TipoRelatorio relatorioTipo,
            UUID relatorioId,
            TarefaIa tarefa,
            String provedorModelo,
            BigDecimal segundosAudio,
            Integer tokensEntrada,
            Integer tokensSaida,
            long duracaoMs,
            BigDecimal custo,
            boolean sucesso,
            String codigoErro,
            Instant agora) {
        return new ChamadaIa(
                GeradorId.novo(),
                trabalhoId,
                relatorioTipo,
                relatorioId,
                tarefa,
                provedorModelo,
                segundosAudio,
                tokensEntrada,
                tokensSaida,
                duracaoMs,
                custo,
                sucesso,
                codigoErro,
                agora);
    }
}
