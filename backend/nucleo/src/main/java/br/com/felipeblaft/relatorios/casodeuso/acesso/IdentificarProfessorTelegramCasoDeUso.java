package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import br.com.felipeblaft.relatorios.servico.RenovacaoAcessoServico;
import java.util.Objects;
import java.util.Optional;

/**
 * Identifica o professor pela conta do Telegram (FR-010): o vínculo só identifica; cada ação é
 * autorizada no seu caso de uso. Professor inativo perde o acesso e o vínculo é desfeito. Cada uso
 * renova a validade do código (FR-005).
 */
public class IdentificarProfessorTelegramCasoDeUso {

    public record Identificacao(Professor professor, VinculoTelegram vinculo) {}

    private final VinculoTelegramRepositorio vinculos;
    private final ProfessorRepositorio professores;
    private final RenovacaoAcessoServico renovacao;

    public IdentificarProfessorTelegramCasoDeUso(
            VinculoTelegramRepositorio vinculos,
            ProfessorRepositorio professores,
            RenovacaoAcessoServico renovacao) {
        this.vinculos = Objects.requireNonNull(vinculos);
        this.professores = Objects.requireNonNull(professores);
        this.renovacao = Objects.requireNonNull(renovacao);
    }

    public Optional<Identificacao> executar(long telegramUsuarioId) {
        Optional<VinculoTelegram> vinculo = vinculos.buscarPorTelegramUsuarioId(telegramUsuarioId);
        if (vinculo.isEmpty()) {
            return Optional.empty();
        }
        Optional<Professor> professor =
                professores.buscarPorId(vinculo.get().professorId()).filter(Professor::ativo);
        if (professor.isEmpty()) {
            vinculos.remover(vinculo.get().id());
            return Optional.empty();
        }
        renovacao.renovar(professor.get());
        return Optional.of(new Identificacao(professor.get(), vinculo.get()));
    }
}
