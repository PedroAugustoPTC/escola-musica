package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.EntradaAudio;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import java.util.Map;
import java.util.Objects;

/**
 * Regras comuns a todo áudio enviado (registro, resposta e alteração): mesmo limite nos dois canais
 * (FR-015) e referência do áudio anexada ao trabalho sem copiar bytes.
 */
public class EnvioAudioServico {

    private final ParametrosNegocio parametros;
    private final AudioTemporarioServico audio;

    public EnvioAudioServico(ParametrosNegocio parametros, AudioTemporarioServico audio) {
        this.parametros = Objects.requireNonNull(parametros);
        this.audio = Objects.requireNonNull(audio);
    }

    public void exigirDentroDoLimite(EntradaAudio entrada) {
        if (entrada.tamanho() > parametros.limiteAudioBytes()) {
            throw new ExcecaoDominio(
                    CodigoErro.AUDIO_MUITO_GRANDE, Map.of("limiteMb", parametros.limiteAudioMb()));
        }
    }

    public void anexar(TrabalhoProcessamento trabalho, EntradaAudio entrada) {
        trabalho.definirParametro(
                TrabalhoProcessamento.ARQUIVO_TAMANHO, Long.toString(entrada.tamanho()));
        switch (entrada) {
            case ArquivoTemporario arquivo -> {
                trabalho.definirArquivoTemporario(arquivo.caminho().toString());
                trabalho.definirParametro(TrabalhoProcessamento.ARQUIVO_MIME, arquivo.mime());
            }
            case ReferenciaArquivoCanal referencia -> {
                trabalho.definirParametro(TrabalhoProcessamento.ARQUIVO_CANAL, referencia.id());
                trabalho.definirParametro(
                        TrabalhoProcessamento.ARQUIVO_CANAL_UNICO, referencia.idUnico());
                trabalho.definirParametro(TrabalhoProcessamento.ARQUIVO_MIME, referencia.mime());
            }
        }
    }

    /** Descarta o áudio de um envio recusado (FR-018). */
    public void descartar(EntradaAudio entrada) {
        audio.descartar(entrada);
    }
}
