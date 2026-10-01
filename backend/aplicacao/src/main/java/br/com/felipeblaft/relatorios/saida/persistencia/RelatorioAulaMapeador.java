package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ResultadoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoInteracao;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.AulaEntidade;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.DocumentoPdfEntidade;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.InteracaoRelatorioEntidade;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.RelatorioAulaEntidade;
import br.com.felipeblaft.relatorios.saida.persistencia.RelatorioEntidades.VersaoRelatorioEntidade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.core.type.TypeReference;

/** Conversão entre as entidades do relatório de aula e o domínio (seções em jsonb). */
final class RelatorioAulaMapeador {

    private static final TypeReference<List<String>> LISTA = new TypeReference<>() {};

    /** Leitores do conteúdo de versão por tipo de relatório (o semestral registra o seu). */
    static final Map<TipoRelatorio, Function<String, ConteudoVersao>> LEITORES = new HashMap<>();

    static {
        LEITORES.put(
                TipoRelatorio.AULA,
                json -> JsonPersistencia.ler(json, ConteudoRelatorioAula.class));
    }

    private RelatorioAulaMapeador() {}

    static AulaEntidade paraEntidade(Aula aula) {
        AulaEntidade e = new AulaEntidade();
        e.id = aula.id();
        e.professorId = aula.professorId();
        e.alunoId = aula.alunoId();
        e.dataAula = aula.dataAula();
        e.criadoEm = aula.criadoEm();
        return e;
    }

    static Aula paraDominio(AulaEntidade e) {
        return new Aula(e.id, e.professorId, e.alunoId, e.dataAula, e.criadoEm);
    }

    static RelatorioAulaEntidade paraEntidade(RelatorioAula r) {
        RelatorioAulaEntidade e = new RelatorioAulaEntidade();
        e.id = r.id();
        e.aulaId = r.aulaId();
        e.professorId = r.professorId();
        e.alunoId = r.alunoId();
        e.transcricao = r.transcricao();
        e.conteudo = JsonPersistencia.escrever(r.conteudo());
        e.perguntasPendentes = JsonPersistencia.escrever(r.perguntasPendentes());
        e.rodadasPerguntas = r.rodadasPerguntas();
        e.status = r.status().name();
        e.etapaProcessamento = r.etapa() == null ? null : r.etapa().name();
        e.versaoVigente = r.versaoVigente();
        e.canalOrigem = r.canalOrigem().name();
        e.motivoFalha = r.motivoFalha();
        e.criadoEm = r.criadoEm();
        e.atualizadoEm = r.atualizadoEm();
        e.aprovadoEm = r.aprovadoEm();
        e.versaoAprovada = r.versaoAprovada();
        e.canalAprovacao = r.canalAprovacao() == null ? null : r.canalAprovacao().name();
        e.canceladoEm = r.canceladoEm();
        e.tempoProcessamentoMs = r.tempoProcessamentoMs();
        e.bloqueioOtimista = r.versao();
        return e;
    }

    static RelatorioAula paraDominio(RelatorioAulaEntidade e) {
        return new RelatorioAula(
                e.id,
                e.aulaId,
                e.professorId,
                e.alunoId,
                e.transcricao,
                JsonPersistencia.ler(e.conteudo, ConteudoRelatorioAula.class),
                JsonPersistencia.ler(e.perguntasPendentes, LISTA),
                e.rodadasPerguntas,
                StatusRelatorio.valueOf(e.status),
                e.etapaProcessamento == null ? null : EtapaRelatorio.valueOf(e.etapaProcessamento),
                e.versaoVigente,
                Canal.valueOf(e.canalOrigem),
                e.motivoFalha,
                e.criadoEm,
                e.atualizadoEm,
                e.aprovadoEm,
                e.versaoAprovada,
                e.canalAprovacao == null ? null : Canal.valueOf(e.canalAprovacao),
                e.canceladoEm,
                e.tempoProcessamentoMs,
                e.bloqueioOtimista);
    }

    static VersaoRelatorioEntidade paraEntidade(VersaoRelatorio v) {
        VersaoRelatorioEntidade e = new VersaoRelatorioEntidade();
        e.id = v.id();
        e.relatorioTipo = v.relatorioTipo().name();
        e.relatorioId = v.relatorioId();
        e.numero = v.numero();
        e.conteudo = JsonPersistencia.escrever(v.conteudo());
        e.origem = v.origem().name();
        e.interacaoId = v.interacaoId();
        e.canal = v.canal().name();
        e.promptVersao = v.promptVersao();
        e.criadaEm = v.criadaEm();
        return e;
    }

    static VersaoRelatorio paraDominio(VersaoRelatorioEntidade e) {
        TipoRelatorio tipo = TipoRelatorio.valueOf(e.relatorioTipo);
        return new VersaoRelatorio(
                e.id,
                tipo,
                e.relatorioId,
                e.numero,
                LEITORES.get(tipo).apply(e.conteudo),
                OrigemVersao.valueOf(e.origem),
                e.interacaoId,
                Canal.valueOf(e.canal),
                e.promptVersao,
                e.criadaEm);
    }

    static InteracaoRelatorioEntidade paraEntidade(InteracaoRelatorio i) {
        InteracaoRelatorioEntidade e = new InteracaoRelatorioEntidade();
        e.id = i.id();
        e.relatorioTipo = i.relatorioTipo().name();
        e.relatorioId = i.relatorioId();
        e.tipo = i.tipo().name();
        e.canal = i.canal().name();
        e.meio = i.meio().name();
        e.texto = i.texto();
        e.versaoBase = i.versaoBase();
        e.rodada = i.rodada();
        e.resultado = i.resultado().name();
        e.versaoResultante = i.versaoResultante();
        e.criadoEm = i.criadoEm();
        return e;
    }

    static InteracaoRelatorio paraDominio(InteracaoRelatorioEntidade e) {
        return new InteracaoRelatorio(
                e.id,
                TipoRelatorio.valueOf(e.relatorioTipo),
                e.relatorioId,
                TipoInteracao.valueOf(e.tipo),
                Canal.valueOf(e.canal),
                Meio.valueOf(e.meio),
                e.texto,
                e.versaoBase,
                e.rodada,
                ResultadoInteracao.valueOf(e.resultado),
                e.versaoResultante,
                e.criadoEm);
    }

    static DocumentoPdfEntidade paraEntidade(DocumentoPdf d) {
        DocumentoPdfEntidade e = new DocumentoPdfEntidade();
        e.id = d.id();
        e.versaoId = d.versaoId();
        e.tipo = d.tipo().name();
        e.chaveArmazenamento = d.chaveArmazenamento();
        e.tamanhoBytes = d.tamanhoBytes();
        e.sha256 = d.sha256();
        e.geradoEm = d.geradoEm();
        return e;
    }

    static DocumentoPdf paraDominio(DocumentoPdfEntidade e) {
        return new DocumentoPdf(
                e.id,
                e.versaoId,
                TipoDocumento.valueOf(e.tipo),
                e.chaveArmazenamento,
                e.tamanhoBytes,
                e.sha256,
                e.geradoEm);
    }
}
