package br.com.felipeblaft.relatorios.saida.fila;

import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.NotificacaoProfessorPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aos 3 minutos sem conclusão, avisa uma vez que está demorando (caso de borda "processamento que
 * excede o tempo esperado"). A Web vê {@code demorando} na consulta do trabalho.
 */
@Component
@Profile("!carga")
public class AvisoDemoraTarefa {

    private final FilaTrabalhoPorta fila;
    private final NotificacaoProfessorPorta notificacao;
    private final RelogioPorta relogio;

    public AvisoDemoraTarefa(
            FilaTrabalhoPorta fila, NotificacaoProfessorPorta notificacao, RelogioPorta relogio) {
        this.fila = fila;
        this.notificacao = notificacao;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void verificar() {
        for (TrabalhoProcessamento trabalho :
                fila.listarDemoradosSemAviso(
                        relogio.agora().minus(TrabalhoProcessamento.LIMITE_DEMORA))) {
            if (fila.marcarAvisoDemora(trabalho.id())) {
                trabalho.marcarAvisoDemora();
                notificacao.avisarDemora(trabalho);
            }
        }
    }
}
