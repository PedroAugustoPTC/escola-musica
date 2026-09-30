package br.com.felipeblaft.relatorios.configuracao;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades da aplicação (prefixo {@code relatorios}), lidas do ambiente via application.yaml. */
@ConfigurationProperties(prefix = "relatorios")
public record PropriedadesAplicacao(
        Escola escola,
        Audio audio,
        Sessao sessao,
        Acesso acesso,
        Codigo codigo,
        Seguranca seguranca,
        Documentos documentos,
        Telegram telegram,
        Provedores provedores,
        Trabalhos trabalhos) {

    public record Escola(ZoneId fusoHorario) {}

    public record Audio(int limiteMb, Path tempDir) {

        public long limiteBytes() {
            return (long) limiteMb * 1024 * 1024;
        }
    }

    public record Sessao(int horas, boolean cookieSeguro) {}

    public record Acesso(int maxTentativas, int bloqueioMinutos) {}

    public record Codigo(int validadeDias) {}

    public record Seguranca(String cpfChave, String segredoHmac) {

        @Override
        public String toString() {
            return "Seguranca[***]";
        }
    }

    public record Documentos(Path dir) {}

    public record Telegram(
            boolean habilitado,
            String apiUrl,
            String token,
            String segredoWebhook,
            String webhookUrl,
            Path arquivosDir,
            int audioPendenteMinutos) {

        @Override
        public String toString() {
            return "Telegram[habilitado=" + habilitado + ", apiUrl=" + apiUrl + "]";
        }
    }

    public record Provedores(String modo, Transcricao transcricao, Llm llm) {

        public boolean simulado() {
            return !"real".equalsIgnoreCase(modo);
        }
    }

    public record Transcricao(String url, String modelo, String chave, BigDecimal precoUsdPorHora) {

        @Override
        public String toString() {
            return "Transcricao[url=" + url + ", modelo=" + modelo + "]";
        }
    }

    public record Llm(
            String url,
            String modelo,
            String chave,
            BigDecimal precoUsdPorMilhaoEntrada,
            BigDecimal precoUsdPorMilhaoSaida) {

        @Override
        public String toString() {
            return "Llm[url=" + url + ", modelo=" + modelo + "]";
        }
    }

    public record Trabalhos(int workers, long intervaloMs) {}
}
