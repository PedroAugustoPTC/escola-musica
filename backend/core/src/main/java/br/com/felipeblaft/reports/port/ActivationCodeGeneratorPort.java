package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.ActivationCode;

/** Gera códigos de ativação aleatórios e imprevisíveis (12 caracteres). */
public interface ActivationCodeGeneratorPort {

    ActivationCode generate();
}
