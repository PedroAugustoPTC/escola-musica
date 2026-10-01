package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.ResultadoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.TrabalhoProcessamentoEntidade;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * Fila de trabalhos no PostgreSQL (R-06): reserva com {@code FOR UPDATE SKIP LOCKED}, sem duplicar
 * trabalho entre workers; índice único parcial impede dois registros em curso para o mesmo aluno.
 */
@Repository
public class FilaTrabalhoPostgresAdaptador implements FilaTrabalhoPorta {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private final TrabalhoProcessamentoJpa jpa;

    FilaTrabalhoPostgresAdaptador(TrabalhoProcessamentoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void enfileirar(TrabalhoProcessamento trabalho) {
        try {
            jpa.saveAndFlush(paraEntidade(trabalho));
        } catch (DataIntegrityViolationException conflito) {
            throw new ConflitoConcorrenciaExcecao("trabalho_processamento");
        }
    }

    @Override
    @Transactional
    public void atualizar(TrabalhoProcessamento trabalho) {
        // um trabalho descartado por outro canal não volta a ficar ativo
        Optional<TrabalhoProcessamentoEntidade> atual = jpa.findById(trabalho.id());
        if (atual.isPresent()
                && StatusTrabalho.DESCARTADO.name().equals(atual.get().status)
                && trabalho.status() != StatusTrabalho.DESCARTADO) {
            return;
        }
        // a mensagem de etapa do Telegram é registrada à parte, pelo envio; a gravação do worker a
        // mantém
        if (atual.isPresent()
                && trabalho.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA).isEmpty()) {
            JsonPersistencia.ler(atual.get().parametros, MAPA).entrySet().stream()
                    .filter(p -> TrabalhoProcessamento.MENSAGEM_ETAPA.equals(p.getKey()))
                    .findFirst()
                    .ifPresent(p -> trabalho.definirParametro(p.getKey(), p.getValue()));
        }
        TrabalhoProcessamentoEntidade entidade = paraEntidade(trabalho);
        // o aviso de demora é marcado à parte; uma gravação do worker não o desfaz
        entidade.avisoDemoraEnviado =
                entidade.avisoDemoraEnviado || atual.map(a -> a.avisoDemoraEnviado).orElse(false);
        jpa.saveAndFlush(entidade);
    }

    /** Grava o id da mensagem de etapa do Telegram sem disputar com a gravação do worker. */
    @Transactional
    public void registrarMensagemEtapa(UUID trabalhoId, long mensagemId) {
        jpa.definirParametro(
                trabalhoId, TrabalhoProcessamento.MENSAGEM_ETAPA, Long.toString(mensagemId));
    }

    @Override
    @Transactional
    public boolean marcarAvisoDemora(UUID trabalhoId) {
        return jpa.marcarAvisoDemora(trabalhoId) > 0;
    }

    @Override
    public Optional<TrabalhoProcessamento> buscarPorId(UUID id) {
        return jpa.findById(id).map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<TrabalhoProcessamento> reservarProximo(Instant agora) {
        Optional<UUID> id = jpa.proximoPendente();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        TrabalhoProcessamento trabalho = paraDominio(jpa.findById(id.get()).orElseThrow());
        trabalho.iniciar(agora);
        jpa.saveAndFlush(paraEntidade(trabalho));
        return Optional.of(trabalho);
    }

    @Override
    public Optional<TrabalhoProcessamento> buscarEmCursoDoRelatorio(
            TipoRelatorio tipo, UUID relatorioId) {
        return jpa.emCursoDoRelatorio(tipo.name(), relatorioId).stream()
                .findFirst()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    @Override
    public Optional<TrabalhoProcessamento> buscarRegistroEmCurso(UUID professorId, UUID alunoId) {
        return jpa.registroEmCurso(professorId, alunoId).stream()
                .findFirst()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio);
    }

    @Override
    public List<TrabalhoProcessamento> listarDemoradosSemAviso(Instant criadosAntesDe) {
        return jpa.demoradosSemAviso(criadosAntesDe).stream()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio)
                .toList();
    }

    /** Registros de aula em transcrição do professor (relatórios em aberto ainda sem relatório). */
    List<TrabalhoProcessamento> registrosEmCursoDoProfessor(UUID professorId) {
        return jpa.registrosEmCursoDoProfessor(professorId).stream()
                .map(FilaTrabalhoPostgresAdaptador::paraDominio)
                .toList();
    }

    /**
     * Trabalhos que estavam em execução quando o backend caiu: voltam para a fila, exceto os que
     * dependiam de um arquivo do diretório temporário (apagado na reinicialização), que falham.
     */
    @Transactional
    public int recuperarTravados(Instant agora) {
        int recuperados = 0;
        for (TrabalhoProcessamentoEntidade entidade :
                jpa.findByStatus(StatusTrabalho.EM_EXECUCAO.name())) {
            TrabalhoProcessamento trabalho = paraDominio(entidade);
            if (trabalho.arquivoTemporario() != null) {
                trabalho.concluir(ResultadoTrabalho.AUDIO_NAO_PROCESSAVEL, agora);
                trabalho.definirArquivoTemporario(null);
            } else {
                trabalho.devolverParaFila();
            }
            jpa.save(paraEntidade(trabalho));
            recuperados++;
        }
        return recuperados;
    }

    static TrabalhoProcessamentoEntidade paraEntidade(TrabalhoProcessamento t) {
        TrabalhoProcessamentoEntidade e = new TrabalhoProcessamentoEntidade();
        e.id = t.id();
        e.tipo = t.tipo().name();
        e.professorId = t.professorId();
        e.alunoId = t.alunoId();
        e.relatorioTipo = t.relatorioTipo() == null ? null : t.relatorioTipo().name();
        e.relatorioId = t.relatorioId();
        e.canalSolicitacao = t.canalSolicitacao().name();
        e.meio = t.meio().name();
        e.parametros = JsonPersistencia.escrever(t.parametros());
        e.arquivoTemporario = t.arquivoTemporario();
        e.status = t.status().name();
        e.etapa = t.etapa().name();
        e.resultadoCodigo = t.resultado() == null ? null : t.resultado().name();
        e.relatorioResultanteId = t.relatorioResultanteId();
        e.tentativas = t.tentativas();
        e.avisoDemoraEnviado = t.avisoDemoraEnviado();
        e.criadoEm = t.criadoEm();
        e.iniciadoEm = t.iniciadoEm();
        e.concluidoEm = t.concluidoEm();
        return e;
    }

    static TrabalhoProcessamento paraDominio(TrabalhoProcessamentoEntidade e) {
        return new TrabalhoProcessamento(
                e.id,
                TipoTrabalho.valueOf(e.tipo),
                e.professorId,
                e.alunoId,
                e.relatorioTipo == null ? null : TipoRelatorio.valueOf(e.relatorioTipo),
                e.relatorioId,
                Canal.valueOf(e.canalSolicitacao),
                Meio.valueOf(e.meio),
                JsonPersistencia.ler(e.parametros, MAPA),
                e.arquivoTemporario,
                StatusTrabalho.valueOf(e.status),
                EtapaTrabalho.valueOf(e.etapa),
                e.resultadoCodigo == null ? null : ResultadoTrabalho.valueOf(e.resultadoCodigo),
                e.relatorioResultanteId,
                e.tentativas,
                e.avisoDemoraEnviado,
                e.criadoEm,
                e.iniciadoEm,
                e.concluidoEm);
    }
}
