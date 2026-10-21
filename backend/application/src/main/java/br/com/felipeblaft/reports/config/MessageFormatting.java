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
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

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

    public String time(Instant instant) {
        return instant.atZone(clock.schoolTimezone()).format(TIME_FORMAT);
    }

    /** "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)" — iguais nos dois canais. */
    public String historyLabel(HistoryItem item) {
        return item.type() == ReportType.LESSON
                ? catalog.text("LESSON_LABEL", Map.of("data", data(item.lessonDate())))
                : catalog.text(
                        "SEMESTER_LABEL",
                        Map.of(
                                "start",
                                data(item.period().start()),
                                "end",
                                data(item.period().end())));
    }

    /** "Aprovado em dd/mm/aaaa às hh:mm" no fuso da escola. */
    public String approvedAt(Instant instant) {
        return catalog.text("APPROVED_AT", Map.of("data", data(instant), "time", time(instant)));
    }

    public String originChannel(Channel channel) {
        return catalog.text(
                channel == Channel.TELEGRAM ? "TELEGRAM_ORIGIN_CHANNEL" : "WEB_ORIGIN_CHANNEL");
    }

    public String step(String step) {
        String key = "STEP_" + step;
        return catalog.contains(key) ? catalog.text(key) : "";
    }

    /** Parâmetros dos textos de erro a partir dos detalhes estruturados vindos do núcleo. */
    public Map<String, Object> errorParameters(ErrorCode code, Map<String, Object> details) {
        Map<String, Object> parameters = new HashMap<>(details);
        switch (code) {
            case TEMPORARILY_BLOCKED, OPERATION_TEMPORARILY_BLOCKED -> {
                long seconds =
                        details.get("remainingSeconds") instanceof Number n ? n.longValue() : 0L;
                parameters.put("minutes", Math.max(1, (seconds + 59) / 60));
            }
            case PROCESSING_IN_PROGRESS -> {
                Object step = details.get("step");
                parameters.put("step", step == null ? "" : step(step.toString()));
            }
            case REPORT_ALREADY_OPEN -> {
                if (details.get("openReport") instanceof Map<?, ?> openReport) {
                    if (openReport.get("originChannel") instanceof Channel channel) {
                        parameters.put("originChannel", originChannel(channel));
                    } else if (openReport.get("originChannel") != null) {
                        parameters.put(
                                "originChannel",
                                originChannel(
                                        Channel.valueOf(
                                                openReport.get("originChannel").toString())));
                    }
                    if (openReport.get("updatedAt") instanceof Instant updated) {
                        parameters.put("data", data(updated));
                        parameters.put("time", time(updated));
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
