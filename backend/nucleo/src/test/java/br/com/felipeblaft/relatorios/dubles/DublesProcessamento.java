package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.Aula;
import br.com.felipeblaft.relatorios.dominio.relatorio.DocumentoPdf;
import br.com.felipeblaft.relatorios.dominio.relatorio.InteracaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.RelatorioAula;
import br.com.felipeblaft.relatorios.dominio.relatorio.TipoDocumento;
import br.com.felipeblaft.relatorios.dominio.relatorio.VersaoRelatorio;
import br.com.felipeblaft.relatorios.dominio.trabalho.ChamadaIa;
import br.com.felipeblaft.relatorios.dominio.trabalho.StatusTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.ArmazenamentoDocumentoPorta;
import br.com.felipeblaft.relatorios.porta.ArquivoCanalPorta;
import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.AudioNormalizado;
import br.com.felipeblaft.relatorios.porta.ConflitoConcorrenciaExcecao;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import br.com.felipeblaft.relatorios.porta.EntradaTarefa;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.ModeloPdfRelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.LlmPorta;
import br.com.felipeblaft.relatorios.porta.NormalizacaoAudioPorta;
import br.com.felipeblaft.relatorios.porta.NotificacaoProfessorPorta;
import br.com.felipeblaft.relatorios.porta.ReferenciaArquivoCanal;
import br.com.felipeblaft.relatorios.porta.RegistroCustoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio.AulaAprovada;
import br.com.felipeblaft.relatorios.porta.RespostaLlm;
import br.com.felipeblaft.relatorios.porta.ResultadoTranscricao;
import br.com.felipeblaft.relatorios.porta.TarefaLlm;
import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import br.com.felipeblaft.relatorios.porta.TranscricaoPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Dublês escritos à mão para as portas de processamento, IA, PDF e fila (sem Mockito). */
public final class DublesProcessamento {

    private DublesProcessamento() {}

    /** Transcrição com respostas programadas em ordem (texto ou exceção). */
    public static class TranscricaoRoteirizada implements TranscricaoPorta {
        private final Deque<Object> roteiro = new ArrayDeque<>();
        public final List<AudioNormalizado> recebidos = new ArrayList<>();

        public TranscricaoRoteirizada programar(Object... respostas) {
            roteiro.addAll(List.of(respostas));
            return this;
        }

        @Override
        public ResultadoTranscricao transcrever(AudioNormalizado audio) {
            recebidos.add(audio);
            Object proxima = roteiro.isEmpty() ? "transcrição padrão da aula" : roteiro.poll();
            if (proxima instanceof RuntimeException erro) {
                throw erro;
            }
            return new ResultadoTranscricao(
                    (String) proxima,
                    audio.duracaoSegundos(),
                    new BigDecimal("0.001"),
                    "provedor/modelo");
        }
    }

    /** Normalização que devolve um caminho derivado e registra o que foi apagado. */
    public static class NormalizacaoAudioFalsa implements NormalizacaoAudioPorta {
        private final Deque<RuntimeException> falhas = new ArrayDeque<>();
        public final List<Path> apagados = new ArrayList<>();
        public final List<Path> normalizados = new ArrayList<>();

        public void falharCom(RuntimeException erro) {
            falhas.add(erro);
        }

        @Override
        public AudioNormalizado normalizar(ArquivoTemporario original) {
            if (!falhas.isEmpty()) {
                throw falhas.poll();
            }
            Path caminho = Path.of(original.caminho() + ".normalizado.ogg");
            normalizados.add(caminho);
            return new AudioNormalizado(caminho, 42.0);
        }

        @Override
        public void apagar(Path caminho) {
            apagados.add(caminho);
        }

        /** Todo arquivo criado ou recebido foi apagado (FR-018). */
        public boolean apagou(Path original) {
            return apagados.contains(original);
        }
    }

    public static class ArquivoCanalFalso implements ArquivoCanalPorta {
        public final List<ReferenciaArquivoCanal> obtidos = new ArrayList<>();

