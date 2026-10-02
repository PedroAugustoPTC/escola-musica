package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class DocumentoCanalRepositorioEmMemoria implements DocumentoCanalRepositorio {

    public final Map<String, DocumentoCanal> dados = new LinkedHashMap<>();

    @Override
    public void emitir(DocumentoCanal documento) {
        dados.put(documento.tokenHash(), documento);
    }

    @Override
    public Optional<DocumentoCanal> buscarPorHash(String tokenHash) {
        return Optional.ofNullable(dados.get(tokenHash));
    }

    @Override
    public int expurgarVencidos(Instant agora) {
        int antes = dados.size();
        dados.values().removeIf(d -> !d.valido(agora));
        return antes - dados.size();
    }
}
