package br.com.felipeblaft.reports.port;

import java.util.UUID;

/** PDF marcado como removido cujo arquivo ainda precisa sair do armazenamento. */
public record PendingFileRemoval(UUID pdfId, String storageKey) {}
