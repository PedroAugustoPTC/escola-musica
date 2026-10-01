package br.com.felipeblaft.relatorios.casodeuso.trabalho;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.AudioTemporarioServico;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * "Descartar e começar um novo" para um registro ainda em transcrição (sem relatório criado): o
 * trabalho fica {@code DESCARTADO} e o resultado que o worker produzir depois é ignorado. O áudio
 * de um trabalho ainda pendente é apagado agora (FR-018); o de um em execução, pelo próprio worker.
 */
public class DescartarRegistroEmProcessamentoCasoDeUso {

    private final FilaTrabalhoPorta fila;
    private final AudioTemporarioServico audio;
    private final RelogioPorta relogio;

    public DescartarRegistroEmProcessamentoCasoDeUso(
            FilaTrabalhoPorta fila, AudioTemporarioServico audio, RelogioPorta relogio) {
        this.fila = Objects.requireNonNull(fila);
        this.audio = Objects.requireNonNull(audio);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public void executar(UUID professorId, UUID trabalhoId) {
        TrabalhoProcessamento trabalho =
                fila.buscarPorId(trabalhoId)
                        .filter(t -> t.professorId().equals(professorId))
                        .orElseThrow(() -> new ExcecaoDominio(CodigoErro.RECURSO_NAO_ENCONTRADO));
        if (trabalho.tipo() != TipoTrabalho.REGISTRO_AULA || trabalho.relatorioId() != null) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        boolean pendente = trabalho.status() == StatusTrabalho.PENDENTE;
        if (!trabalho.descartar(relogio.agora())) {
            throw new ExcecaoDominio(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        if (pendente && trabalho.arquivoTemporario() != null) {
            audio.descartar(new ArquivoTemporario(Path.of(trabalho.arquivoTemporario()), 0, null));
            trabalho.definirArquivoTemporario(null);
        }
        fila.atualizar(trabalho);
    }
}
