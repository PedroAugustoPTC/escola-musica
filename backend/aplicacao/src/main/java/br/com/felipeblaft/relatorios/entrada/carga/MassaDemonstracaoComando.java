package br.com.felipeblaft.relatorios.entrada.carga;

import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.CarregarCadastroInicialCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.OrigemVersao;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.SaidaRelatorioSemestralIa;
import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comando {@code massa --arquivo <cadastros.json>}: massa de DEMONSTRAÇÃO para o ambiente local
 * ({@code local.sh subir --massa=true}). Faz a carga dos cadastros fictícios e cria, pelos mesmos
 * objetos de domínio e pelo mesmo gerador de PDF do sistema, aulas aprovadas no semestre anterior e
 * no atual (PDFs de conferência e oficiais), um semestral aprovado, um semestral em conferência e
 * relatórios de aula em aberto. Só roda com o banco vazio, para nunca misturar dados fictícios com
 * dados reais.
 */
@Component
@Profile("carga")
public class MassaDemonstracaoComando {

    public static final String NOME = "massa";

    private static final String TRANSCRICAO = "Transcrição de demonstração (massa fictícia).";

    /** Plano por aluno: aulas aprovadas no semestre anterior e no atual, e o dia da semana. */
    private record Plano(int anterior, int atual, int deslocamento) {}

    private static final Map<String, Plano> PLANOS =
            Map.of(
                    "Matteo Mafra", new Plano(12, 10, 0),
                    "Olavo Torrente", new Plano(8, 8, 1),
                    "Miguel de Castro Torrente", new Plano(0, 6, 2),
                    "Beatriz Arruda", new Plano(0, 4, 3),
                    "Helena Prado", new Plano(4, 3, 1),
                    "Davi Coutinho", new Plano(0, 2, 4));

    private final CarregarCadastroInicialCasoDeUso carga;
    private final ProfessorRepositorio professores;
    private final AlunoRepositorio alunos;
    private final RelatorioAulaRepositorio relatoriosAula;
    private final VersaoRelatorioRepositorio versoes;
    private final RelatorioSemestralRepositorio semestrais;
    private final GeracaoDocumentoServico documentos;
    private final TransacaoPorta transacao;
    private final RelogioPorta relogio;
    private final FormatacaoMensagens formatacao;
    private final JdbcTemplate jdbc;
    private final JsonMapper json =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @SuppressWarnings("java:S107")
    public MassaDemonstracaoComando(
            CarregarCadastroInicialCasoDeUso carga,
            ProfessorRepositorio professores,
            AlunoRepositorio alunos,
            RelatorioAulaRepositorio relatoriosAula,
            VersaoRelatorioRepositorio versoes,
            RelatorioSemestralRepositorio semestrais,
            GeracaoDocumentoServico documentos,
            TransacaoPorta transacao,
            RelogioPorta relogio,
            FormatacaoMensagens formatacao,
            JdbcTemplate jdbc) {
        this.carga = carga;
        this.professores = professores;
        this.alunos = alunos;
        this.relatoriosAula = relatoriosAula;
        this.versoes = versoes;
        this.semestrais = semestrais;
        this.documentos = documentos;
        this.transacao = transacao;
        this.relogio = relogio;
        this.formatacao = formatacao;
        this.jdbc = jdbc;
    }

    /** Devolve o código de saída do processo (0 = sucesso). */
    public int executar(List<String> argumentos, PrintStream saida) {
        String arquivo = ArgumentosComando.valor(argumentos, "--arquivo");
        if (arquivo == null) {
            saida.println("Uso: massa --arquivo <cadastros.json>");
            return 2;
        }
        Integer existentes = jdbc.queryForObject("select count(*) from professor", Integer.class);
        if (existentes != null && existentes > 0) {
            saida.println(
                    "A massa de demonstração só é criada em banco vazio (há professores cadastrados)."
                            + " Use: local.sh subir --massa=true, que zera os dados locais antes.");
            return 1;
        }
        ArquivoCarga cadastros;
        try {
            cadastros = json.readValue(Files.readString(Path.of(arquivo)), ArquivoCarga.class);
        } catch (Exception erro) {
            saida.println(
                    "Não foi possível ler o arquivo de cadastros (JSON inválido ou ausente).");
            return 1;
        }
        ResultadoCarga resultado = carga.executar(cadastros, false);
        if (!resultado.sucesso()) {
            saida.println("Carga dos cadastros recusada: " + resultado.erros());
            return 1;
        }
        Resumo resumo = gerar(cadastros);
        saida.println("Massa de demonstração criada (dados fictícios).");
        saida.printf(
                "Professores: %d · Alunos: %d%n",
                resultado.professores().criados(), resultado.alunos().criados());
        saida.printf(
                "Relatórios de aula: %d aprovados, %d em aberto · Semestrais: %d aprovado(s), %d em"
                        + " conferência · PDFs gerados: %d%n",
                resumo.aulasAprovadas,
                resumo.aulasEmAberto,
                resumo.semestraisAprovados,
                resumo.semestraisEmConferencia,
                resumo.pdfs);
        saida.println("Códigos de acesso (repasse fora do sistema):");
        for (ResultadoCarga.CodigoEmitido codigo : resultado.codigosEmitidos()) {
            saida.println(ArgumentosComando.linhaCodigo(codigo, formatacao));
        }
        return 0;
    }

