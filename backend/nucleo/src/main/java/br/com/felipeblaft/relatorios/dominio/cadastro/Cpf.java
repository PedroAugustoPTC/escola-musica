package br.com.felipeblaft.relatorios.dominio.cadastro;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;

/**
 * CPF normalizado (11 dígitos, dígitos verificadores válidos). Nunca é exibido nem registrado: o
 * {@link #toString()} não revela o valor (FR-011, FR-055).
 */
public final class Cpf {

    private final String digitos;

    private Cpf(String digitos) {
        this.digitos = digitos;
    }

    public static Cpf de(String entrada) {
        if (entrada == null) {
            throw new ExcecaoDominio(CodigoErro.CPF_INVALIDO);
        }
        String digitos = entrada.replaceAll("[.\\-\\s/]", "");
        if (!digitos.matches("\\d{11}") || digitos.chars().distinct().count() == 1) {
            throw new ExcecaoDominio(CodigoErro.CPF_INVALIDO);
        }
        if (digitoVerificador(digitos, 9) != digitos.charAt(9) - '0'
                || digitoVerificador(digitos, 10) != digitos.charAt(10) - '0') {
            throw new ExcecaoDominio(CodigoErro.CPF_INVALIDO);
        }
        return new Cpf(digitos);
    }

    private static int digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** Os 11 dígitos, apenas para cifragem e hash. */
    public String digitos() {
        return digitos;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Cpf cpf && cpf.digitos.equals(digitos);
    }

    @Override
    public int hashCode() {
        return digitos.hashCode();
    }

    @Override
    public String toString() {
        return "Cpf[***]";
    }
}
