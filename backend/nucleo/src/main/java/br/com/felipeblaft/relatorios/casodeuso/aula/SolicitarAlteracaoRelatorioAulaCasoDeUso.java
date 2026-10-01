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
 * Pedir alteração em linguagem natural, por texto ou áudio (FR-027, FR-027a, FR-033): exige
 * conferência, nenhuma etapa em curso e a versão vigente; nunca aprova nem cancela.
 */
public class SolicitarAlteracaoRelatorioAulaCasoDeUso {

    private final AcessoRelatorioServico acesso;
    private final RelatorioAulaRepositorio relatorios;
    private final EnvioAudioServico envioAudio;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public SolicitarAlteracaoRelatorioAulaCasoDeUso(
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

    public TrabalhoProcessamento alterarPorTexto(
            UUID professorId, UUID relatorioId, String texto, int versaoBase, Canal canal) {
        RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
        relatorio.exigirPodeAlterar(versaoBase);
        if (texto == null || texto.isBlank()) {
            throw new ExcecaoDominio(CodigoErro.ALTERACAO_VAZIA);
        }
        TrabalhoProcessamento trabalho =
                iniciar(
                        relatorio,
                        canal,
                        Meio.TEXTO,
                        EtapaRelatorio.APLICANDO_ALTERACAO,
                        versaoBase);
        trabalho.definirParametro(TrabalhoProcessamento.TEXTO, texto.strip());
        fila.enfileirar(trabalho);
        return trabalho;
    }

    public TrabalhoProcessamento alterarPorAudio(
            UUID professorId, UUID relatorioId, EntradaAudio audio, int versaoBase, Canal canal) {
        try {
            RelatorioAula relatorio = acesso.relatorioAulaParaAtualizar(professorId, relatorioId);
            relatorio.exigirPodeAlterar(versaoBase);
            envioAudio.exigirDentroDoLimite(audio);
            TrabalhoProcessamento trabalho =
                    iniciar(
                            relatorio,
                            canal,
                            Meio.AUDIO,
                            EtapaRelatorio.TRANSCREVENDO_PEDIDO,
                            versaoBase);
            envioAudio.anexar(trabalho, audio);
            fila.enfileirar(trabalho);
            return trabalho;
        } catch (ExcecaoDominio recusa) {
            envioAudio.descartar(audio);
            throw recusa;
        }
    }

    private TrabalhoProcessamento iniciar(
            RelatorioAula relatorio, Canal canal, Meio meio, EtapaRelatorio etapa, int versaoBase) {
        relatorio.iniciarEtapa(etapa, relogio.agora());
        relatorios.salvar(relatorio);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.ALTERACAO,
                        relatorio.professorId(),
                        relatorio.alunoId(),
                        TipoRelatorio.AULA,
                        relatorio.id(),
                        canal,
                        meio,
                        meio == Meio.AUDIO
                                ? EtapaTrabalho.TRANSCREVENDO_PEDIDO
                                : EtapaTrabalho.APLICANDO_ALTERACAO,
                        relogio.agora());
        trabalho.definirParametro(TrabalhoProcessamento.VERSAO_BASE, Integer.toString(versaoBase));
        return trabalho;
    }
}
