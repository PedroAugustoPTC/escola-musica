package br.com.felipeblaft.relatorios.entrada.web;

import java.time.Instant;

public record SessaoDto(
        ProfessorResumoDto professor, Instant sessaoExpiraEm, boolean telegramVinculado) {}
