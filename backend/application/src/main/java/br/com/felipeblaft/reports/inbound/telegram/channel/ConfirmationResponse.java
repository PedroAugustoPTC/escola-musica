package br.com.felipeblaft.reports.inbound.telegram.channel;

import java.util.List;

/** Corpo 200 de {@code POST /confirmacoes}: {@code ignoradas} não é erro. */
public record ConfirmationResponse(String versao, int aceitas, List<String> ignoradas) {}
