package br.com.felipeblaft.reports.architecture;

import java.util.List;

/**
 * Regra do aviso de revisão do inventário (FR-071): mudar o que vai a um provedor (adaptadores de
 * IA ou prompts) sem tocar {@code privacy/processing-inventory.md} gera um aviso. O script {@code
 * infra/scripts/check-inventory-review.sh} aplica a mesma regra ao diff do PR; mantenha os dois
 * alinhados.
 */
final class InventoryReviewGuard {

    static final String INVENTORY = "privacy/processing-inventory.md";

    private static final List<String> PROVIDER_PATHS =
            List.of(
                    "backend/application/src/main/java/br/com/felipeblaft/reports/outbound/ai/",
                    "backend/application/src/main/resources/prompts/");

    private InventoryReviewGuard() {}

    static List<String> check(List<String> changedPaths) {
        if (changedPaths.contains(INVENTORY)) {
            return List.of();
        }
        List<String> touched =
                changedPaths.stream()
                        .filter(path -> PROVIDER_PATHS.stream().anyMatch(path::startsWith))
                        .toList();
        if (touched.isEmpty()) {
            return List.of();
        }
        return List.of(
                "Mudou o que vai a um provedor (%d arquivo(s)) sem revisar %s."
                        .formatted(touched.size(), INVENTORY));
    }
}
