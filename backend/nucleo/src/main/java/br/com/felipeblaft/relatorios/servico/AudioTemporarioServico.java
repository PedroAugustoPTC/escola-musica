package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.porta.ArquivoCanalPorta;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.AudioNormalizado;
import br.com.felipeblaft.relatorios.porta.EntradaAudio;
import br.com.felipeblaft.relatorios.porta.NormalizacaoAudioPorta;
import br.com.felipeblaft.relatorios.porta.ProvedorIndisponivelExcecao;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import br.com.felipeblaft.relatorios.porta.ResultadoTranscricao;
import br.com.felipeblaft.relatorios.porta.TranscricaoPorta;
import java.util.Objects;

/**
 * Obtém, normaliza e transcreve o áudio e apaga o original e o normalizado num {@code finally},
 * com sucesso ou falha (FR-018). Diante de indisponibilidade do provedor, tenta de novo uma vez
 * (R-08) antes de apagar.
 */
public class AudioTemporarioServico {

    private final ArquivoCanalPorta arquivoCanal;
    private final NormalizacaoAudioPorta normalizacao;
    private final TranscricaoPorta transcricao;

    public AudioTemporarioServico(
            ArquivoCanalPorta arquivoCanal,
            NormalizacaoAudioPorta normalizacao,
            TranscricaoPorta transcricao) {
        this.arquivoCanal = Objects.requireNonNull(arquivoCanal);
        this.normalizacao = Objects.requireNonNull(normalizacao);
        this.transcricao = Objects.requireNonNull(transcricao);
    }

    public ResultadoTranscricao transcrever(EntradaAudio entrada) {
        ArquivoTemporario original = null;
        AudioNormalizado normalizado = null;
        try {
            original =
                    switch (entrada) {
                        case ArquivoTemporario arquivo -> arquivo;
                        case ReferenciaArquivoCanal referencia -> arquivoCanal.obterArquivo(referencia);
                    };
            normalizado = normalizacao.normalizar(original);
            ResultadoTranscricao resultado;
            try {
                resultado = transcricao.transcrever(normalizado);
            } catch (ProvedorIndisponivelExcecao primeiraFalha) {
                resultado = transcricao.transcrever(normalizado);
            }
            if (resultado.texto() == null || resultado.texto().isBlank()) {
                throw new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.SEM_FALA);
            }
            return resultado;
        } finally {
            if (normalizado != null) {
                normalizacao.apagar(normalizado.caminho());
            }
            if (original != null) {
                normalizacao.apagar(original.caminho());
            }
        }
    }

    /** Descarta um áudio que não será processado (ex.: o caso de uso recusou o envio). */
    public void descartar(EntradaAudio entrada) {
        if (entrada instanceof ArquivoTemporario arquivo) {
            normalizacao.apagar(arquivo.caminho());
        }
    }
}
