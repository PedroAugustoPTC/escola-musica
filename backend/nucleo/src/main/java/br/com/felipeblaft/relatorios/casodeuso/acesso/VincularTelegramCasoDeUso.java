package br.com.felipeblaft.relatorios.casodeuso.acesso;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import br.com.felipeblaft.relatorios.servico.AutenticacaoCodigoServico;
import br.com.felipeblaft.relatorios.servico.RenovacaoAcessoServico;
import java.util.Objects;

/**
 * Vincula a conta do Telegram enviando o mesmo código da Web (FR-004, FR-006). Uma conta só se
 * vincula a um professor e um professor a uma conta. O código não é consumido.
 */
public class VincularTelegramCasoDeUso {

    public record Resultado(Professor professor, VinculoTelegram vinculo) {}

    private final AutenticacaoCodigoServico autenticacao;
    private final RenovacaoAcessoServico renovacao;
    private final VinculoTelegramRepositorio vinculos;
    private final RelogioPorta relogio;

    public VincularTelegramCasoDeUso(
            AutenticacaoCodigoServico autenticacao,
            RenovacaoAcessoServico renovacao,
            VinculoTelegramRepositorio vinculos,
            RelogioPorta relogio) {
        this.autenticacao = Objects.requireNonNull(autenticacao);
        this.renovacao = Objects.requireNonNull(renovacao);
        this.vinculos = Objects.requireNonNull(vinculos);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public Resultado executar(long telegramUsuarioId, long chatId, String codigo) {
        Professor professor =
                autenticacao.autenticar(Canal.TELEGRAM, Long.toString(telegramUsuarioId), codigo);
        if (vinculos.buscarPorTelegramUsuarioId(telegramUsuarioId).isPresent()) {
            throw new ExcecaoDominio(CodigoErro.TELEGRAM_JA_VINCULADO);
        }
        if (vinculos.buscarPorProfessorId(professor.id()).isPresent()) {
            throw new ExcecaoDominio(CodigoErro.PROFESSOR_JA_VINCULADO);
        }
        VinculoTelegram vinculo =
                VinculoTelegram.novo(professor.id(), telegramUsuarioId, chatId, relogio.agora());
        vinculos.salvar(vinculo);
        renovacao.renovar(professor);
        return new Resultado(professor, vinculo);
    }
}
