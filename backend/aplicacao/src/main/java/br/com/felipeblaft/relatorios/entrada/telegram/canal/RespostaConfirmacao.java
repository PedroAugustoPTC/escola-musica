package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import java.util.List;

/** Corpo 200 de {@code POST /confirmacoes}: {@code ignoradas} não é erro. */
public record RespostaConfirmacao(String versao, int aceitas, List<String> ignoradas) {}
