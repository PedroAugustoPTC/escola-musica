package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.util.UUID;

/**
 * Identifica o PDF que um {@link SaidaTelegram.Documento} carrega, sem os bytes: é o que a rota
 * interna do canal usa para emitir o identificador opaco que o orquestrador troca pelos bytes.
 * {@code versao} nulo significa o PDF oficial (aprovado).
 */
public record ReferenciaDocumento(
        UUID professorId, TipoRelatorio tipo, UUID relatorioId, Integer versao) {

    public static ReferenciaDocumento conferencia(
            UUID professorId, TipoRelatorio tipo, UUID relatorioId, int versao) {
        return new ReferenciaDocumento(professorId, tipo, relatorioId, versao);
    }

    public static ReferenciaDocumento oficial(
            UUID professorId, TipoRelatorio tipo, UUID relatorioId) {
        return new ReferenciaDocumento(professorId, tipo, relatorioId, null);
    }
}
