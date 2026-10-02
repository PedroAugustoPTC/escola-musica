package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class DocumentoCanalRepositorioAdaptador implements DocumentoCanalRepositorio {

    private final DocumentoCanalJpa jpa;

    DocumentoCanalRepositorioAdaptador(DocumentoCanalJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void emitir(DocumentoCanal documento) {
        DocumentoCanalEntidade e = new DocumentoCanalEntidade();
        e.tokenHash = documento.tokenHash();
        e.professorId = documento.professorId();
        e.relatorioTipo = documento.relatorioTipo().name();
        e.relatorioId = documento.relatorioId();
        e.versao = documento.versao();
        e.nomeArquivo = documento.nomeArquivo();
        e.criadoEm = documento.criadoEm();
        e.expiraEm = documento.expiraEm();
        jpa.saveAndFlush(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentoCanal> buscarPorHash(String tokenHash) {
        return jpa.findById(tokenHash)
                .map(
                        e ->
                                new DocumentoCanal(
                                        e.tokenHash,
                                        e.professorId,
                                        TipoRelatorio.valueOf(e.relatorioTipo),
                                        e.relatorioId,
                                        e.versao,
                                        e.nomeArquivo,
                                        e.criadoEm,
                                        e.expiraEm));
    }

    @Override
    @Transactional
    public int expurgarVencidos(Instant agora) {
        return jpa.removerVencidos(agora);
    }
}
