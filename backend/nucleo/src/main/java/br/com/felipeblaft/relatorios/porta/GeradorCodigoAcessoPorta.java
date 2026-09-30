package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;

/** Gera códigos de acesso aleatórios e imprevisíveis. */
public interface GeradorCodigoAcessoPorta {

    CodigoAcesso gerar();
}
