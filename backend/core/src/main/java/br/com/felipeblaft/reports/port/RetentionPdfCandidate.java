package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.PdfDocument;
import java.time.Instant;

/**
 * PDF lido com a linha do relatório bloqueada, para reavaliar a decisão da retenção.
 *
 * @param approvedVersion o PDF é da versão aprovada vigente
 * @param reportApprovedAt aprovação do relatório; nulo se não aprovado
 */
public record RetentionPdfCandidate(
        PdfDocument pdf, boolean approvedVersion, Instant reportApprovedAt) {}