    /** Contadores do que foi criado. */
    static final class Resumo {
        int aulasAprovadas;
        int aulasEmAberto;
        int semestraisAprovados;
        int semestraisEmConferencia;
        int pdfs;
    }

    Resumo gerar(ArquivoCarga cadastros) {
        Resumo resumo = new Resumo();
        LocalDate hoje = relogio.hojeNoFusoDaEscola();
        PeriodoSemestral atual = PeriodoSemestral.contendo(hoje);
        PeriodoSemestral anterior = atual.anterior();
        for (ArquivoCarga.ProfessorCarga dados : cadastros.professores()) {
            Professor professor =
                    professores.buscarPorEmail(dados.email().strip().toLowerCase()).orElseThrow();
            List<Aluno> doProfessor =
                    alunos.listarDoProfessor(professor.id()).stream()
                            .sorted(Comparator.comparing(Aluno::nome))
                            .toList();
            for (Aluno aluno : doProfessor) {
                Plano plano = PLANOS.get(aluno.nome());
                if (plano == null) {
                    continue; // sem histórico: serve para testar as telas vazias
                }
                List<LocalDate> datasAnteriores =
                        semanas(
                                anterior.inicio(),
                                anterior.fim(),
                                plano.deslocamento(),
                                plano.anterior());
                List<LocalDate> datasAtuais =
                        semanas(
                                atual.inicio(),
                                hoje.minusDays(7),
                                plano.deslocamento(),
                                plano.atual());
                int indice = 0;
                for (LocalDate data : concatenar(datasAnteriores, datasAtuais)) {
                    aulaAprovada(professor, aluno, data, indice++);
                    resumo.aulasAprovadas++;
                    resumo.pdfs += 2;
                }
                switch (aluno.nome()) {
                    case "Matteo Mafra" -> {
                        semestral(professor, aluno, anterior, datasAnteriores.size(), true);
                        resumo.semestraisAprovados++;
                        resumo.pdfs += 2;
                    }
                    case "Olavo Torrente" -> {
                        semestral(professor, aluno, anterior, datasAnteriores.size(), false);
                        resumo.semestraisEmConferencia++;
                        resumo.pdfs++;
                        aulaEmAberto(professor, aluno, hoje.minusDays(1), indice, false);
                        resumo.aulasEmAberto++;
                        resumo.pdfs++;
                    }
                    case "Miguel de Castro Torrente" -> {
                        aulaEmAberto(professor, aluno, hoje, indice, true);
                        resumo.aulasEmAberto++;
                    }
                    default -> {
                        // só aulas aprovadas
                    }
                }
            }
        }
        return resumo;
    }

