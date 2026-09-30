package br.com.felipeblaft.relatorios.dominio.telegram;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.util.Objects;
import java.util.UUID;

/** Relatório apresentado no chat (perguntas ou conferência), ao qual texto e áudio se aplicam. */
public record RelatorioFoco(TipoRelatorio tipo, UUID id) {

    public RelatorioFoco {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(id);
    }
}
