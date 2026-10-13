package br.com.felipeblaft.reports.configuracao;

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
public class FormatacaoMensagens {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CatalogoMensagens catalogo;
    private final ClockPort relogio;

    public FormatacaoMensagens(CatalogoMensagens catalogo, ClockPort relogio) {
        this.catalogo = catalogo;
        this.relogio = relogio;
    }

    public String data(LocalDate data) {
        return data.format(DATA);
    }

    public String data(Instant instante) {
        return instante.atZone(relogio.schoolTimezone()).format(DATA);
    }

    public String hora(Instant instante) {
        return instante.atZone(relogio.schoolTimezone()).format(HORA);
    }

    /** "Aula de dd/mm/aaaa" ou "Semestral (dd/mm/aaaa a dd/mm/aaaa)" — iguais nos dois canais. */
    public String rotuloHistorico(HistoryItem item) {
        return item.type() == ReportType.AULA
                ? catalogo.texto("ROTULO_AULA", Map.of("data", data(item.lessonDate())))
                : catalogo.texto(
                        "ROTULO_SEMESTRAL",
                        Map.of(
                                "inicio",
                                data(item.period().start()),
                                "fim",
                                data(item.period().end())));
    }

    /** "Aprovado em dd/mm/aaaa às hh:mm" no fuso da escola. */
    public String aprovadoEm(Instant instante) {
        return catalogo.texto(
                "APROVADO_EM", Map.of("data", data(instante), "hora", hora(instante)));
    }

    public String canalOrigem(Channel canal) {
        return catalogo.texto(
                canal == Channel.TELEGRAM ? "CANAL_ORIGEM_TELEGRAM" : "CANAL_ORIGEM_WEB");
    }

    public String etapa(String etapa) {
        String chave = "ETAPA_" + etapa;
        return catalogo.contem(chave) ? catalogo.texto(chave) : "";
    }

    /** Parâmetros dos textos de erro a partir dos detalhes estruturados vindos do núcleo. */
    public Map<String, Object> parametrosErro(ErrorCode codigo, Map<String, Object> detalhes) {
        Map<String, Object> parametros = new HashMap<>(detalhes);
        switch (codigo) {
            case BLOQUEADO_TEMPORARIAMENTE -> {
                long segundos =
                        detalhes.get("segundosRestantes") instanceof Number n ? n.longValue() : 0L;
                parametros.put("minutos", Math.max(1, (segundos + 59) / 60));
            }
            case PROCESSAMENTO_EM_ANDAMENTO -> {
                Object etapa = detalhes.get("etapa");
                parametros.put("etapa", etapa == null ? "" : etapa(etapa.toString()));
            }
            case RELATORIO_EM_ABERTO -> {
                if (detalhes.get("relatorioEmAberto") instanceof Map<?, ?> aberto) {
                    if (aberto.get("canalOrigem") instanceof Channel canal) {
                        parametros.put("canalOrigem", canalOrigem(canal));
                    } else if (aberto.get("canalOrigem") != null) {
                        parametros.put(
                                "canalOrigem",
                                canalOrigem(Channel.valueOf(aberto.get("canalOrigem").toString())));
                    }
                    if (aberto.get("atualizadoEm") instanceof Instant atualizado) {
                        parametros.put("data", data(atualizado));
                        parametros.put("hora", hora(atualizado));
                    }
                }
            }
            default -> {
                // demais códigos usam os detalhes como estão (ex.: versaoVigente, quantidade,
                // limiteMb)
            }
        }
        return parametros;
    }
}
