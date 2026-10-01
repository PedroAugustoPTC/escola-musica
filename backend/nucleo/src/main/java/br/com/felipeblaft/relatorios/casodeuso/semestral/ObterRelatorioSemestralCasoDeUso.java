package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.DocumentoObtido;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.FonteDocumentos;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.AcessoSemestralServico;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado e conteúdo vigente do semestral, iguais nos dois canais (FR-002), e a fonte dos PDFs do
 * semestral para {@code ObterDocumentoRelatorioCasoDeUso} (sempre depois de conferir o dono).
 */
public class ObterRelatorioSemestralCasoDeUso implements FonteDocumentos {

    public record VisaoRelatorioSemestral(
            RelatorioSemestral relatorio,
            Optional<InteracaoRelatorio> ultimaInteracao,
            Optional<TrabalhoProcessamento> trabalhoEmCurso,
            boolean pdfDisponivel) {}

    private final AcessoSemestralServico acesso;
    private final InteracaoRelatorioRepositorio interacoes;
    private final VersaoRelatorioRepositorio versoes;
    private final DocumentoPdfRepositorio documentos;
    private final GeracaoDocumentoServico geracao;
    private final FilaTrabalhoPorta fila;

    public ObterRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            InteracaoRelatorioRepositorio interacoes,
            VersaoRelatorioRepositorio versoes,
            DocumentoPdfRepositorio documentos,
            GeracaoDocumentoServico geracao,
            FilaTrabalhoPorta fila) {
        this.acesso = Objects.requireNonNull(acesso);
        this.interacoes = Objects.requireNonNull(interacoes);
        this.versoes = Objects.requireNonNull(versoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.geracao = Objects.requireNonNull(geracao);
        this.fila = Objects.requireNonNull(fila);
    }

    public VisaoRelatorioSemestral executar(UUID professorId, UUID relatorioId) {
        RelatorioSemestral relatorio = acesso.relatorio(professorId, relatorioId);
        boolean pdf =
                documento(relatorio.id(), relatorio.versaoVigente(), TipoDocumento.CONFERENCIA)
                        .isPresent();
        return new VisaoRelatorioSemestral(
                relatorio,
                interacoes.ultima(TipoRelatorio.SEMESTRAL, relatorio.id()),
                fila.buscarEmCursoDoRelatorio(TipoRelatorio.SEMESTRAL, relatorio.id()),
                pdf);
    }

    @Override
    public TipoRelatorio tipo() {
        return TipoRelatorio.SEMESTRAL;
    }

    @Override
    public DocumentoObtido conferencia(UUID professorId, UUID relatorioId, int numero) {
        RelatorioSemestral relatorio = acesso.relatorio(professorId, relatorioId);
        return ler(
                relatorio.id(), numero, TipoDocumento.CONFERENCIA, nome(relatorio, "-v" + numero));
    }

    @Override
    public DocumentoObtido oficial(UUID professorId, UUID relatorioId) {
        RelatorioSemestral relatorio = acesso.relatorio(professorId, relatorioId);
        if (relatorio.status() != StatusRelatorio.APROVADO) {
            throw naoEncontrado();
        }
        return ler(
                relatorio.id(),
                relatorio.versaoAprovada(),
                TipoDocumento.OFICIAL,
                nome(relatorio, ""));
    }

    private static String nome(RelatorioSemestral relatorio, String sufixo) {
        return "relatorio-semestral-" + relatorio.periodo().chave() + sufixo + ".pdf";
    }

    private Optional<DocumentoPdf> documento(UUID relatorioId, int numero, TipoDocumento tipo) {
        return versoes.buscar(TipoRelatorio.SEMESTRAL, relatorioId, numero)
                .flatMap(v -> documentos.buscar(v.id(), tipo));
    }

    private DocumentoObtido ler(UUID relatorioId, int numero, TipoDocumento tipo, String nome) {
        byte[] bytes =
                documento(relatorioId, numero, tipo)
                        .flatMap(geracao::abrir)
                        .orElseThrow(ObterRelatorioSemestralCasoDeUso::naoEncontrado);
        return new DocumentoObtido(bytes, nome);
    }

    private static ExcecaoDominio naoEncontrado() {
        return new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO);
    }
}
