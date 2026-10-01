package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;

/**
 * Avisos ativos ao professor (FR-021): etapas e resultado no canal de origem e, para trabalhos da
 * Web com Telegram vinculado, um aviso curto no bot. A Web acompanha por consulta e não precisa de
 * envio.
 */
public interface NotificacaoProfessorPorta {

    void notificarEtapa(TrabalhoProcessamento trabalho);

    void notificarResultado(TrabalhoProcessamento trabalho);

    void avisarDemora(TrabalhoProcessamento trabalho);
}
