package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Monta a resposta de um PDF entregue pela API: tipo {@code application/pdf}, nada em cache ({@code
 * no-store, private}) e o nome sugerido, que não revela identificadores internos. {@code inline}
 * para abrir no navegador, {@code attachment} para baixar. Fonte única do portal do professor e da
 * área administrativa.
 */
public final class PdfResponses {

    private PdfResponses() {}

    /** PDF para exibir no navegador. */
    public static ResponseEntity<byte[]> inline(ObtainedDocument document) {
        return build(document, ContentDisposition.inline());
    }

    /** O mesmo PDF, para baixar. */
    public static ResponseEntity<byte[]> attachment(ObtainedDocument document) {
        return build(document, ContentDisposition.attachment());
    }

    private static ResponseEntity<byte[]> build(
            ObtainedDocument document, ContentDisposition.Builder disposition) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.filename(document.fileName()).build().toString())
                .body(document.content());
    }
}