        @Override
        public ArquivoTemporario obterArquivo(ReferenciaArquivoCanal referencia) {
            obtidos.add(referencia);
            return new ArquivoTemporario(
                    Path.of("/tmp/audios/canal-" + referencia.id()),
                    referencia.tamanho(),
                    referencia.mime());
        }
    }

    /** LLM com respostas programadas por tarefa; registra as entradas recebidas. */
    public static class LlmRoteirizado implements LlmPorta {
        private final Map<TarefaLlm, Deque<Object>> roteiro = new EnumMap<>(TarefaLlm.class);
        public final List<EntradaTarefa> entradas = new ArrayList<>();

        public LlmRoteirizado programar(TarefaLlm tarefa, Object... respostas) {
            roteiro.computeIfAbsent(tarefa, t -> new ArrayDeque<>()).addAll(List.of(respostas));
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> RespostaLlm<T> gerarJson(
                TarefaLlm tarefa, EntradaTarefa entrada, Class<T> tipoSaida) {
            entradas.add(entrada);
            Deque<Object> fila = roteiro.get(tarefa);
            if (fila == null || fila.isEmpty()) {
                throw new IllegalStateException("sem resposta programada para " + tarefa);
            }
            Object proxima = fila.poll();
            if (proxima instanceof RuntimeException erro) {
                throw erro;
            }
            return new RespostaLlm<>(
                    (T) proxima,
                    tarefa.name().toLowerCase().replace('_', '-') + ".v1",
                    100,
                    50,
                    new BigDecimal("0.0001"),
                    "provedor/modelo",
                    10);
        }
    }

    public static class GeracaoPdfFalsa implements GeracaoPdfPorta {
        public final List<ModeloPdfRelatorioAula> modelos = new ArrayList<>();

        @Override
        public DocumentoGerado gerarRelatorioAula(ModeloPdfRelatorioAula modelo) {
            modelos.add(modelo);
            byte[] bytes =
                    ("PDF " + modelo.situacao() + " v" + modelo.versao())
                            .getBytes(StandardCharsets.UTF_8);
            return new DocumentoGerado(bytes, "sha-" + modelos.size(), bytes.length);
        }

        public final List<ModeloPdfRelatorioSemestral> modelosSemestrais = new ArrayList<>();

        @Override
        public DocumentoGerado gerarRelatorioSemestral(ModeloPdfRelatorioSemestral modelo) {
            modelosSemestrais.add(modelo);
            byte[] bytes =
                    ("PDF SEMESTRAL " + modelo.situacao() + " v" + modelo.versao())
                            .getBytes(StandardCharsets.UTF_8);
            return new DocumentoGerado(bytes, "sha-s" + modelosSemestrais.size(), bytes.length);
        }
    }

    public static class ArmazenamentoDocumentoEmMemoria implements ArmazenamentoDocumentoPorta {
        public final Map<String, byte[]> arquivos = new LinkedHashMap<>();

        @Override
        public void salvar(String chave, byte[] conteudo) {
            arquivos.put(chave, conteudo);
        }

        @Override
        public Optional<byte[]> abrir(String chave) {
            return Optional.ofNullable(arquivos.get(chave));
        }

        @Override
        public boolean existe(String chave) {
            return arquivos.containsKey(chave);
        }
    }

    public static class NotificacaoRegistradora implements NotificacaoProfessorPorta {
        public final List<String> eventos = new ArrayList<>();

        @Override
        public void notificarEtapa(TrabalhoProcessamento trabalho) {
            eventos.add("etapa:" + trabalho.etapa());
        }

        @Override
        public void notificarResultado(TrabalhoProcessamento trabalho) {
            eventos.add("resultado:" + trabalho.resultado());
        }

        @Override
        public void avisarDemora(TrabalhoProcessamento trabalho) {
            eventos.add("demora");
        }
    }

    public static class RegistroCustoEmMemoria implements RegistroCustoPorta {
        public final List<ChamadaIa> chamadas = new ArrayList<>();

        @Override
        public void registrar(ChamadaIa chamada) {
            chamadas.add(chamada);
        }
    }

    /** Executa o trecho diretamente (os repositórios em memória não têm transação). */
    public static class TransacaoImediata implements TransacaoPorta {
        public int transacoes;

