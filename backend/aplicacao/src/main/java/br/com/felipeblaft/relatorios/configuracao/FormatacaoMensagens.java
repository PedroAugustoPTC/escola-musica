package br.com.felipeblaft.relatorios.configuracao;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
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
    private final RelogioPorta relogio;

    public FormatacaoMensagens(CatalogoMensagens catalogo, RelogioPorta relogio) {
        this.catalogo = catalogo;
        this.relogio = relogio;
    }

    public String data(LocalDate data) {
        return data.format(DATA);
    }

    public String data(Instant instante) {
        return instante.atZone(relogio.fusoDaEscola()).format(DATA);
    }

    public String hora(Instant instante) {
        return instante.atZone(relogio.fusoDaEscola()).format(HORA);
    }

    public String canalOrigem(Canal canal) {
        return catalogo.texto(canal == Canal.TELEGRAM ? "CANAL_ORIGEM_TELEGRAM" : "CANAL_ORIGEM_WEB");
    }

    public String etapa(String etapa) {
        String chave = "ETAPA_" + etapa;
        return catalogo.contem(chave) ? catalogo.texto(chave) : "";
    }

    /** Parâmetros dos textos de erro a partir dos detalhes estruturados vindos do núcleo. */
    public Map<String, Object> parametrosErro(CodigoErro codigo, Map<String, Object> detalhes) {
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
                    if (aberto.get("canalOrigem") instanceof Canal canal) {
                        parametros.put("canalOrigem", canalOrigem(canal));
                    } else if (aberto.get("canalOrigem") != null) {
                        parametros.put(
                                "canalOrigem",
                                canalOrigem(Canal.valueOf(aberto.get("canalOrigem").toString())));
                    }
                    if (aberto.get("atualizadoEm") instanceof Instant atualizado) {
                        parametros.put("data", data(atualizado));
                        parametros.put("hora", hora(atualizado));
                    }
                }
            }
            default -> {
                // demais códigos usam os detalhes como estão (ex.: versaoVigente, quantidade, limiteMb)
            }
        }
        return parametros;
    }
}
