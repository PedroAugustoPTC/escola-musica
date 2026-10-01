package br.com.felipeblaft.relatorios.casodeuso.semestral;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.EntradaAudio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AcessoSemestralServico;
import br.com.felipeblaft.relatorios.servico.EnvioAudioServico;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido de alteração do semestral em linguagem natural, por texto ou áudio (FR-043, FR-033): como
 * no relatório de aula, exige a versão vigente e gera nova versão; nunca altera as Informações
 * Gerais.
 */
public class SolicitarAlteracaoRelatorioSemestralCasoDeUso {

    private final AcessoSemestralServico acesso;
    private final RelatorioSemestralRepositorio semestrais;
    private final EnvioAudioServico envioAudio;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public SolicitarAlteracaoRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            RelatorioSemestralRepositorio semestrais,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        this.acesso = Objects.requireNonNull(acesso);
        this.semestrais = Objects.requireNonNull(semestrais);
        this.envioAudio = Objects.requireNonNull(envioAudio);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public TrabalhoProcessamento alterarPorTexto(
            UUID professorId, UUID relatorioId, String texto, int versaoBase, Canal canal) {
        RelatorioSemestral relatorio = acesso.relatorioParaAtualizar(professorId, relatorioId);
        relatorio.exigirPodeAlterar(versaoBase);
        if (texto == null || texto.isBlank()) {
            throw new ExcecaoDominio(CodigoErro.ALTERACAO_VAZIA);
        }
        TrabalhoProcessamento trabalho = iniciar(relatorio, canal, Meio.TEXTO, versaoBase);
        trabalho.definirParametro(TrabalhoProcessamento.TEXTO, texto.strip());
        fila.enfileirar(trabalho);
        return trabalho;
    }

    public TrabalhoProcessamento alterarPorAudio(
            UUID professorId, UUID relatorioId, EntradaAudio audio, int versaoBase, Canal canal) {
        try {
            RelatorioSemestral relatorio = acesso.relatorioParaAtualizar(professorId, relatorioId);
            relatorio.exigirPodeAlterar(versaoBase);
            envioAudio.exigirDentroDoLimite(audio);
            TrabalhoProcessamento trabalho = iniciar(relatorio, canal, Meio.AUDIO, versaoBase);
            envioAudio.anexar(trabalho, audio);
            fila.enfileirar(trabalho);
            return trabalho;
        } catch (ExcecaoDominio recusa) {
            envioAudio.descartar(audio);
            throw recusa;
        }
    }

    private TrabalhoProcessamento iniciar(
            RelatorioSemestral relatorio, Canal canal, Meio meio, int versaoBase) {
        relatorio.iniciarEtapa(
                meio == Meio.AUDIO
                        ? EtapaRelatorio.TRANSCREVENDO_PEDIDO
                        : EtapaRelatorio.APLICANDO_ALTERACAO,
                relogio.agora());
        semestrais.salvar(relatorio);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.ALTERACAO,
                        relatorio.professorId(),
                        relatorio.alunoId(),
                        TipoRelatorio.SEMESTRAL,
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
