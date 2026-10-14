package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.HistoryItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Formatação comum aos dois canais: datas {@code dd/mm/aaaa} e horas {@code hh:mm} no fuso da
 * escola, e os parâmetros de texto de cada código de erro (mesmo conteúdo nos dois canais, FR-003).
 */
@Component
public class MessageFormatting {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageCatalog catalog;
    private final ClockPort clock;

    public MessageFormatting(MessageCatalog catalog, ClockPort clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    public String data(LocalDate data) {
        return data.format(DATA);
    }

    public String data(Instant instant) {
        return instant.atZone(clock.schoolTimezone()).format(DATA);
    }

    public String hora(Instant instant) {
        return instant.atZone(clock.schoolTimezone()).format(HORA);
    }

    /** "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)" — iguais nos dois canais. */
    public String historyLabel(HistoryItem item) {
        return item.type() == ReportType.AULA
                ? catalog.text("ROTULO_AULA", Map.of("data", data(item.lessonDate())))
                : catalog.text(
                        "ROTULO_SEMESTRAL",
                        Map.of(
                                "inicio",
                                data(item.period().start()),
                                "fim",
                                data(item.period().end())));
    }

    /** "Aprovado em dd/mm/aaaa às hh:mm" no fuso da escola. */
    public String approvedAt(Instant instant) {
        return catalog.text("APROVADO_EM", Map.of("data", data(instant), "hora", hora(instant)));
    }

    public String originChannel(Channel channel) {
        return catalog.text(
                channel == Channel.TELEGRAM ? "CANAL_ORIGEM_TELEGRAM" : "CANAL_ORIGEM_WEB");
    }

    public String step(String step) {
        String key = "ETAPA_" + step;
        return catalog.contains(key) ? catalog.text(key) : "";
    }

    /** Parâmetros dos textos de erro a partir dos detalhes estruturados vindos do núcleo. */
    public Map<String, Object> errorParameters(ErrorCode code, Map<String, Object> details) {
        Map<String, Object> parameters = new HashMap<>(details);
        switch (code) {
            case BLOQUEADO_TEMPORARIAMENTE -> {
                long seconds =
                        details.get("segundosRestantes") instanceof Number n ? n.longValue() : 0L;
                parameters.put("minutos", Math.max(1, (seconds + 59) / 60));
            }
            case PROCESSAMENTO_EM_ANDAMENTO -> {
                Object step = details.get("etapa");
                parameters.put("etapa", step == null ? "" : step(step.toString()));
            }
            case RELATORIO_EM_ABERTO -> {
                if (details.get("relatorioEmAberto") instanceof Map<?, ?> openReport) {
                    if (openReport.get("canalOrigem") instanceof Channel channel) {
                        parameters.put("canalOrigem", originChannel(channel));
                    } else if (openReport.get("canalOrigem") != null) {
                        parameters.put(
                                "canalOrigem",
                                originChannel(
                                        Channel.valueOf(openReport.get("canalOrigem").toString())));
                    }
                    if (openReport.get("atualizadoEm") instanceof Instant updated) {
                        parameters.put("data", data(updated));
                        parameters.put("hora", hora(updated));
                    }
                }
            }
            default -> {
                // demais códigos usam os detalhes como estão (ex.: versaoVigente, quantidade,
                // limiteMb)
            }
        }
        return parameters;
    }
}
