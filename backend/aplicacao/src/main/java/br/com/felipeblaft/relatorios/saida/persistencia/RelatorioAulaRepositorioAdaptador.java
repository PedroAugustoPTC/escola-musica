package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class RelatorioAulaRepositorioAdaptador implements RelatorioAulaRepositorio {

    private final AulaJpa aulas;
    private final RelatorioAulaJpa relatorios;

    RelatorioAulaRepositorioAdaptador(AulaJpa aulas, RelatorioAulaJpa relatorios) {
        this.aulas = aulas;
        this.relatorios = relatorios;
    }

    @Override
    public void salvarAula(Aula aula) {
        aulas.save(RelatorioAulaMapeador.paraEntidade(aula));
    }

    @Override
    public Optional<Aula> buscarAula(UUID aulaId) {
        return aulas.findById(aulaId).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public void salvar(RelatorioAula relatorio) {
        try {
            RelatorioAulaEntidade salvo =
                    relatorios.saveAndFlush(RelatorioAulaMapeador.paraEntidade(relatorio));
            relatorio.registrarVersaoPersistida(salvo.bloqueioOtimista);
        } catch (ObjectOptimisticLockingFailureException
                | DataIntegrityViolationException conflito) {
            throw new ConflitoConcorrenciaExcecao("relatorio_aula");
        }
    }

    @Override
    public Optional<RelatorioAula> buscarPorId(UUID id) {
        return relatorios.findById(id).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public Optional<RelatorioAula> buscarParaAtualizar(UUID id) {
        return relatorios.buscarParaAtualizar(id).map(RelatorioAulaMapeador::paraDominio);
    }

    @Override
    public Optional<RelatorioAula> buscarEmAbertoDoAluno(UUID professorId, UUID alunoId) {
        return relatorios
                .buscarEmAberto(professorId, alunoId)
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