        @Override
        public <T> T executar(Supplier<T> trecho) {
            transacoes++;
            return trecho.get();
        }
    }

    public static class FilaTrabalhoEmMemoria implements FilaTrabalhoPorta {
        public final Map<UUID, TrabalhoProcessamento> trabalhos = new LinkedHashMap<>();

        @Override
        public void enfileirar(TrabalhoProcessamento trabalho) {
            if (trabalho.tipo() == TipoTrabalho.REGISTRO_AULA
                    && buscarRegistroEmCurso(trabalho.professorId(), trabalho.alunoId())
                            .isPresent()) {
                throw new ConflitoConcorrenciaExcecao("registro em curso");
            }
            trabalhos.put(trabalho.id(), trabalho);
        }

        @Override
        public void atualizar(TrabalhoProcessamento trabalho) {
            TrabalhoProcessamento atual = trabalhos.get(trabalho.id());
            if (atual != null && atual != trabalho && atual.status() == StatusTrabalho.DESCARTADO) {
                return;
            }
            trabalhos.put(trabalho.id(), trabalho);
        }

        @Override
        public Optional<TrabalhoProcessamento> buscarPorId(UUID id) {
            return Optional.ofNullable(trabalhos.get(id));
        }

        @Override
        public Optional<TrabalhoProcessamento> buscarConsolidacaoEmCurso(
                UUID professorId, UUID alunoId, String periodoChave) {
            return trabalhos.values().stream()
                    .filter(t -> t.tipo() == TipoTrabalho.CONSOLIDACAO_SEMESTRAL)
                    .filter(t -> t.professorId().equals(professorId) && t.alunoId().equals(alunoId))
                    .filter(
                            t ->
                                    periodoChave.equals(
                                            t.parametro(TrabalhoProcessamento.PERIODO_CHAVE)
                                                    .orElse(null)))
                    .filter(t -> t.status().emCurso())
                    .findFirst();
        }

        @Override
        public Optional<TrabalhoProcessamento> reservarProximo(Instant agora) {
            Optional<TrabalhoProcessamento> proximo =
                    trabalhos.values().stream()
                            .filter(t -> t.status() == StatusTrabalho.PENDENTE)
                            .findFirst();
            proximo.ifPresent(t -> t.iniciar(agora));
            return proximo;
        }

        @Override
        public Optional<TrabalhoProcessamento> buscarEmCursoDoRelatorio(
                TipoRelatorio tipo, UUID relatorioId) {
            return trabalhos.values().stream()
                    .filter(t -> t.status().emCurso())
                    .filter(t -> tipo == t.relatorioTipo() && relatorioId.equals(t.relatorioId()))
                    .findFirst();
        }

        @Override
        public Optional<TrabalhoProcessamento> buscarRegistroEmCurso(
                UUID professorId, UUID alunoId) {
            return trabalhos.values().stream()
                    .filter(t -> t.status().emCurso())
                    .filter(t -> t.relatorioId() == null)
                    .filter(t -> t.professorId().equals(professorId) && t.alunoId().equals(alunoId))
                    .findFirst();
        }

        @Override
        public boolean marcarAvisoDemora(UUID trabalhoId) {
            TrabalhoProcessamento trabalho = trabalhos.get(trabalhoId);
            if (trabalho == null || trabalho.avisoDemoraEnviado()) {
                return false;
            }
            trabalho.marcarAvisoDemora();
            return true;
        }

        @Override
        public List<TrabalhoProcessamento> listarDemoradosSemAviso(Instant criadosAntesDe) {
            return trabalhos.values().stream()
                    .filter(t -> t.status().emCurso() && !t.avisoDemoraEnviado())
                    .filter(t -> t.criadoEm().isBefore(criadosAntesDe))
                    .toList();
        }
    }

    public static class RelatorioAulaRepositorioEmMemoria implements RelatorioAulaRepositorio {
        public final Map<UUID, Aula> aulas = new LinkedHashMap<>();
        public final Map<UUID, RelatorioAula> relatorios = new LinkedHashMap<>();
        public int gravacoes;

        @Override
        public void salvarAula(Aula aula) {
            aulas.put(aula.id(), aula);
        }

