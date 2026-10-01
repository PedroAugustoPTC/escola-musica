package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.DocumentoObtido;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega autorizada dos PDFs (R-11, FR-048): sempre por rota do relatório, depois de conferir dono
 * e associação; o nome sugerido não revela identificadores internos e nada fica em cache.
 */
@RestController
@RequestMapping("/api/v1")
public class DocumentoControlador {

    private final ObterDocumentoRelatorioCasoDeUso obterDocumento;

    public DocumentoControlador(ObterDocumentoRelatorioCasoDeUso obterDocumento) {
        this.obterDocumento = obterDocumento;
    }

    @GetMapping("/relatorios-aula/{relatorioId}/versoes/{numero}/pdf")
    public ResponseEntity<byte[]> conferenciaAula(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @PathVariable int numero) {
        return pdf(
                obterDocumento.conferencia(
                        professor.id(), TipoRelatorio.AULA, relatorioId, numero));
    }

    @GetMapping("/relatorios-aula/{relatorioId}/pdf-oficial")
    public ResponseEntity<byte[]> oficialAula(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return pdf(obterDocumento.oficial(professor.id(), TipoRelatorio.AULA, relatorioId));
    }

    @GetMapping("/relatorios-semestrais/{relatorioId}/versoes/{numero}/pdf")
    public ResponseEntity<byte[]> conferenciaSemestral(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId,
            @PathVariable int numero) {
        return pdf(
                obterDocumento.conferencia(
                        professor.id(), TipoRelatorio.SEMESTRAL, relatorioId, numero));
    }

    @GetMapping("/relatorios-semestrais/{relatorioId}/pdf-oficial")
    public ResponseEntity<byte[]> oficialSemestral(
            @AuthenticationPrincipal ProfessorAutenticado professor,
            @PathVariable UUID relatorioId) {
        return pdf(obterDocumento.oficial(professor.id(), TipoRelatorio.SEMESTRAL, relatorioId));
    }

    static ResponseEntity<byte[]> pdf(DocumentoObtido documento) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(documento.nomeArquivo())
                                .build()
                                .toString())
                .body(documento.conteudo());
    }
}
