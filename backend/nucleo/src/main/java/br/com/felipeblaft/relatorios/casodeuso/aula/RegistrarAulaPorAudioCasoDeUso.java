package br.com.felipeblaft.relatorios.casodeuso.aula;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioEmAberto;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.EntradaAudio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import br.com.felipeblaft.relatorios.servico.EnvioAudioServico;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registrar uma aula por áudio (FR-014 a FR-017, FR-034): exige associação, nenhum relatório em
 * aberto para o aluno e o limite de tamanho; enfileira o processamento com o canal de origem. O
 * áudio de um envio recusado é descartado.
 */
public class RegistrarAulaPorAudioCasoDeUso {

    private final AutorizacaoServico autorizacao;
    private final RelatoriosEmAbertoConsultaPorta emAberto;
    private final EnvioAudioServico envioAudio;
    private final FilaTrabalhoPorta fila;
    private final RelogioPorta relogio;

    public RegistrarAulaPorAudioCasoDeUso(
            AutorizacaoServico autorizacao,
            RelatoriosEmAbertoConsultaPorta emAberto,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        this.autorizacao = Objects.requireNonNull(autorizacao);
        this.emAberto = Objects.requireNonNull(emAberto);
        this.envioAudio = Objects.requireNonNull(envioAudio);
        this.fila = Objects.requireNonNull(fila);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public TrabalhoProcessamento executar(
            UUID professorId, UUID alunoId, EntradaAudio audio, Canal canal) {
        try {
            autorizacao.exigirAssociacao(professorId, alunoId);
            exigirSemRelatorioEmAberto(professorId, alunoId);
            envioAudio.exigirDentroDoLimite(audio);
            TrabalhoProcessamento trabalho =
                    TrabalhoProcessamento.novo(
                            TipoTrabalho.REGISTRO_AULA,
                            professorId,
                            alunoId,
                            null,
                            null,
                            canal,
                            Meio.AUDIO,
                            EtapaTrabalho.ENVIANDO,
                            relogio.agora());
            envioAudio.anexar(trabalho, audio);
            try {
                fila.enfileirar(trabalho);
            } catch (ConflitoConcorrenciaExcecao conflito) {
                exigirSemRelatorioEmAberto(professorId, alunoId);
                throw new ExcecaoDominio(CodigoErro.RELATORIO_EM_ABERTO);
            }
            return trabalho;
        } catch (ExcecaoDominio recusa) {
            envioAudio.descartar(audio);
            throw recusa;
        }
    }

    private void exigirSemRelatorioEmAberto(UUID professorId, UUID alunoId) {
        Optional<RelatorioEmAberto> aberto = emAberto.buscarDoAluno(professorId, alunoId);
        if (aberto.isPresent()) {
            throw new ExcecaoDominio(
                    CodigoErro.RELATORIO_EM_ABERTO,
                    Map.of("relatorioEmAberto", aberto.get().detalhes()));
        }
    }
}
