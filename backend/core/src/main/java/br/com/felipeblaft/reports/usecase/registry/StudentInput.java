package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.common.Secret;

/**
 * Dados de aluno como chegam de qualquer canal (Web, Telegram, carga): textos brutos, validados no
 * núcleo (R-11). Na edição, campo nulo mantém o atual; {@code cpf} nulo ou em branco mantém o CPF
 * atual; {@code guardian}, quando enviado, substitui os três campos juntos. O CPF é {@link Secret}
 * e o {@code toString} não revela dado pessoal.
 *
 * @param birthDate {@code aaaa-mm-dd} (Web e carga) ou {@code dd/mm/aaaa} (Telegram)
 */
public record StudentInput(
        String name, String birthDate, Secret cpf, GuardianInput guardian, String instrument) {

    @Override
    public String toString() {
        return "StudentInput";
    }
}
