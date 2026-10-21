package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.common.Secret;
import java.time.Instant;

/**
 * Resultado de cadastrar, reativar ou reemitir o código: o código de acesso em claro existe só
 * aqui, é exibido uma única vez e nunca vai a lista, log, URL nem registro de autoria.
 */
public record TeacherWithCode(TeacherView teacher, Secret accessCode, Instant validUntil) {}
