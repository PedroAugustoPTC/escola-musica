package br.com.felipeblaft.relatorios.saida.seguranca;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.porta.GeradorCodigoAcessoPorta;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Códigos de 8 caracteres com {@link SecureRandom} (R-12). */
@Component
public class GeradorCodigoAcessoAdaptador implements GeradorCodigoAcessoPorta {

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public CodigoAcesso gerar() {
        StringBuilder codigo = new StringBuilder(CodigoAcesso.TAMANHO);
        for (int i = 0; i < CodigoAcesso.TAMANHO; i++) {
            codigo.append(
                    CodigoAcesso.ALFABETO.charAt(
                            aleatorio.nextInt(CodigoAcesso.ALFABETO.length())));
        }
        return CodigoAcesso.de(codigo.toString());
    }
}
