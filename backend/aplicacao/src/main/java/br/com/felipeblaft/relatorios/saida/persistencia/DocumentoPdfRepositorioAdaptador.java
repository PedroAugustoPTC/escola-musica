package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class DocumentoPdfRepositorioAdaptador implements DocumentoPdfRepositorio {

    private final DocumentoPdfJpa jpa;

    DocumentoPdfRepositorioAdaptador(DocumentoPdfJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(DocumentoPdf documento) {
        jpa.saveAndFlush(RelatorioAulaMapeador.paraEntidade(documento));
    }

    @Override
    public Optional<DocumentoPdf> buscar(UUID versaoId, TipoDocumento tipo) {
        return jpa.findByVersaoIdAndTipo(versaoId, tipo.name())
                .map(RelatorioAulaMapeador::paraDominio);
    }
}