        @Override
        public Optional<Aula> buscarAula(UUID aulaId) {
            return Optional.ofNullable(aulas.get(aulaId));
        }

        @Override
        public void salvar(RelatorioAula relatorio) {
            gravacoes++;
            relatorios.put(relatorio.id(), relatorio);
        }

        @Override
        public Optional<RelatorioAula> buscarPorId(UUID id) {
            return Optional.ofNullable(relatorios.get(id));
        }

        @Override
        public Optional<RelatorioAula> buscarParaAtualizar(UUID id) {
            return buscarPorId(id);
        }

        @Override
        public Optional<RelatorioAula> buscarEmAbertoDoAluno(UUID professorId, UUID alunoId) {
            return relatorios.values().stream()
                    .filter(r -> r.status().emAberto())
                    .filter(r -> r.professorId().equals(professorId) && r.alunoId().equals(alunoId))
                    .findFirst();
        }

        private java.util.stream.Stream<RelatorioAula> doPar(UUID professorId, UUID alunoId) {
            return relatorios.values().stream()
                    .filter(
                            r ->
                                    r.professorId().equals(professorId)
                                            && r.alunoId().equals(alunoId));
        }

        private java.time.LocalDate data(RelatorioAula relatorio) {
            return aulas.get(relatorio.aulaId()).dataAula();
        }

        @Override
        public List<AulaAprovada> listarAprovadosNoPeriodo(
                UUID professorId,
                UUID alunoId,
                java.time.LocalDate inicio,
                java.time.LocalDate fim) {
            return doPar(professorId, alunoId)
                    .filter(
                            r ->
                                    r.status()
                                            == br.com.felipeblaft.relatorios.dominio.relatorio
                                                    .StatusRelatorio.APROVADO)
                    .filter(r -> !data(r).isBefore(inicio) && !data(r).isAfter(fim))
                    .sorted(java.util.Comparator.comparing(this::data))
                    .map(r -> new AulaAprovada(r.id(), data(r), r.conteudo(), r.aprovadoEm()))
                    .toList();
        }

        @Override
        public int contarEmAbertoNoPeriodo(
                UUID professorId,
                UUID alunoId,
                java.time.LocalDate inicio,
                java.time.LocalDate fim) {
            return (int)
                    doPar(professorId, alunoId)
                            .filter(r -> r.status().emAberto())
                            .filter(r -> !data(r).isBefore(inicio) && !data(r).isAfter(fim))
                            .count();
        }

        @Override
        public Optional<java.time.LocalDate> dataPrimeiraAula(UUID professorId, UUID alunoId) {
            return aulas.values().stream()
                    .filter(a -> a.professorId().equals(professorId) && a.alunoId().equals(alunoId))
                    .map(Aula::dataAula)
                    .min(java.util.Comparator.naturalOrder());
        }
    }

