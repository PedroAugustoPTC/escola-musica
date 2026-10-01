package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Relatórios semestrais (Informações Gerais e seções 2 a 10 em jsonb). */
@Repository
class RelatorioSemestralRepositorioAdaptador implements RelatorioSemestralRepositorio {

    private final RelatorioSemestralJpa jpa;

    RelatorioSemestralRepositorioAdaptador(RelatorioSemestralJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(RelatorioSemestral relatorio) {
        try {
            RelatorioSemestralEntidade salvo = jpa.saveAndFlush(paraEntidade(relatorio));
            relatorio.registrarVersaoPersistida(salvo.bloqueioOtimista);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflito) {
            throw new ConflitoConcorrenciaExcecao("relatorio_semestral");
        }
    }

    @Override
    public Optional<RelatorioSemestral> buscarPorId(UUID id) {
        return jpa.findById(id).map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public Optional<RelatorioSemestral> buscarParaAtualizar(UUID id) {
        return jpa.buscarParaAtualizar(id).map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public Optional<RelatorioSemestral> buscarAprovado(
            UUID professorId, UUID alunoId, String periodoChave) {
        return jpa.doTrio(professorId, alunoId, periodoChave, StatusRelatorio.APROVADO.name())
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public Optional<RelatorioSemestral> buscarEmAberto(
            UUID professorId, UUID alunoId, String periodoChave) {
        return jpa.doTrio(
                        professorId,
                        alunoId,
                        periodoChave,
                        StatusRelatorio.AGUARDANDO_CONFERENCIA.name())
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio);
    }

    @Override
    public List<RelatorioSemestral> listarAprovados(UUID professorId, UUID alunoId) {
        return jpa.aprovados(professorId, alunoId).stream()
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio)
                .toList();
    }

    List<RelatorioSemestral> emAbertoDoProfessor(UUID professorId) {
        return jpa.emAbertoDoProfessor(professorId).stream()
                .map(RelatorioSemestralRepositorioAdaptador::paraDominio)
                .toList();
    }

    static RelatorioSemestralEntidade paraEntidade(RelatorioSemestral r) {
        RelatorioSemestralEntidade e = new RelatorioSemestralEntidade();
        e.id = r.id();
        e.professorId = r.professorId();
        e.alunoId = r.alunoId();
        e.periodoChave = r.periodo().chave();
        e.periodoInicio = r.periodo().inicio();
        e.periodoFim = r.periodo().fim();
        e.quantidadeAulasConsideradas = r.quantidadeAulasConsideradas();
        e.quantidadeRelatoriosEmAberto = r.quantidadeRelatoriosEmAberto();
        e.informacoesGerais = JsonPersistencia.escrever(r.informacoesGerais());
        e.conteudo = JsonPersistencia.escrever(r.conteudo());
        e.status = r.status().name();
        e.etapaProcessamento = r.etapa() == null ? null : r.etapa().name();
        e.versaoVigente = r.versaoVigente();
        e.canalOrigem = r.canalOrigem().name();
        e.criadoEm = r.criadoEm();
        e.atualizadoEm = r.atualizadoEm();
        e.aprovadoEm = r.aprovadoEm();
        e.versaoAprovada = r.versaoAprovada();
        e.canalAprovacao = r.canalAprovacao() == null ? null : r.canalAprovacao().name();
        e.canceladoEm = r.canceladoEm();
        e.tempoProcessamentoMs = r.tempoProcessamentoMs();
        e.bloqueioOtimista = r.versao();
        return e;
    }

    static RelatorioSemestral paraDominio(RelatorioSemestralEntidade e) {
        return new RelatorioSemestral(
                e.id,
                e.professorId,
                e.alunoId,
                PeriodoSemestral.de(e.periodoChave),
                e.quantidadeAulasConsideradas,
                e.quantidadeRelatoriosEmAberto,
                JsonPersistencia.ler(e.informacoesGerais, InformacoesGerais.class),
                JsonPersistencia.ler(e.conteudo, ConteudoRelatorioSemestral.class),
                StatusRelatorio.valueOf(e.status),
                e.etapaProcessamento == null ? null : EtapaRelatorio.valueOf(e.etapaProcessamento),
                e.versaoVigente,
                Canal.valueOf(e.canalOrigem),
                e.criadoEm,
                e.atualizadoEm,
                e.aprovadoEm,
                e.versaoAprovada,
                e.canalAprovacao == null ? null : Canal.valueOf(e.canalAprovacao),
                e.canceladoEm,
                e.tempoProcessamentoMs,
                e.bloqueioOtimista);
    }
}
