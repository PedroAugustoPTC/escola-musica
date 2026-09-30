package br.com.felipeblaft.relatorios.dominio.relatorio;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Visão derivada de um relatório em aberto (data-model.md, "Relatório em aberto"): relatório não
 * aprovado nem cancelado, ou registro ainda em transcrição (sem relatório criado).
 */
public record RelatorioEmAberto(
        TipoRelatorio tipo,
        UUID relatorioId,
        UUID trabalhoId,
        UUID alunoId,
        String alunoNome,
        StatusRelatorio status,
        EtapaTrabalho etapa,
        String periodoChave,
        Canal canalOrigem,
        Instant atualizadoEm) {

    public RelatorioEmAberto {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(alunoId);
        Objects.requireNonNull(canalOrigem);
        Objects.requireNonNull(atualizadoEm);
    }

    /** Detalhes estruturados para o erro {@code RELATORIO_EM_ABERTO} (sem texto). */
    public Map<String, Object> detalhes() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("tipo", tipo);
        mapa.put("relatorioId", relatorioId);
        mapa.put("trabalhoId", trabalhoId);
        mapa.put("alunoId", alunoId);
        mapa.put("alunoNome", alunoNome);
        mapa.put("status", status);
        mapa.put("etapa", etapa);
        mapa.put("periodoChave", periodoChave);
        mapa.put("canalOrigem", canalOrigem);
        mapa.put("atualizadoEm", atualizadoEm);
        return mapa;
    }
}
