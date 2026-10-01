package br.com.felipeblaft.relatorios.paridade;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Paridade de acesso, início, busca, bloqueio e encerramento (US2; quickstart V1, V2, V15). */
class RoteiroParidadeAcessoIT extends TelegramConversaBase {

    @Autowired CatalogoMensagens catalogo;

    @BeforeEach
    void carregar() {
        Professor clara = cenario.clara();
        Professor bruno = cenario.bruno();
        cenario.aluno(clara, "Matteo Mafra", "Piano");
        cenario.aluno(clara, "Olavo Torrente", "Violão");
        cenario.aluno(clara, "Miguel de Castro Torrente", "Violino");
        cenario.aluno(bruno, "Helena Prado", "Flauta");
    }

    private CanalTelegramDriver telegram() {
        return new CanalTelegramDriver(
                texto -> {
                    API_BOTS.resetRequests();
                    enviarTexto(USUARIO_CLARA, texto);
                    return chamadas("sendMessage");
                },
                catalogo,
                () -> jdbc.queryForObject("select count(*) from vinculo_telegram", Long.class));
    }

    private static RoteiroParidade roteiro(Runnable fimDoBloqueio) {
        return new RoteiroParidade()
                .passo("código errado", c -> c.entrar("ZZZZZZZZ"))
                .passo("código errado 2", c -> c.entrar("ZZZZZZZZ"))
                .passo("código errado 3", c -> c.entrar("ZZZZZZZZ"))
                .passo("código errado 4", c -> c.entrar("ZZZZZZZZ"))
                .passo("código errado 5", c -> c.entrar("ZZZZZZZZ"))
                .passo("código certo bloqueado", c -> c.entrar(CenarioIT.CODIGO_CLARA))
                .passo(
                        "fim do bloqueio",
                        c -> {
                            fimDoBloqueio.run();
                            return c.entrar(CenarioIT.CODIGO_CLARA);
                        })
                .passo("início", CanalDriver::inicio)
                .passo("busca com um", c -> c.buscar("matteo"))
                .passo("busca com vários", c -> c.buscar("torrente"))
                .passo("busca sem resultado", c -> c.buscar("Helena"))
                .passo("encerrar", CanalDriver::encerrar);
    }

    @Test
    void mesmoRoteiroMesmosResultadosNosDoisCanais() throws Exception {
        List<String> web =
                roteiro(() -> relogio.avancar(Duration.ofMinutes(15)))
                        .executar(new CanalWebDriver(mvc, "198.51.100.20"));
        relogio.definir(br.com.felipeblaft.relatorios.suporte.RelogioAjustavel.PADRAO);
        List<String> bot =
                roteiro(() -> relogio.avancar(Duration.ofMinutes(15))).executar(telegram());

        assertThat(bot).containsExactlyElementsOf(web);
        assertThat(web)
                .contains(
                        "código errado = CODIGO_INVALIDO",
                        "código certo bloqueado = BLOQUEADO_TEMPORARIAMENTE",
                        "fim do bloqueio = SUCESSO",
                        "início = ResumoInicio[totalAlunos=3, alunos=[Matteo Mafra, Miguel de Castro Torrente, Olavo Torrente]]",
                        "busca com um = ResultadoBusca[tipo=ENCONTRADOS, alunos=[Matteo Mafra]]",
                        "busca com vários = ResultadoBusca[tipo=ENCONTRADOS, alunos=[Miguel de Castro Torrente, Olavo Torrente]]",
                        "busca sem resultado = ResultadoBusca[tipo=NENHUM, alunos=[Matteo Mafra, Miguel de Castro Torrente, Olavo Torrente]]",
                        "encerrar = ENCERRADO");
    }
}
