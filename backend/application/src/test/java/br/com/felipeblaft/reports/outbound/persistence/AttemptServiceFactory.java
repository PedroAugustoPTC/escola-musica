package br.com.felipeblaft.reports.outbound.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.port.AccessAttemptRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Monta o serviço de tentativas sobre o adaptador real do Postgres, com um resumo de origem
 * determinístico no lugar do {@code DataProtectionAdapter} (o resumo por escopo é do fluxo W-E): os
 * testes aqui verificam a contagem atômica, não o algoritmo do resumo.
 */
final class AttemptServiceFactory {

    private AttemptServiceFactory() {}

    static AccessAttemptsControlService create(
            AccessAttemptRepository repository, ClockPort clock) {
        DataProtectionPort protection = mock(DataProtectionPort.class);
        when(protection.originHash(any(AttemptScope.class), anyString()))
                .thenAnswer(
                        call -> hex("scope:" + call.getArgument(0) + ":" + call.getArgument(1)));
        when(protection.originHash(any(Channel.class), anyString()))
                .thenAnswer(
                        call -> hex("channel:" + call.getArgument(0) + ":" + call.getArgument(1)));
        return new AccessAttemptsControlService(
                repository, protection, clock, BusinessParameters.defaults());
    }

    private static String hex(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