    private static List<LocalDate> concatenar(List<LocalDate> a, List<LocalDate> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    /** Uma aula por semana a partir da 2ª semana do período, até o limite e a quantidade. */
    private static List<LocalDate> semanas(
            LocalDate inicio, LocalDate limite, int deslocamento, int quantidade) {
        List<LocalDate> datas = new java.util.ArrayList<>();
        LocalDate data = inicio.plusDays(7L + deslocamento);
        while (datas.size() < quantidade && !data.isAfter(limite)) {
            datas.add(data);
            data = data.plusWeeks(1);
        }
        return datas;
    }

    private Instant as(LocalDate data, int hora, int minuto) {
        return data.atTime(hora, minuto).atZone(relogio.fusoDaEscola()).toInstant();
    }

    private void aulaAprovada(Professor professor, Aluno aluno, LocalDate data, int indice) {
        transacao.executar(
                () -> {
                    Canal canal = indice % 3 == 2 ? Canal.TELEGRAM : Canal.WEB;
                    Instant registrada = as(data, 18, 10);
                    Aula aula = Aula.nova(professor.id(), aluno.id(), data, registrada);
                    relatoriosAula.salvarAula(aula);
                    RelatorioAula relatorio =
                            RelatorioAula.criar(aula, TRANSCRICAO, canal, registrada);
                    relatorio.registrarEstruturacao(
                            ConteudosDemonstracao.aula(aluno.instrumento(), indice),
                            List.of(),
                            as(data, 18, 12));
                    relatorio.aprovar(1, canal, as(data, 18, 40));
                    relatoriosAula.salvar(relatorio);
                    VersaoRelatorio versao = versaoAula(relatorio, canal, registrada);
                    documentos.gerarConferencia(relatorio, versao);
                    documentos.gerarOficial(relatorio, versao);
                });
    }

    private void aulaEmAberto(
            Professor professor, Aluno aluno, LocalDate data, int indice, boolean comPerguntas) {
        transacao.executar(
                () -> {
                    Instant registrada = as(data, 17, 5);
                    Aula aula = Aula.nova(professor.id(), aluno.id(), data, registrada);
                    relatoriosAula.salvarAula(aula);
                    RelatorioAula relatorio =
                            RelatorioAula.criar(aula, TRANSCRICAO, Canal.TELEGRAM, registrada);
                    boolean criarVersao =
                            relatorio.registrarEstruturacao(
                                    ConteudosDemonstracao.aula(aluno.instrumento(), indice),
                                    comPerguntas ? ConteudosDemonstracao.PERGUNTAS : List.of(),
                                    as(data, 17, 7));
                    relatoriosAula.salvar(relatorio);
                    if (criarVersao) {
                        documentos.gerarConferencia(
                                relatorio, versaoAula(relatorio, Canal.TELEGRAM, registrada));
                    }
                });
    }

    private VersaoRelatorio versaoAula(RelatorioAula relatorio, Canal canal, Instant quando) {
        VersaoRelatorio versao =
                VersaoRelatorio.nova(
                        TipoRelatorio.AULA,
                        relatorio.id(),
                        1,
                        relatorio.conteudo(),
                        OrigemVersao.ESTRUTURACAO_INICIAL,
                        null,
                        canal,
                        "estruturacao-aula.v1",
                        quando);
        versoes.salvar(versao);
        return versao;
    }

    private void semestral(
            Professor professor,
            Aluno aluno,
            PeriodoSemestral periodo,
            int quantidadeAulas,
            boolean aprovar) {
        if (quantidadeAulas == 0) {
            return;
        }
        transacao.executar(
                () -> {
                    LocalDate geradoEm = periodo.fim().plusDays(3);
                    Instant consolidado = as(geradoEm, 10, 0);
                    InformacoesGerais informacoes =
                            InformacoesGerais.calcular(
                                    aluno.nome(),
                                    aluno.dataNascimento(),
                                    aluno.instrumento(),
                                    professor.nome(),
                                    periodo,
                                    quantidadeAulas,
                                    geradoEm);
                    RelatorioSemestral relatorio =
                            RelatorioSemestral.criar(
                                    professor.id(),
                                    aluno.id(),
                                    periodo,
                                    quantidadeAulas,
                                    0,
                                    informacoes,
                                    conteudoSemestral().conteudo(),
                                    Canal.WEB,
                                    consolidado);
                    if (aprovar) {
                        relatorio.aprovar(1, Canal.WEB, as(geradoEm, 10, 30));
                    }
                    semestrais.salvar(relatorio);
                    VersaoRelatorio versao =
                            VersaoRelatorio.nova(
                                    TipoRelatorio.SEMESTRAL,
                                    relatorio.id(),
                                    1,
                                    relatorio.conteudo(),
                                    OrigemVersao.CONSOLIDACAO,
                                    null,
                                    Canal.WEB,
                                    "consolidacao-semestral.v1",
                                    consolidado);
                    versoes.salvar(versao);
                    documentos.gerarConferencia(relatorio, versao);
                    if (aprovar) {
                        documentos.gerarOficial(relatorio, versao);
                    }
                });
    }

    /** O mesmo conteúdo semestral da IA simulada (ia/simulado/consolidacao-semestral.json). */
    private SaidaRelatorioSemestralIa conteudoSemestral() {
        try (InputStream entrada =
                MassaDemonstracaoComando.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/consolidacao-semestral.json")) {
            return json.readValue(entrada, SaidaRelatorioSemestralIa.class);
        } catch (java.io.IOException erro) {
            throw new java.io.UncheckedIOException(erro);
        }
    }
}
