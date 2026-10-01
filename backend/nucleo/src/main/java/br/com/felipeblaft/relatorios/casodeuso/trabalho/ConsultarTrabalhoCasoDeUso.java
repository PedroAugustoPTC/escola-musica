package br.com.felipeblaft.relatorios.casodeuso.trabalho;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Etapa e resultado de um processamento, consultados pela Web a cada 2 s (FR-021). Trabalho de
 * outro professor é {@code RECURSO_NAO_ENCONTRADO}.
 */
public class ConsultarTrabalhoCasoDeUso {

    public record VisaoTrabalho(
            TrabalhoProcessamento trabalho, boolean demorando, Optional<String> textoEntendido) {}

    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public ConsultarTrabalhoCasoDeUso(FilaTrabalhoPorta fila, RelogioPorta relogio) {
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public VisaoTrabalho executar(UUID professorId, UUID trabalhoId) {
        TrabalhoProcessamento trabalho =
                fila.buscarPorId(trabalhoId)
                        .filter(t -> t.professorId().equals(professorId))
                        .orElseThrow(() -> new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO));
        Optional<String> texto =
                trabalho.parametro(TrabalhoProcessamento.TEXTO_ENTENDIDO)
                        .or(() -> trabalho.parametro(TrabalhoProcessamento.TEXTO));
        return new VisaoTrabalho(trabalho, trabalho.demorando(relogio.agora()), texto);
    }
}
