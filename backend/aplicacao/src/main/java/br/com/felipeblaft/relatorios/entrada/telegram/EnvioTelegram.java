package br.com.felipeblaft.relatorios.entrada.telegram;

/**
 * Envio das saídas do bot pela API de bots (implementado por MensageriaTelegramAdaptador). Falhas
 * de envio são registradas e não desfazem o estado já gravado.
 */
public interface EnvioTelegram {

    void enviar(SaidaTelegram saida);
}
