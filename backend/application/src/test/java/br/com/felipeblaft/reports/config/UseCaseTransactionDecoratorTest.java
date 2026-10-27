package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.RotateCpfKeysUseCase;
import org.junit.jupiter.api.Test;

/**
 * Os casos de uso que controlam a própria transação (tudo-ou-nada da carga inicial e da rotação de
 * chave) não podem ser envolvidos por uma transação externa: ela viraria só-reversão ao capturar a
 * falha do caso de uso interno e o commit lançaria {@code UnexpectedRollbackException}.
 */
class UseCaseTransactionDecoratorTest {

    @Test
    void useCasesThatControlTheirOwnTransactionAreNotWrapped() {
        assertThat(UseCaseTransactionDecorator.NO_TRANSACTION)
                .contains(
                        LoadInitialRegistryUseCase.class.getSimpleName(),
                        RotateCpfKeysUseCase.class.getSimpleName());
    }
}
