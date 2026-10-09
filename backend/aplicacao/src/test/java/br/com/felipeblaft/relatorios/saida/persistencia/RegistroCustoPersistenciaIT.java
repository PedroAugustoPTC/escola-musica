package br.com.felipeblaft.relatorios.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TarefaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RegistroCustoPorta;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-056: o custo e o tempo de cada chamada de IA ficam em {@code chamada_ia}; a transcrição e a
 * consolidação, registradas antes de o relatório existir, são ligadas a ele depois; as chamadas de
 * um trabalho sem relatório continuam sem ligação. Os fluxos completos nos dois canais estão nos
 * roteiros de paridade.
 */
class RegistroCustoPersistenciaIT extends IntegracaoBase {

    @Autowired RegistroCustoPorta custos;
    @Autowired FilaTrabalhoPorta fila;
    @Autowired CenarioIT cenario;
    @Autowired TransactionTemplate transacao;

    private TrabalhoProcessamento trabalho(Professor professor, Aluno aluno) {
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        professor.id(),
                        aluno.id(),
                        null,
                        null,
                        Canal.WEB,
                        Meio.AUDIO,
                        EtapaTrabalho.ENVIANDO,
                        relogio.agora());
        fila.enfileirar(trabalho);
        return trabalho;
    }

    private void chamada(
            TrabalhoProcessamento trabalho, UUID relatorio, TarefaIa tarefa, String custo) {
        custos.registrar(
                ChamadaIa.nova(
                        trabalho.id(),
                        relatorio == null ? null : TipoRelatorio.AULA,
                        relatorio,
                        tarefa,
                        "provedor/modelo",
                        null,
                        100,
                        50,
                        1200,
                        new BigDecimal(custo),
                        true,
                        null,
                        relogio.agora()));
    }

    @Test
    void vincularLigaSoAsChamadasDoTrabalhoQueAindaNaoTemRelatorio() {
        Professor clara = cenario.clara();
        Aluno matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        Aluno helena = cenario.aluno(clara, "Helena Prado", "Violino");
        TrabalhoProcessamento a = trabalho(clara, matteo);
        TrabalhoProcessamento outro = trabalho(clara, helena);
        UUID relatorio = UUID.randomUUID();
        UUID jaLigado = UUID.randomUUID();

        chamada(a, null, TarefaIa.TRANSCRICAO, "0.01");
        chamada(a, jaLigado, TarefaIa.ESTRUTURACAO, "0.002");
        chamada(outro, null, TarefaIa.TRANSCRICAO, "0.5");

        transacao.executeWithoutResult(
                s -> custos.vincularRelatorio(a.id(), TipoRelatorio.AULA, relatorio));

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from chamada_ia where relatorio_id = ?",
                                Integer.class,
                                relatorio))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select tarefa from chamada_ia where relatorio_id = ?",
                                String.class,
                                relatorio))
                .isEqualTo("TRANSCRICAO");
        // a já ligada a outro relatório não muda, e o outro trabalho segue sem relatório
        assertThat(
                        jdbc.queryForObject(
                                "select relatorio_id from chamada_ia where tarefa = 'ESTRUTURACAO'",
                                UUID.class))
                .isEqualTo(jaLigado);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from chamada_ia where trabalho_id = ?"
                                        + " and relatorio_id is null",
                                Integer.class,
                                outro.id()))
                .isEqualTo(1);
        // custo por relatório consultável e sem prompt nem resposta guardados
        assertThat(
                        jdbc.queryForObject(
                                "select sum(custo_estimado_usd) from chamada_ia where relatorio_id = ?",
                                BigDecimal.class,
                                relatorio))
                .isEqualByComparingTo("0.01");
        assertThat(
                        jdbc.queryForObject(
                                "select duracao_ms from chamada_ia where relatorio_id = ?",
                                Long.class,
                                relatorio))
                .isEqualTo(1200L);
    }
}
