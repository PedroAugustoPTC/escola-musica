package br.com.felipeblaft.reports.domain.privacy;

/**
 * Consentimento como chega de qualquer canal: textos brutos, validados no núcleo. O mesmo esquema
 * na Web e no Telegram. O {@code toString} não revela o nome de quem consentiu.
 *
 * @param consentedOn {@code aaaa-mm-dd} (Web) ou {@code dd/mm/aaaa} (Telegram)
 * @param termVersion número da versão do termo
 */
public record ConsentInput(
        String consentingName,
        String relationship,
        String consentedOn,
        Integer termVersion,
        String collectionMethod) {

    @Override
    public String toString() {
        return "ConsentInput";
    }
}
