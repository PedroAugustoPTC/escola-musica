package br.com.felipeblaft.reports;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Ponto de entrada do backend: servidor (Web + webhook do Telegram) ou comandos de carga. */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PropriedadesAplicacao.class)
public class RelatoriosAplicacao {

    public static void main(String[] args) {
        SpringApplication.run(RelatoriosAplicacao.class, args);
    }
}
