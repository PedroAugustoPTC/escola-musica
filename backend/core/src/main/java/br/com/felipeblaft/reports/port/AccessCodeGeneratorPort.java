package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.AccessCode;

/** Gera códigos de acesso aleatórios e imprevisíveis. */
public interface AccessCodeGeneratorPort {

    AccessCode generate();
}
