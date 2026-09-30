package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.util.Objects;

/**
 * Persiste o estado da conversa depois de cada update (FR-036): o adaptador do Telegram não guarda
 * nada em memória. Dois updates simultâneos da mesma conversa são resolvidos por bloqueio otimista.
 */
public class AtualizarConversaTelegramCasoDeUso {

    private final VinculoTelegramRepositorio vinculos;
    private final RelogioPorta relogio;

    public AtualizarConversaTelegramCasoDeUso(
            VinculoTelegramRepositorio vinculos, RelogioPorta relogio) {
        this.vinculos = Objects.requireNonNull(vinculos);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public void executar(VinculoTelegram vinculo) {
        vinculo.tocar(relogio.agora());
        vinculos.salvar(vinculo);
    }
}
