package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.acesso.TentativaAcesso;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.TentativaAcessoRepositorio;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bloqueio por tentativas incorretas, igual nos dois canais (FR-007): a origem é a conta do
 * Telegram ou o IP da Web, guardada só como hash. O bloqueio é verificado antes do código.
 */
public class ControleTentativasAcessoServico {

    private final TentativaAcessoRepositorio tentativas;
    private final ProtecaoDadosPorta protecao;
    private final RelogioPorta relogio;
    private final ParametrosNegocio parametros;

    public ControleTentativasAcessoServico(
            TentativaAcessoRepositorio tentativas,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            ParametrosNegocio parametros) {
        this.tentativas = Objects.requireNonNull(tentativas);
        this.protecao = Objects.requireNonNull(protecao);
        this.relogio = Objects.requireNonNull(relogio);
        this.parametros = Objects.requireNonNull(parametros);
    }

    /**
     * Recusa com {@code BLOQUEADO_TEMPORARIAMENTE} (e o tempo restante) se a origem está bloqueada.
     */
    public void verificarBloqueio(Canal canal, String origem) {
        Instant agora = relogio.agora();
        Optional<TentativaAcesso> registro = tentativas.buscar(canal, hash(canal, origem));
        if (registro.isEmpty()) {
            return;
        }
        TentativaAcesso tentativa = registro.get();
        Optional<Duration> restante = tentativa.bloqueioRestante(agora);
        if (restante.isPresent()) {
            long segundos = Math.max(1, (restante.get().toMillis() + 999) / 1000);
            throw new ExcecaoDominio(
                    CodigoErro.BLOQUEADO_TEMPORARIAMENTE, Map.of("segundosRestantes", segundos));
        }
        if (tentativa.bloqueioEncerrado(agora)) {
            tentativa.zerar(agora);
            tentativas.salvar(tentativa);
        }
    }

    public void registrarFalha(Canal canal, String origem) {
        Instant agora = relogio.agora();
        String hash = hash(canal, origem);
        TentativaAcesso tentativa =
                tentativas
                        .buscar(canal, hash)
                        .orElseGet(() -> TentativaAcesso.nova(canal, hash, agora));
        tentativa.registrarFalha(
                agora, parametros.maximoTentativasAcesso(), parametros.duracaoBloqueio());
        tentativas.salvar(tentativa);
    }

    public void registrarSucesso(Canal canal, String origem) {
        Instant agora = relogio.agora();
        tentativas
                .buscar(canal, hash(canal, origem))
                .filter(t -> t.tentativasIncorretas() > 0 || t.bloqueadoAte() != null)
                .ifPresent(
                        tentativa -> {
                            tentativa.zerar(agora);
                            tentativas.salvar(tentativa);
                        });
    }

    private String hash(Canal canal, String origem) {
        return protecao.hashOrigem(canal, origem == null ? "" : origem);
    }
}
