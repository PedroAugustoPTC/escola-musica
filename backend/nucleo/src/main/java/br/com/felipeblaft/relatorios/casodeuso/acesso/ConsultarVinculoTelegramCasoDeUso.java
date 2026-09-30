package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.util.Objects;
import java.util.UUID;

/** Informa se o professor tem conta do Telegram vinculada (usado pela sessão da Web). */
public class ConsultarVinculoTelegramCasoDeUso {

    private final VinculoTelegramRepositorio vinculos;

    public ConsultarVinculoTelegramCasoDeUso(VinculoTelegramRepositorio vinculos) {
        this.vinculos = Objects.requireNonNull(vinculos);
    }

    public boolean executar(UUID professorId) {
        return vinculos.buscarPorProfessorId(professorId).isPresent();
    }
}
