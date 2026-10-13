package br.com.felipeblaft.reports.domain.report;

public enum DocumentType {
    /** PDF de toda versão, marcado "Documento em conferência — não oficial" (FR-049). */
    CONFERENCIA,
    /** PDF da versão aprovada, com quem aprovou, quando e qual versão (FR-030). */
    OFICIAL
}
