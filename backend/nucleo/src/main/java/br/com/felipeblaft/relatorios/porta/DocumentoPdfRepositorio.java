package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoPdfRepositorio {

    void salvar(DocumentoPdf documento);

    Optional<DocumentoPdf> buscar(UUID versaoId, TipoDocumento tipo);
}
