package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso.DocumentoObtido;
import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Objects;
import java.util.Optional;

/**
 * Entrega os bytes do PDF por identificador opaco (R-08). Identificador desconhecido, vencido ou
 * cujo documento sumiu resultam todos em "não encontrado", indistinguíveis. A checagem de dono e de
 * associação é repetida na leitura, pelo mesmo caso de uso do portal. Falha real de leitura propaga
 * (nunca bytes parciais).
 */
public class ObterDocumentoCanalCasoDeUso {

    private final DocumentoCanalRepositorio documentos;
    private final ObterDocumentoRelatorioCasoDeUso obterDocumento;
    private final RelogioPorta relogio;

    public ObterDocumentoCanalCasoDeUso(
            DocumentoCanalRepositorio documentos,
            ObterDocumentoRelatorioCasoDeUso obterDocumento,
            RelogioPorta relogio) {
        this.documentos = Objects.requireNonNull(documentos);
        this.obterDocumento = Objects.requireNonNull(obterDocumento);
        this.relogio = Objects.requireNonNull(relogio);
    }

    /**
     * @param token o identificador opaco recebido; só o seu hash é consultado
     */
    public Optional<DocumentoObtido> executar(String token) {
        Optional<DocumentoCanal> referencia =
                documentos
                        .buscarPorHash(DocumentoCanal.hashDoToken(token))
                        .filter(documento -> documento.valido(relogio.agora()));
        if (referencia.isEmpty()) {
            return Optional.empty();
        }
        DocumentoCanal documento = referencia.get();
        try {
            return Optional.of(
                    documento.oficial()
                            ? obterDocumento.oficial(
                                    documento.professorId(),
                                    documento.relatorioTipo(),
                                    documento.relatorioId())
                            : obterDocumento.conferencia(
                                    documento.professorId(),
                                    documento.relatorioTipo(),
                                    documento.relatorioId(),
                                    documento.versao()));
        } catch (ExcecaoDominio semAcesso) {
            // relatório sumiu, deixou de existir a associação ou não está mais aprovado: nenhum
            // motivo é distinguível de "identificador desconhecido" (FR-022)
            return Optional.empty();
        }
    }
}
