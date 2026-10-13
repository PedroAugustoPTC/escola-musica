package br.com.felipeblaft.reports.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
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

    @Autowired CostRecordPort custos;
    @Autowired JobQueuePort fila;
    @Autowired CenarioIT cenario;
    @Autowired TransactionTemplate transacao;

    private ProcessingJob trabalho(Teacher professor, Student aluno) {
        ProcessingJob trabalho =
                ProcessingJob.create(
                        JobType.REGISTRO_AULA,
                        professor.id(),
                        aluno.id(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.AUDIO,
                        JobStep.ENVIANDO,
                        relogio.now());
        fila.enqueue(trabalho);
        return trabalho;
    }

    private void chamada(ProcessingJob trabalho, UUID relatorio, AiTask tarefa, String custo) {
        custos.register(
                AiCall.create(
                        trabalho.id(),
                        relatorio == null ? null : ReportType.AULA,
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
                        relogio.now()));
    }

    @Test
    void vincularLigaSoAsChamadasDoTrabalhoQueAindaNaoTemRelatorio() {
        Teacher clara = cenario.clara();
        Student matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        Student helena = cenario.aluno(clara, "Helena Prado", "Violino");
        ProcessingJob a = trabalho(clara, matteo);
        ProcessingJob outro = trabalho(clara, helena);
        UUID relatorio = UUID.randomUUID();
        UUID jaLigado = UUID.randomUUID();

        chamada(a, null, AiTask.TRANSCRICAO, "0.01");
        chamada(a, jaLigado, AiTask.ESTRUTURACAO, "0.002");
        chamada(outro, null, AiTask.TRANSCRICAO, "0.5");

        transacao.executeWithoutResult(s -> custos.linkReport(a.id(), ReportType.AULA, relatorio));

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
