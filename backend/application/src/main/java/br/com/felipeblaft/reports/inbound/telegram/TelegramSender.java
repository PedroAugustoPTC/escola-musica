package br.com.felipeblaft.reports.inbound.telegram;

/**
 * Envio das saídas do bot pela API de bots (implementado por MensageriaTelegramAdaptador). Falhas
 * de envio são registradas e não desfazem o estado já gravado.
 */
public interface TelegramSender {

    void send(TelegramOutput output);
}
