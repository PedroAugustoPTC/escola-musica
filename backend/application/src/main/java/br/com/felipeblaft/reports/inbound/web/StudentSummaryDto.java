package br.com.felipeblaft.reports.inbound.web;

import java.time.LocalDate;
import java.util.UUID;

public record StudentSummaryDto(
        UUID id,
        String nome,
        String instrumento,
        LocalDate ultimaAulaEm,
        boolean possuiRelatorioEmAberto) {}
