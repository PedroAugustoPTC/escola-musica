package br.com.felipeblaft.relatorios.porta;

import java.math.BigDecimal;

/** Texto transcrito com duração, custo estimado e identificação opaca do provedor/modelo. */
public record ResultadoTranscricao(
        String texto, double duracaoSegundos, BigDecimal custoEstimado, String provedorModelo) {

    @Override
    public String toString() {
        return "ResultadoTranscricao[duracao=" + duracaoSegundos + "]";
    }
}
