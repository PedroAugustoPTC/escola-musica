package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.port.ClockPort;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Monta o registro de autoria (FR-040) e escolhe o modo de gravação: o sucesso entra na mesma
 * transação do caso de uso (gravação e registro valem juntos); falha, negativa e bloqueio entram em
 * transação independente, porque a transação da operação será desfeita. Os parâmetros são só
 * identificadores e códigos: operação, código de erro, identificador de correlação e resumo da
 * origem passam por validação de formato estrito (aqui e no construtor de {@link AuthorshipRecord},
 * que a porta recebe direto), de modo que CPF, telefone, e-mail digitado, senha ou código de acesso
 * ou de ativação nunca são gravados por engano.
 */
public class AuthorshipService {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,47}");

    private final AuthorshipRecorderPort recorder;
    private final ClockPort clock;

    public AuthorshipService(AuthorshipRecorderPort recorder, ClockPort clock) {
        this.recorder = Objects.requireNonNull(recorder);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Registra o sucesso de uma operação, na mesma transação. */
    public void record(
            Actor actor,
            Operation operation,
            AuthorshipTargetType targetType,
            UUID targetId,
            String correlationId) {
        record(
                actor,
                operation.name(),
                targetType,
                targetId,
                AuthorshipResult.SUCCESS,
                null,
                correlationId);
    }

    /**
     * Registra uma operação ou evento com o resultado dado: {@code SUCCESS} na mesma transação,
     * qualquer outro em transação independente.
     */
    public void record(
            Actor actor,
            String operation,
            AuthorshipTargetType targetType,
            UUID targetId,
            AuthorshipResult result,
            String errorCode,
            String correlationId) {
        write(
                new AuthorshipRecord(
                        GeneratorId.create(),
                        clock.now(),
                        actor.id(),
                        actor.role(),
                        actor.origin(),
                        checkedCode(operation),
                        targetType,
                        targetId,
                        result,
                        checkedErrorCode(result, errorCode),
                        null,
                        required(correlationId)));
    }

    /**
     * Registra uma leitura da visão da escola (feature 006) com o detalhe (filtros e página, ou
     * versão do PDF): {@code SUCCESS} na mesma transação da leitura, qualquer outro resultado em
     * transação independente. O detalhe só é aceito nas operações de leitura da feature (validado
     * no construtor de {@link AuthorshipRecord}).
     */
    public void recordRead(
            Actor actor,
            Operation operation,
            AuthorshipTargetType targetType,
            UUID targetId,
            AuthorshipReadDetail detail,
            AuthorshipResult result,
            String errorCode,
            String correlationId) {
        write(
                new AuthorshipRecord(
                        GeneratorId.create(),
                        clock.now(),
                        actor.id(),
                        actor.role(),
                        actor.origin(),
                        checkedCode(operation.name()),
                        targetType,
                        targetId,
                        result,
                        checkedErrorCode(result, errorCode),
                        null,
                        required(correlationId),
                        detail));
    }

    /**
     * Registra um evento de autenticação (entrada, vínculo, bloqueio), o único que leva o resumo da
     * origem. O autor é nulo e o papel é nulo quando o e-mail ou o código é desconhecido.
     */
    public void recordAuthentication(
            ActionOrigin origin,
            Role role,
            UUID authorId,
            String operation,
            AuthorshipResult result,
            String errorCode,
            String originHash,
            String correlationId) {
        write(
                new AuthorshipRecord(
                        GeneratorId.create(),
                        clock.now(),
                        authorId,
                        role,
                        origin,
                        checkedCode(operation),
                        null,
                        null,
                        result,
                        checkedErrorCode(result, errorCode),
                        required(originHash),
                        required(correlationId)));
    }

    private void write(AuthorshipRecord record) {
        if (record.result() == AuthorshipResult.SUCCESS) {
            recorder.record(record);
        } else {
            recorder.recordIndependent(record);
        }
    }

    private static String checkedCode(String value) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("operação de autoria fora do formato");
        }
        return value;
    }

    private static String checkedErrorCode(AuthorshipResult result, String errorCode) {
        if (errorCode == null && result == AuthorshipResult.SUCCESS) {
            return null;
        }
        if (result == AuthorshipResult.SUCCESS) {
            throw new IllegalArgumentException("sucesso não leva código de erro");
        }
        return errorCode == null ? null : checkedCode(errorCode);
    }

    /** O formato estrito é validado no construtor de {@link AuthorshipRecord}. */
    private static String required(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identificador de autoria ausente");
        }
        return value;
    }
}
