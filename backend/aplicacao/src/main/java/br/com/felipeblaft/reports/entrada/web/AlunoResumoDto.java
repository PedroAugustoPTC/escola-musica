package br.com.felipeblaft.reports.entrada.web;

import java.time.LocalDate;
import java.util.UUID;

public record AlunoResumoDto(
        UUID id,
        String nome,
        String instrumento,
        LocalDate ultimaAulaEm,
        boolean possuiRelatorioEmAberto) {}
