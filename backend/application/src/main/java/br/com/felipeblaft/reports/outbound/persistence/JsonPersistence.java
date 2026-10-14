package br.com.felipeblaft.reports.outbound.persistence;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Serialização das colunas {@code jsonb}: o domínio não conhece o formato de armazenamento. */
final class JsonPersistence {

    static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private JsonPersistence() {}

    static String write(Object value) {
        return JSON.writeValueAsString(value);
    }

    static <T> T read(String json, Class<T> type) {
        return JSON.readValue(json, type);
    }

    static <T> T read(String json, TypeReference<T> type) {
        return JSON.readValue(json, type);
    }
}
