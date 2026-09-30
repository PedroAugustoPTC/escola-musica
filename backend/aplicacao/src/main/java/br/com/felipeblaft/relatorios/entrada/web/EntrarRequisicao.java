package br.com.felipeblaft.relatorios.entrada.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EntrarRequisicao(@NotBlank @Size(max = 16) String codigo) {

    @Override
    public String toString() {
        return "EntrarRequisicao[codigo=***]";
    }
}
