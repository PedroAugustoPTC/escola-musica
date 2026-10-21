package br.com.felipeblaft.reports.support;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Varredor de vazamento (SC-004, R-25): procura um conjunto de valores canários em corpo,
 * cabeçalhos e URL de uma resposta, e em qualquer outro texto (log, mensagem de erro, rastro de
 * exceção, autoria, ação de saída), respeitando a lista de {@linkplain Place lugares} onde o valor
 * é permitido (a resposta ao ator autorizado e o cartão do bot; nunca log, erro, autoria, {@code
 * channel_action.payload} fora do cartão, rastro de exceção ou URL).
 *
 * <p>A busca ignora caixa e acento, entende a codificação de URL e o escape de caractere Unicode do
 * JSON e, para valores numéricos com máscara (CPF, telefone, data), acha o número com qualquer
 * separador entre os dígitos. A mensagem de falha cita o rótulo e o lugar, nunca o valor.
 */
public final class ResponseSweeper {

    /** Onde um texto varrido foi encontrado. */
    public enum Place {
        BODY,
        HEADERS,
        URL,
        LOG,
        ERROR_MESSAGE,
        EXCEPTION_TRACE,
        AUTHORSHIP,
        ACTION_PAYLOAD,
        BOT_CARD
    }

    /** Um valor canário achado em lugar não permitido; guarda o rótulo, nunca o valor. */
    public record Finding(String label, Place place) {

        @Override
        public String toString() {
            return label + " em " + place;
        }
    }

    private static final Pattern NUMERIC_VALUE = Pattern.compile("[\\d\\s().\\-/+]+");
    private static final Pattern SEPARATOR_BETWEEN_DIGITS =
            Pattern.compile("(?<=\\d)[\\s().\\-/]+(?=\\d)");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private static final int MIN_NUMERIC_DIGITS = 8;

    private final Map<String, String> needles;
    private final Set<Place> allowed;

    private ResponseSweeper(Map<String, String> needles, Set<Place> allowed) {
        this.needles = needles;
        this.allowed = allowed;
    }

    /** Varredor para os valores (rótulo para valor); nenhum lugar é permitido. */
    public static ResponseSweeper searching(Map<String, String> values) {
        Map<String, String> canonical = new LinkedHashMap<>();
        values.forEach((label, value) -> canonical.put(label, canonicalNeedle(value)));
        return new ResponseSweeper(canonical, EnumSet.noneOf(Place.class));
    }

    /** Novo varredor que também permite os valores nos lugares informados. */
    public ResponseSweeper allowing(Place... places) {
        Set<Place> more = EnumSet.noneOf(Place.class);
        more.addAll(allowed);
        more.addAll(List.of(places));
        return new ResponseSweeper(needles, more);
    }

    /** Varre um texto qualquer; vazio se o lugar é permitido. */
    public List<Finding> sweepText(Place place, String text) {
        if (allowed.contains(place) || text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> haystacks = haystacksOf(text);
        List<Finding> findings = new ArrayList<>();
        needles.forEach(
                (label, needle) -> {
                    if (haystacks.stream().anyMatch(haystack -> haystack.contains(needle))) {
                        findings.add(new Finding(label, place));
                    }
                });
        return findings;
    }

    /** Varre URL, cabeçalhos e corpo; o cabeçalho {@code Location} conta como URL. */
    public List<Finding> sweep(String url, Map<String, List<String>> headers, String body) {
        Set<Finding> findings = new LinkedHashSet<>();
        findings.addAll(sweepText(Place.URL, url));
        StringBuilder plainHeaders = new StringBuilder();
        headers.forEach(
                (name, values) -> {
                    for (String value : values) {
                        if ("location".equalsIgnoreCase(name)) {
                            findings.addAll(sweepText(Place.URL, value));
                        } else {
                            plainHeaders.append(name).append(": ").append(value).append('\n');
                        }
                    }
                });
        findings.addAll(sweepText(Place.HEADERS, plainHeaders.toString()));
        findings.addAll(sweepText(Place.BODY, body));
        return List.copyOf(findings);
    }

    /**
     * Varre a camada de servlet: URI com consulta, cabeçalhos, corpo e mensagem de erro da
     * resposta.
     */
    public List<Finding> sweep(MockHttpServletRequest request, MockHttpServletResponse response) {
        String query = request.getQueryString();
        String url = request.getRequestURI() + (query == null ? "" : "?" + query);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name));
        }
        Set<Finding> findings = new LinkedHashSet<>(sweep(url, headers, bodyOf(response)));
        findings.addAll(sweepText(Place.ERROR_MESSAGE, response.getErrorMessage()));
        return List.copyOf(findings);
    }

    public List<Finding> sweep(MvcResult result) {
        return sweep(result.getRequest(), result.getResponse());
    }

    /** Falha se o texto tem algum valor num lugar não permitido. */
    public void assertClean(Place place, String text) {
        requireNone(sweepText(place, text));
    }

    public void assertClean(MockHttpServletRequest request, MockHttpServletResponse response) {
        requireNone(sweep(request, response));
    }

    public void assertClean(MvcResult result) {
        requireNone(sweep(result));
    }

    /** Falha com a lista de achados (rótulo e lugar, nunca o valor). */
    public static void requireNone(Collection<Finding> findings) {
        if (!findings.isEmpty()) {
            throw new AssertionError(
                    "Dado canário em lugar não permitido: "
                            + String.join("; ", findings.stream().map(Finding::toString).toList()));
        }
    }

    private static String bodyOf(MockHttpServletResponse response) {
        try {
            return response.getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<String> haystacksOf(String text) {
        String decoded = urlDecode(text);
        String unescaped = unescapeUnicode(decoded);
        List<String> variants = new ArrayList<>();
        for (String variant : new LinkedHashSet<>(List.of(text, decoded, unescaped))) {
            String folded = fold(variant);
            variants.add(folded);
            variants.add(SEPARATOR_BETWEEN_DIGITS.matcher(folded).replaceAll(""));
        }
        return variants;
    }

    private static String canonicalNeedle(String value) {
        if (NUMERIC_VALUE.matcher(value).matches()) {
            String digits = value.replaceAll("\\D", "");
            if (digits.length() >= MIN_NUMERIC_DIGITS) {
                return digits;
            }
        }
        return fold(value);
    }

    private static String fold(String text) {
        String withoutAccents =
                Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String urlDecode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return text;
        }
    }

    private static String unescapeUnicode(String text) {
        Matcher matcher = UNICODE_ESCAPE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String character = String.valueOf((char) Integer.parseInt(matcher.group(1), 16));
            matcher.appendReplacement(result, Matcher.quoteReplacement(character));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
