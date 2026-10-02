package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.DocumentoObtido;
import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dubles.CenarioRelatorio;
import br.com.felipeblaft.relatorios.dubles.DocumentoCanalRepositorioEmMemoria;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObterDocumentoCanalCasoDeUsoTeste {

    private final CenarioRelatorio cenario = new CenarioRelatorio();
    private final DocumentoCanalRepositorioEmMemoria repositorio =
            new DocumentoCanalRepositorioEmMemoria();
    private final List<String> chamadas = new ArrayList<>();
    private RuntimeException falha;

    private final ObterDocumentoRelatorioCasoDeUso portal =
            new ObterDocumentoRelatorioCasoDeUso(
                    cenario.acesso,
                    cenario.relatorios,
                    cenario.versoes,
                    cenario.documentosPdf,
                    cenario.documentos) {
                @Override
                public DocumentoObtido conferencia(
                        UUID professorId, TipoRelatorio tipo, UUID relatorioId, int numero) {
                    chamadas.add("conferencia:" + professorId + ":" + numero);
                    if (falha != null) {
                        throw falha;
                    }
                    return new DocumentoObtido(new byte[] {1, 2}, "conferencia.pdf");
                }

                @Override
                public DocumentoObtido oficial(
                        UUID professorId, TipoRelatorio tipo, UUID relatorioId) {
                    chamadas.add("oficial:" + professorId);
                    if (falha != null) {
                        throw falha;
                    }
                    return new DocumentoObtido(new byte[] {9}, "oficial.pdf");
                }
            };

    private final ObterDocumentoCanalCasoDeUso obter =
            new ObterDocumentoCanalCasoDeUso(repositorio, portal, cenario.relogio);
    private final EmitirDocumentoCanalCasoDeUso emitir =
            new EmitirDocumentoCanalCasoDeUso(
                    repositorio,
                    cenario.relogio,
                    new PoliticaRetentativa(
                            Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24)));

    @Test
    void oficialSaiPeloMesmoCasoDeUsoDoPortalComODonoGravado() {
        UUID professor = UUID.randomUUID();
        String token =
                emitir.executar(professor, TipoRelatorio.AULA, UUID.randomUUID(), null, "o.pdf");
        DocumentoObtido obtido = obter.executar(token).orElseThrow();
        assertThat(obtido.conteudo()).containsExactly(9);
        assertThat(chamadas).containsExactly("oficial:" + professor);
    }

    @Test
    void conferenciaUsaONumeroDaVersao() {
        UUID professor = UUID.randomUUID();
        String token =
                emitir.executar(professor, TipoRelatorio.AULA, UUID.randomUUID(), 3, "c.pdf");
        assertThat(obter.executar(token).orElseThrow().nomeArquivo()).isEqualTo("conferencia.pdf");
        assertThat(chamadas).containsExactly("conferencia:" + professor + ":3");
    }

    @Test
    void repeticaoDentroDoPrazoContinuaValida() {
        String token =
                emitir.executar(
                        UUID.randomUUID(), TipoRelatorio.AULA, UUID.randomUUID(), null, "o.pdf");
        assertThat(obter.executar(token)).isPresent();
        cenario.relogio.avancar(Duration.ofHours(24));
        assertThat(obter.executar(token)).isPresent();
    }

    @Test
    void desconhecidoEVencidoSaoNaoEncontradoSemChamarOPortal() {
        assertThat(obter.executar("a".repeat(43))).isEmpty();
        String token =
                emitir.executar(
                        UUID.randomUUID(), TipoRelatorio.AULA, UUID.randomUUID(), null, "o.pdf");
        cenario.relogio.avancar(Duration.ofHours(25));
        assertThat(obter.executar(token)).isEmpty();
        assertThat(chamadas).isEmpty();
    }

    @Test
    void documentoQueSumiuOuSemAssociacaoViraNaoEncontradoIndistinguivel() {
        String token =
                emitir.executar(
                        UUID.randomUUID(), TipoRelatorio.AULA, UUID.randomUUID(), null, "o.pdf");
        for (CodigoErro codigo :
                List.of(
                        CodigoErro.RECURSO_NAO_ENCONTRADO,
                        CodigoErro.ALUNO_NAO_ASSOCIADO,
                        CodigoErro.DADOS_INVALIDOS)) {
            falha = new ExcecaoDominio(codigo);
            assertThat(obter.executar(token)).as(codigo.name()).isEmpty();
        }
    }

    @Test
    void falhaRealDeLeituraPropagaNuncaBytesParciais() {
        String token =
                emitir.executar(
                        UUID.randomUUID(), TipoRelatorio.AULA, UUID.randomUUID(), null, "o.pdf");
        falha = new IllegalStateException("disco");
        assertThatThrownBy(() -> obter.executar(token)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soOHashDoTokenEConsultado() {
        String token =
                emitir.executar(
                        UUID.randomUUID(), TipoRelatorio.AULA, UUID.randomUUID(), null, "o.pdf");
        assertThat(repositorio.dados).containsKey(DocumentoCanal.hashDoToken(token));
        assertThat(repositorio.dados).doesNotContainKey(token);
    }
}
