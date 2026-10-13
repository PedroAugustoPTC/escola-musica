package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class DocumentoCanalRepositorioAdaptador implements ChannelDocumentRepository {

    private final DocumentoCanalJpa jpa;

    DocumentoCanalRepositorioAdaptador(DocumentoCanalJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void emit(ChannelDocument documento) {
        DocumentoCanalEntidade e = new DocumentoCanalEntidade();
        e.tokenHash = documento.tokenHash();
        e.professorId = documento.teacherId();
        e.relatorioTipo = documento.reportType().name();
        e.relatorioId = documento.reportId();
        e.versao = documento.version();
        e.nomeArquivo = documento.fileName();
        e.criadoEm = documento.createdAt();
        e.expiraEm = documento.expiresAt();
        jpa.saveAndFlush(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChannelDocument> searchByHash(String tokenHash) {
        return jpa.findById(tokenHash)
                .map(
                        e ->
                                new ChannelDocument(
                                        e.tokenHash,
                                        e.professorId,
                                        ReportType.valueOf(e.relatorioTipo),
                                        e.relatorioId,
                                        e.versao,
                                        e.nomeArquivo,
                                        e.criadoEm,
                                        e.expiraEm));
    }

    @Override
    @Transactional
    public int purgeExpired(Instant agora) {
        return jpa.removerVencidos(agora);
    }
}
