package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.common.Secret;
import java.time.Instant;

/**
 * Resultado de criar ou reativar um administrador: o código de ativação em claro existe só aqui, é
 * exibido uma única vez e nunca vai a lista, log, URL nem registro de autoria.
 */
public record AdministratorWithCode(
        AdministratorView administrator, Secret activationCode, Instant validUntil) {}
