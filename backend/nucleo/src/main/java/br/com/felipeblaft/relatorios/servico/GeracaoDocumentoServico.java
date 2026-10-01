package br.com.felipeblaft.relatorios.servico;

import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.ArmazenamentoDocumentoPorta;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.Aprovacao;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.DocumentoGerado;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.ModeloPdfRelatorioAula;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.ModeloPdfRelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.SituacaoDocumento;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gera, guarda e registra os PDFs (R-10, R-11): o de conferência de cada versão e o oficial da
 * versão aprovada. A chave de armazenamento é interna: {@code relatorios/<tipo>/<id>/v<n>.pdf}.
 */
public class GeracaoDocumentoServico {

    private final GeracaoPdfPorta geracaoPdf;
    private final ArmazenamentoDocumentoPorta armazenamento;
    private final DocumentoPdfRepositorio documentos;
    private final RelatorioAulaRepositorio relatorios;
    private final AlunoRepositorio alunos;
    private final ProfessorRepositorio professores;
    private final RelogioPorta relogio;

    public GeracaoDocumentoServico(
            GeracaoPdfPorta geracaoPdf,
            ArmazenamentoDocumentoPorta armazenamento,
            DocumentoPdfRepositorio documentos,
            RelatorioAulaRepositorio relatorios,
            AlunoRepositorio alunos,
            ProfessorRepositorio professores,
            RelogioPorta relogio) {
        this.geracaoPdf = Objects.requireNonNull(geracaoPdf);
        this.armazenamento = Objects.requireNonNull(armazenamento);
        this.documentos = Objects.requireNonNull(documentos);
        this.relatorios = Objects.requireNonNull(relatorios);
        this.alunos = Objects.requireNonNull(alunos);
        this.professores = Objects.requireNonNull(professores);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public static String chave(TipoRelatorio tipo, UUID relatorioId, String arquivo) {
        return "relatorios/"
                + tipo.name().toLowerCase(Locale.ROOT)
                + "/"
                + relatorioId
                + "/"
                + arquivo;
    }

    public DocumentoPdf gerarConferencia(RelatorioAula relatorio, VersaoRelatorio versao) {
        return gerarAula(relatorio, versao, SituacaoDocumento.CONFERENCIA, null);
    }

    public DocumentoPdf gerarOficial(RelatorioAula relatorio, VersaoRelatorio versao) {
        Professor professor = professor(relatorio.professorId());
        Aprovacao aprovacao =
                new Aprovacao(professor.nome(), relatorio.aprovadoEm(), relatorio.versaoAprovada());
        return gerarAula(relatorio, versao, SituacaoDocumento.OFICIAL, aprovacao);
    }

    private DocumentoPdf gerarAula(
            RelatorioAula relatorio,
            VersaoRelatorio versao,
            SituacaoDocumento situacao,
            Aprovacao aprovacao) {
        Aula aula = relatorios.buscarAula(relatorio.aulaId()).orElseThrow();
        Aluno aluno = alunos.buscarPorId(relatorio.alunoId()).orElseThrow();
        Professor professor = professor(relatorio.professorId());
        ModeloPdfRelatorioAula modelo =
                new ModeloPdfRelatorioAula(
                        aluno.nome(),
                        professor.nome(),
                        aula.dataAula(),
                        (ConteudoRelatorioAula) versao.conteudo(),
                        versao.numero(),
                        situacao,
                        aprovacao);
        DocumentoGerado gerado = geracaoPdf.gerarRelatorioAula(modelo);
        String arquivo =
                situacao == SituacaoDocumento.OFICIAL
                        ? "oficial.pdf"
                        : "v" + versao.numero() + ".pdf";
        return guardar(TipoRelatorio.AULA, relatorio.id(), versao, situacao, gerado, arquivo);
    }

    public DocumentoPdf gerarConferencia(RelatorioSemestral relatorio, VersaoRelatorio versao) {
        return gerarSemestral(relatorio, versao, SituacaoDocumento.CONFERENCIA, null);
    }

    public DocumentoPdf gerarOficial(RelatorioSemestral relatorio, VersaoRelatorio versao) {
        Professor professor = professor(relatorio.professorId());
        Aprovacao aprovacao =
                new Aprovacao(professor.nome(), relatorio.aprovadoEm(), relatorio.versaoAprovada());
        return gerarSemestral(relatorio, versao, SituacaoDocumento.OFICIAL, aprovacao);
    }

    private DocumentoPdf gerarSemestral(
            RelatorioSemestral relatorio,
            VersaoRelatorio versao,
            SituacaoDocumento situacao,
            Aprovacao aprovacao) {
        ModeloPdfRelatorioSemestral modelo =
                new ModeloPdfRelatorioSemestral(
                        relatorio.informacoesGerais(),
                        (ConteudoRelatorioSemestral) versao.conteudo(),
                        versao.numero(),
                        situacao,
                        aprovacao);
        DocumentoGerado gerado = geracaoPdf.gerarRelatorioSemestral(modelo);
        String arquivo =
                situacao == SituacaoDocumento.OFICIAL
                        ? "oficial.pdf"
                        : "v" + versao.numero() + ".pdf";
        return guardar(TipoRelatorio.SEMESTRAL, relatorio.id(), versao, situacao, gerado, arquivo);
    }

    /** Guarda os bytes e registra o documento (usado também pelo semestral). */
    public DocumentoPdf guardar(
            TipoRelatorio tipo,
            UUID relatorioId,
            VersaoRelatorio versao,
            SituacaoDocumento situacao,
            DocumentoGerado gerado,
            String arquivo) {
        String chave = chave(tipo, relatorioId, arquivo);
        armazenamento.salvar(chave, gerado.bytes());
        DocumentoPdf documento =
                DocumentoPdf.novo(
                        versao.id(),
                        situacao == SituacaoDocumento.OFICIAL
                                ? TipoDocumento.OFICIAL
                                : TipoDocumento.CONFERENCIA,
                        chave,
                        gerado.tamanho(),
                        gerado.sha256(),
                        relogio.agora());
        documentos.salvar(documento);
        return documento;
    }

    public Optional<byte[]> abrir(DocumentoPdf documento) {
        return armazenamento.abrir(documento.chaveArmazenamento());
    }

    private Professor professor(UUID id) {
        return professores.buscarPorId(id).orElseThrow();
    }
}