    public static class RelatorioSemestralRepositorioEmMemoria
            implements br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio {
        public final Map<UUID, br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral>
                relatorios = new LinkedHashMap<>();

        @Override
        public void salvar(
                br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral relatorio) {
            boolean conflito =
                    relatorios.values().stream()
                            .filter(r -> !r.id().equals(relatorio.id()))
                            .filter(
                                    r ->
                                            r.professorId().equals(relatorio.professorId())
                                                    && r.alunoId().equals(relatorio.alunoId())
                                                    && r.periodo().equals(relatorio.periodo()))
                            .anyMatch(
                                    r ->
                                            r.status() == relatorio.status()
                                                    && (r.status()
                                                                    == br.com.felipeblaft.relatorios
                                                                            .dominio.relatorio
                                                                            .StatusRelatorio
                                                                            .APROVADO
                                                            || r.status()
                                                                    == br.com.felipeblaft.relatorios
                                                                            .dominio.relatorio
                                                                            .StatusRelatorio
                                                                            .AGUARDANDO_CONFERENCIA));
            if (conflito) {
                throw new ConflitoConcorrenciaExcecao("relatorio_semestral");
            }
            relatorios.put(relatorio.id(), relatorio);
        }

        @Override
        public Optional<br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral>
                buscarPorId(UUID id) {
            return Optional.ofNullable(relatorios.get(id));
        }

        @Override
        public Optional<br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral>
                buscarParaAtualizar(UUID id) {
            return buscarPorId(id);
        }

        private Optional<br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral> doTrio(
                UUID professorId,
                UUID alunoId,
                String periodoChave,
                java.util.function.Predicate<
                                br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio>
                        status) {
            return relatorios.values().stream()
                    .filter(r -> r.professorId().equals(professorId) && r.alunoId().equals(alunoId))
                    .filter(r -> r.periodo().chave().equals(periodoChave))
                    .filter(r -> status.test(r.status()))
                    .findFirst();
        }

        @Override
        public Optional<br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral>
                buscarAprovado(UUID professorId, UUID alunoId, String periodoChave) {
            return doTrio(
                    professorId,
                    alunoId,
                    periodoChave,
                    s ->
                            s
                                    == br.com.felipeblaft.relatorios.dominio.relatorio
                                            .StatusRelatorio.APROVADO);
        }

        @Override
        public Optional<br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral>
                buscarEmAberto(UUID professorId, UUID alunoId, String periodoChave) {
            return doTrio(
                    professorId,
                    alunoId,
                    periodoChave,
                    br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio::emAberto);
        }

        @Override
        public List<br.com.felipeblaft.relatorios.dominio.semestral.RelatorioSemestral>
                listarAprovados(UUID professorId, UUID alunoId) {
            return relatorios.values().stream()
                    .filter(r -> r.professorId().equals(professorId) && r.alunoId().equals(alunoId))
                    .filter(
                            r ->
                                    r.status()
                                            == br.com.felipeblaft.relatorios.dominio.relatorio
                                                    .StatusRelatorio.APROVADO)
                    .toList();
        }
    }

    public static class VersaoRelatorioRepositorioEmMemoria implements VersaoRelatorioRepositorio {
        public final List<VersaoRelatorio> versoes = new ArrayList<>();

        @Override
        public void salvar(VersaoRelatorio versao) {
            if (buscar(versao.relatorioTipo(), versao.relatorioId(), versao.numero()).isPresent()) {
                throw new IllegalStateException("versão imutável já existe");
            }
            versoes.add(versao);
        }

        @Override
        public Optional<VersaoRelatorio> buscar(TipoRelatorio tipo, UUID relatorioId, int numero) {
            return versoes.stream()
                    .filter(
                            v ->
                                    v.relatorioTipo() == tipo
                                            && v.relatorioId().equals(relatorioId)
                                            && v.numero() == numero)
                    .findFirst();
        }
    }

    public static class InteracaoRelatorioRepositorioEmMemoria
            implements InteracaoRelatorioRepositorio {
        public final List<InteracaoRelatorio> interacoes = new ArrayList<>();

        @Override
        public void salvar(InteracaoRelatorio interacao) {
            interacoes.add(interacao);
        }

        @Override
        public Optional<InteracaoRelatorio> buscarPorId(UUID id) {
            return interacoes.stream().filter(i -> i.id().equals(id)).findFirst();
        }

        @Override
        public Optional<InteracaoRelatorio> ultima(TipoRelatorio tipo, UUID relatorioId) {
            List<InteracaoRelatorio> doRelatorio =
                    interacoes.stream()
                            .filter(
                                    i ->
                                            i.relatorioTipo() == tipo
                                                    && i.relatorioId().equals(relatorioId))
                            .toList();
            return doRelatorio.isEmpty() ? Optional.empty() : Optional.of(doRelatorio.getLast());
        }
    }

    public static class DocumentoPdfRepositorioEmMemoria implements DocumentoPdfRepositorio {
        public final Map<String, DocumentoPdf> documentos = new HashMap<>();

        @Override
        public void salvar(DocumentoPdf documento) {
            documentos.put(documento.versaoId() + ":" + documento.tipo(), documento);
        }

        @Override
        public Optional<DocumentoPdf> buscar(UUID versaoId, TipoDocumento tipo) {
            return Optional.ofNullable(documentos.get(versaoId + ":" + tipo));
        }
    }
}
