package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.EntradaAudio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import br.com.felipeblaft.relatorios.servico.EnvioAudioServico;
import java.util.Objects;
import java.util.UUID;

/**
 * Responder às perguntas pendentes numa única mensagem, por texto ou áudio (FR-024, FR-024a). A
 * resposta é incorporada pela IA no processamento (FR-025).
 */
public class ResponderPerguntasCasoDeUso {

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final EnvioAudioServico envioAudio;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public ResponderPerguntasCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.envioAudio = Objects.requireNonNull(envioAudio);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public TrabalhoProcessamento responderTexto(
            UUID professorId, UUID relatorioId, String texto, Canal canal) {
        RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
        relatorio.exigirPodeResponder();
        if (texto == null || texto.isBlank()) {
            throw new ExcecaoDominio(CodigoErro.RESPOSTA_VAZIA);
        }
        TrabalhoProcessamento trabalho =
                iniciar(relatorio, canal, Meio.TEXTO, EtapaRelatorio.INCORPORANDO_RESPOSTAS);
        trabalho.definirParametro(TrabalhoProcessamento.TEXTO, texto.strip());
        fila.enfileirar(trabalho);
        return trabalho;
    }

    public TrabalhoProcessamento responderAudio(
            UUID professorId, UUID relatorioId, EntradaAudio audio, Canal canal) {
        try {
            RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
            relatorio.exigirPodeResponder();
            envioAudio.exigirDentroDoLimite(audio);
            TrabalhoProcessamento trabalho =
                    iniciar(relatorio, canal, Meio.AUDIO, EtapaRelatorio.TRANSCREVENDO_PEDIDO);
            envioAudio.anexar(trabalho, audio);
            fila.enfileirar(trabalho);
            return trabalho;
        } catch (ExcecaoDominio recusa) {
            envioAudio.descartar(audio);
            throw recusa;
        }
    }

    private TrabalhoProcessamento iniciar(
            RelatorioAula relatorio, Canal canal, Meio meio, EtapaRelatorio etapa) {
        relatorio.iniciarEtapa(etapa, relogio.agora());
        relatorios.salvar(relatorio);
        return TrabalhoProcessamento.novo(
                TipoTrabalho.RESPOSTA_PERGUNTAS,
                relatorio.professorId(),
                relatorio.alunoId(),
                TipoRelatorio.AULA,
                relatorio.id(),
                canal,
                meio,
                meio == Meio.AUDIO ? EtapaTrabalho.TRANSCREVENDO_PEDIDO : EtapaTrabalho.INCORPORANDO_RESPOSTAS,
                relogio.agora());
    }
}
