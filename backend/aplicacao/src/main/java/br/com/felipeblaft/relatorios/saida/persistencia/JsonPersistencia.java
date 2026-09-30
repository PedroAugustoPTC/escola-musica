package br.com.felipeblaft.relatorios.saida.persistencia;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Serialização das colunas {@code jsonb}: o domínio não conhece o formato de armazenamento. */
final class JsonPersistencia {

    static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private JsonPersistencia() {}

    static String escrever(Object valor) {
        return JSON.writeValueAsString(valor);
    }

    static <T> T ler(String json, Class<T> tipo) {
        return JSON.readValue(json, tipo);
    }

    static <T> T ler(String json, TypeReference<T> tipo) {
        return JSON.readValue(json, tipo);
    }
}
