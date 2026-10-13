package br.com.felipeblaft.reports.entrada.telegram.canal;

import java.util.List;

/** Corpo 200 de {@code POST /confirmacoes}: {@code ignoradas} não é erro. */
public record ConfirmacaoResposta(String versao, int aceitas, List<String> ignoradas) {}
