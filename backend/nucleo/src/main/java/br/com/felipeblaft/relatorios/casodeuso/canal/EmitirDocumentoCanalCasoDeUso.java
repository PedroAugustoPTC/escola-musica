package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Emite o identificador opaco de um PDF para o orquestrador (R-08): token de 256 bits (Base64url,
 * 43 caracteres), com só o hash SHA-256 guardado. Vale pelo prazo de entrega mais uma hora, para
 * cobrir todas as retentativas.
 */
public class EmitirDocumentoCanalCasoDeUso {

    public static final Duration FOLGA_VALIDADE = Duration.ofHours(1);

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final DocumentoCanalRepositorio documentos;
    private final RelogioPorta relogio;
    private final PoliticaRetentativa politica;

    public EmitirDocumentoCanalCasoDeUso(
            DocumentoCanalRepositorio documentos,
            RelogioPorta relogio,
            PoliticaRetentativa politica) {
        this.documentos = Objects.requireNonNull(documentos);
        this.relogio = Objects.requireNonNull(relogio);
        this.politica = Objects.requireNonNull(politica);
    }

    /**
     * @param versao número da versão de conferência; nulo para o PDF oficial
     * @return o token a entregar ao orquestrador (nunca mais é recuperável do banco)
     */
    public String executar(
            UUID professorId,
            TipoRelatorio tipo,
            UUID relatorioId,
            Integer versao,
            String nomeArquivo) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant agora = relogio.agora();
        documentos.emitir(
                new DocumentoCanal(
                        DocumentoCanal.hashDoToken(token),
                        professorId,
                        tipo,
                        relatorioId,
                        versao,
                        nomeArquivo,
                        agora,
                        agora.plus(politica.prazo()).plus(FOLGA_VALIDADE)));
        return token;
    }
}
