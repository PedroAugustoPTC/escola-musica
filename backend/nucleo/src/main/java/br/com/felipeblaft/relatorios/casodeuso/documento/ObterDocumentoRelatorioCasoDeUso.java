package br.com.felipeblaft.relatorios.casodeuso.documento;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Entrega autorizada de PDFs (FR-048, R-11): primeiro verifica o dono e a associação, depois lê os
 * bytes pela chave interna. O nome sugerido não revela identificadores internos.
 */
public class ObterDocumentoRelatorioCasoDeUso {

    public record DocumentoObtido(byte[] conteudo, String nomeArquivo) {}

    /** Fonte dos documentos de outros tipos de relatório (ex.: semestral). */
    public interface FonteDocumentos {
        TipoRelatorio tipo();

        DocumentoObtido conferencia(UUID professorId, UUID relatorioId, int numero);

        DocumentoObtido oficial(UUID professorId, UUID relatorioId);
    }

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final VersaoRelatorioRepositorio versoes;
    private final DocumentoPdfRepositorio documentos;
    private final GeracaoDocumentoServico geracao;
    private FonteDocumentos semestral;

    public ObterDocumentoRelatorioCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            VersaoRelatorioRepositorio versoes,
            DocumentoPdfRepositorio documentos,
            GeracaoDocumentoServico geracao) {
        this.acesso = Objects.requireNonNull(acesso);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.versoes = Objects.requireNonNull(versoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.geracao = Objects.requireNonNull(geracao);
    }

    public void registrarFonteSemestral(FonteDocumentos fonte) {
        this.semestral = fonte;
    }

    public DocumentoObtido conferencia(
            UUID professorId, TipoRelatorio tipo, UUID relatorioId, int numero) {
        if (tipo == TipoRelatorio.SEMESTRAL) {
            return fonteSemestral().conferencia(professorId, relatorioId, numero);
        }
        RelatorioAula relatorio = acesso.relatorioAula(professorId, relatorioId);
        String nome = nomeAula(relatorio, "-v" + numero);
        return ler(relatorio.id(), numero, TipoDocumento.CONFERENCIA, nome);
    }

    public DocumentoObtido oficial(UUID professorId, TipoRelatorio tipo, UUID relatorioId) {
        if (tipo == TipoRelatorio.SEMESTRAL) {
            return fonteSemestral().oficial(professorId, relatorioId);
        }
        RelatorioAula relatorio = acesso.relatorioAula(professorId, relatorioId);
        if (relatorio.status() != StatusRelatorio.APROVADO) {
            throw naoEncontrado();
        }
        return ler(
                relatorio.id(),
                relatorio.versaoAprovada(),
                TipoDocumento.OFICIAL,
                nomeAula(relatorio, ""));
    }

    private String nomeAula(RelatorioAula relatorio, String sufixo) {
        LocalDate data = relatorios.buscarAula(relatorio.aulaId()).orElseThrow().dataAula();
        return "relatorio-aula-" + data + sufixo + ".pdf";
    }

    private DocumentoObtido ler(UUID relatorioId, int numero, TipoDocumento tipo, String nome) {
        Optional<DocumentoPdf> documento =
                versoes.buscar(TipoRelatorio.AULA, relatorioId, numero)
                        .flatMap(v -> documentos.buscar(v.id(), tipo));
        byte[] bytes =
                documento
                        .flatMap(geracao::abrir)
                        .orElseThrow(ObterDocumentoRelatorioCasoDeUso::naoEncontrado);
        return new DocumentoObtido(bytes, nome);
    }

    private FonteDocumentos fonteSemestral() {
        if (semestral == null) {
            throw naoEncontrado();
        }
        return semestral;
    }

    private static ExcecaoDominio naoEncontrado() {
        return new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO);
    }
}
