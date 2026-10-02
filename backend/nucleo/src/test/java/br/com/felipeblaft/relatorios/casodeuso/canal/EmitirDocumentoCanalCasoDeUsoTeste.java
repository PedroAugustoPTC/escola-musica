package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dubles.DocumentoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmitirDocumentoCanalCasoDeUsoTeste {

    private final RelogioFixo relogio = new RelogioFixo();
    private final DocumentoCanalRepositorioEmMemoria repositorio =
            new DocumentoCanalRepositorioEmMemoria();
    private final EmitirDocumentoCanalCasoDeUso emitir =
            new EmitirDocumentoCanalCasoDeUso(
                    repositorio,
                    relogio,
                    new PoliticaRetentativa(
                            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)));

    @Test
    void tokenTem43CaracteresBase64urlEGravaSoOHash() {
        UUID professor = UUID.randomUUID();
        UUID relatorio = UUID.randomUUID();
        String token = emitir.executar(professor, TipoRelatorio.AULA, relatorio, 2, "r-v2.pdf");

        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(repositorio.dados).doesNotContainKey(token);
        DocumentoCanal gravado = repositorio.buscarPorHash(DocumentoCanal.hashDoToken(token)).orElseThrow();
        assertThat(gravado.tokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(gravado.professorId()).isEqualTo(professor);
        assertThat(gravado.relatorioId()).isEqualTo(relatorio);
        assertThat(gravado.relatorioTipo()).isEqualTo(TipoRelatorio.AULA);
        assertThat(gravado.versao()).isEqualTo(2);
        assertThat(gravado.nomeArquivo()).isEqualTo("r-v2.pdf");
    }

    @Test
    void validadeEPrazoDeEntregaMaisUmaHora() {
        String token = emitir.executar(UUID.randomUUID(), TipoRelatorio.SEMESTRAL, UUID.randomUUID(), null, "s.pdf");
        DocumentoCanal gravado = repositorio.buscarPorHash(DocumentoCanal.hashDoToken(token)).orElseThrow();
        assertThat(gravado.oficial()).isTrue();
        assertThat(gravado.expiraEm()).isEqualTo(relogio.agora().plus(Duration.ofHours(25)));
        assertThat(gravado.valido(relogio.agora().plus(Duration.ofHours(24)))).isTrue();
        assertThat(gravado.valido(relogio.agora().plus(Duration.ofHours(25)))).isFalse();
    }

    @Test
    void tokensSaoUnicos() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(emitir.executar(UUID.randomUUID(), TipoRelatorio.AULA, UUID.randomUUID(), null, "x.pdf"));
        }
        assertThat(tokens).hasSize(200);
    }

    @Test
    void hashDoTokenEHexSha256() {
        assertThat(DocumentoCanal.hashDoToken("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
