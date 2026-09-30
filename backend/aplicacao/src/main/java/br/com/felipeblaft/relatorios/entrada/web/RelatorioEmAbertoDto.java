package br.com.felipeblaft.relatorios.entrada.web;

import java.time.Instant;
import java.util.UUID;

public record RelatorioEmAbertoDto(
        String tipo,
        UUID relatorioId,
        UUID trabalhoId,
        UUID alunoId,
        String alunoNome,
        String status,
        String etapa,
        String periodoChave,
        String canalOrigem,
        Instant atualizadoEm) {}
