package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import br.com.felipeblaft.reports.port.ChannelFilePort;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.LessonReportRepository.ApprovedLesson;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.PdfGenerationPort;
import br.com.felipeblaft.reports.port.PdfGenerationPort.SemesterReportPdfModel;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.port.TranscriptionResult;
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
public final class ProcessingDoubles {

    private ProcessingDoubles() {}

    /** Transcrição com respostas programadas em ordem (texto ou exceção). */
    public static class ScriptedTranscription implements TranscriptionPort {
        private final Deque<Object> script = new ArrayDeque<>();
        public final List<NormalizedAudio> received = new ArrayList<>();

        public ScriptedTranscription schedule(Object... responses) {
            script.addAll(List.of(responses));
            return this;
        }

        @Override
        public TranscriptionResult transcribe(NormalizedAudio audio) {
            received.add(audio);
            Object next = script.isEmpty() ? "transcrição padrão da aula" : script.poll();
            if (next instanceof RuntimeException error) {
                throw error;
            }
            return new TranscriptionResult(
                    (String) next,
                    audio.durationSeconds(),
                    new BigDecimal("0.001"),
                    "provider/model");
        }
    }

    /** Normalização que devolve um caminho derivado e registra o que foi apagado. */
    public static class FakeAudioNormalization implements AudioNormalizationPort {
        private final Deque<RuntimeException> failures = new ArrayDeque<>();
        public final List<Path> deleted = new ArrayList<>();
        public final List<Path> normalized = new ArrayList<>();

        public void failWith(RuntimeException error) {
            failures.add(error);
        }

        @Override
        public NormalizedAudio normalize(TemporaryFile original) {
            if (!failures.isEmpty()) {
                throw failures.poll();
            }
            Path path = Path.of(original.path() + ".normalized.ogg");
            normalized.add(path);
            return new NormalizedAudio(path, 42.0);
        }

        @Override
        public void delete(Path path) {
            deleted.add(path);
        }

        /** Todo arquivo criado ou recebido foi apagado (FR-018). */
        public boolean deleted(Path original) {
            return deleted.contains(original);
        }
    }

    public static class FakeChannelFile implements ChannelFilePort {
        public final List<ChannelFileReference> obtained = new ArrayList<>();

        @Override
        public TemporaryFile getFile(ChannelFileReference reference) {
            obtained.add(reference);
            return new TemporaryFile(
                    Path.of("/tmp/audios/channel-" + reference.id()),
                    reference.size(),
                    reference.mime());
        }
    }

    /** LLM com respostas programadas por tarefa; registra as entradas recebidas. */
    public static class ScriptedLlm implements LlmPort {
        private final Map<TaskLlm, Deque<Object>> script = new EnumMap<>(TaskLlm.class);
        public final List<TaskInput> inputs = new ArrayList<>();

        public ScriptedLlm schedule(TaskLlm task, Object... responses) {
            script.computeIfAbsent(task, t -> new ArrayDeque<>()).addAll(List.of(responses));
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseLlm<T> generateJson(TaskLlm task, TaskInput input, Class<T> outputType) {
            inputs.add(input);
            Deque<Object> queue = script.get(task);
            if (queue == null || queue.isEmpty()) {
                throw new IllegalStateException("sem resposta programada para " + task);
            }
            Object next = queue.poll();
            if (next instanceof RuntimeException error) {
                throw error;
            }
            return new ResponseLlm<>(
                    (T) next,
                    task.name().toLowerCase().replace('_', '-') + ".v1",
                    100,
                    50,
                    new BigDecimal("0.0001"),
                    "provider/model",
                    10);
        }
    }

    public static class FakePdfGeneration implements PdfGenerationPort {
        public final List<LessonReportPdfModel> models = new ArrayList<>();

        @Override
        public GeneratedDocument generateLessonReport(LessonReportPdfModel model) {
            models.add(model);
            byte[] bytes =
                    ("PDF " + model.status() + " v" + model.version())
                            .getBytes(StandardCharsets.UTF_8);
            return new GeneratedDocument(bytes, "sha-" + models.size(), bytes.length);
        }

        public final List<SemesterReportPdfModel> semesterModels = new ArrayList<>();

        @Override
        public GeneratedDocument generateSemesterReport(SemesterReportPdfModel model) {
            semesterModels.add(model);
            byte[] bytes =
                    ("PDF SEMESTER " + model.status() + " v" + model.version())
                            .getBytes(StandardCharsets.UTF_8);
            return new GeneratedDocument(bytes, "sha-s" + semesterModels.size(), bytes.length);
        }
    }

    public static class InMemoryDocumentStorage implements DocumentStoragePort {
        public final Map<String, byte[]> files = new LinkedHashMap<>();

        @Override
        public void save(String key, byte[] content) {
            files.put(key, content);
        }

        @Override
        public Optional<byte[]> open(String key) {
            return Optional.ofNullable(files.get(key));
        }

        @Override
        public boolean exists(String key) {
            return files.containsKey(key);
        }

        /** Chaves cuja remoção deve falhar (simula falha de E/S). */
        public final java.util.Set<String> failingDeletes = new java.util.HashSet<>();

        @Override
        public void delete(String key) {
            if (failingDeletes.contains(key)) {
                throw new IllegalStateException("falha simulada de remoção");
            }
            files.remove(key);
        }
    }

    public static class RecordingNotification implements TeacherNotificationPort {
        public final List<String> events = new ArrayList<>();

        @Override
        public void notifyStep(ProcessingJob job) {
            events.add("step:" + job.step());
        }

        @Override
        public void notifyResult(ProcessingJob job) {
            events.add("result:" + job.result());
        }

        @Override
        public void notifyDelay(ProcessingJob job) {
            events.add("delay");
        }
    }

    public static class InMemoryCostRecord implements CostRecordPort {
        public final List<AiCall> calls = new ArrayList<>();

        @Override
        public void register(AiCall call) {
            calls.add(call);
        }

        @Override
        public void linkReport(UUID jobId, ReportType type, UUID reportId) {
            calls.replaceAll(
                    c ->
                            c.jobId().equals(jobId) && c.reportId() == null
                                    ? c.withReport(type, reportId)
                                    : c);
        }
    }

    /** Executa o trecho diretamente (os repositórios em memória não têm transação). */
    public static class ImmediateTransaction implements TransactionPort {
        public int transactions;

        @Override
        public <T> T run(Supplier<T> excerpt) {
            transactions++;
            return excerpt.get();
        }

        @Override
        public <T> T runAndRollBack(Supplier<T> excerpt) {
            throw new UnsupportedOperationException("F-TEST completa o dublê (T020)");
        }
    }

    public static class InMemoryJobQueue implements JobQueuePort {
        public final Map<UUID, ProcessingJob> jobs = new LinkedHashMap<>();

        @Override
        public void enqueue(ProcessingJob job) {
            if (job.type() == JobType.LESSON_REGISTRATION
                    && searchRegistrationInProgress(job.teacherId(), job.studentId()).isPresent()) {
                throw new ConcurrencyConflictException("registro em curso");
            }
            jobs.put(job.id(), job);
        }

        @Override
        public void update(ProcessingJob job) {
            ProcessingJob current = jobs.get(job.id());
            if (current != null && current != job && current.status() == JobStatus.DISCARDED) {
                return;
            }
            jobs.put(job.id(), job);
        }

        @Override
        public Optional<ProcessingJob> searchById(UUID id) {
            return Optional.ofNullable(jobs.get(id));
        }

        @Override
        public Optional<ProcessingJob> searchConsolidationInProgress(
                UUID teacherId, UUID studentId, String periodKey) {
            return jobs.values().stream()
                    .filter(t -> t.type() == JobType.SEMESTER_CONSOLIDATION)
                    .filter(t -> t.teacherId().equals(teacherId) && t.studentId().equals(studentId))
                    .filter(
                            t ->
                                    periodKey.equals(
                                            t.parameter(ProcessingJob.PERIOD_KEY).orElse(null)))
                    .filter(t -> t.status().inProgress())
                    .findFirst();
        }

        @Override
        public Optional<ProcessingJob> reserveNext(Instant now) {
            Optional<ProcessingJob> next =
                    jobs.values().stream().filter(t -> t.status() == JobStatus.PENDING).findFirst();
            next.ifPresent(t -> t.start(now));
            return next;
        }

        @Override
        public Optional<ProcessingJob> searchInProgressByReport(ReportType type, UUID reportId) {
            return jobs.values().stream()
                    .filter(t -> t.status().inProgress())
                    .filter(t -> type == t.reportType() && reportId.equals(t.reportId()))
                    .findFirst();
        }

        @Override
        public Optional<ProcessingJob> searchRegistrationInProgress(
                UUID teacherId, UUID studentId) {
            return jobs.values().stream()
                    .filter(t -> t.status().inProgress())
                    .filter(t -> t.reportId() == null)
                    .filter(t -> t.teacherId().equals(teacherId) && t.studentId().equals(studentId))
                    .findFirst();
        }

        @Override
        public void registerStepMessage(UUID jobId, long messageId) {
            ProcessingJob job = jobs.get(jobId);
            if (job != null) {
                job.defineParameter(ProcessingJob.STEP_MESSAGE, Long.toString(messageId));
            }
        }

        @Override
        public boolean markDelayNotice(UUID jobId) {
            ProcessingJob job = jobs.get(jobId);
            if (job == null || job.delayNoticeSent()) {
                return false;
            }
            job.markDelayNotice();
            return true;
        }

        @Override
        public List<ProcessingJob> listDelayedWithoutNotice(Instant createdBefore) {
            return jobs.values().stream()
                    .filter(t -> t.status().inProgress() && !t.delayNoticeSent())
                    .filter(t -> t.createdAt().isBefore(createdBefore))
                    .toList();
        }
    }

    public static class InMemoryLessonReportRepository implements LessonReportRepository {
        public final Map<UUID, Lesson> lessons = new LinkedHashMap<>();
        public final Map<UUID, LessonReport> reports = new LinkedHashMap<>();
        public int recordings;

        @Override
        public void saveLesson(Lesson lesson) {
            lessons.put(lesson.id(), lesson);
        }

        @Override
        public Optional<Lesson> searchLesson(UUID lessonId) {
            return Optional.ofNullable(lessons.get(lessonId));
        }

        @Override
        public void save(LessonReport report) {
            recordings++;
            reports.put(report.id(), report);
        }

        @Override
        public Optional<LessonReport> searchById(UUID id) {
            return Optional.ofNullable(reports.get(id));
        }

        @Override
        public Optional<LessonReport> searchForUpdate(UUID id) {
            return searchById(id);
        }

        @Override
        public Optional<LessonReport> searchOpenByStudent(UUID teacherId, UUID studentId) {
            return reports.values().stream()
                    .filter(r -> r.status().isOpen())
                    .filter(r -> r.teacherId().equals(teacherId) && r.studentId().equals(studentId))
                    .findFirst();
        }

        private java.util.stream.Stream<LessonReport> forPair(UUID teacherId, UUID studentId) {
            return reports.values().stream()
                    .filter(
                            r ->
                                    r.teacherId().equals(teacherId)
                                            && r.studentId().equals(studentId));
        }

        private java.time.LocalDate data(LessonReport report) {
            return lessons.get(report.lessonId()).lessonDate();
        }

        @Override
        public List<ApprovedLesson> listApprovedInPeriod(
                UUID teacherId,
                UUID studentId,
                java.time.LocalDate start,
                java.time.LocalDate end) {
            return forPair(teacherId, studentId)
                    .filter(
                            r ->
                                    r.status()
                                            == br.com.felipeblaft.reports.domain.report.ReportStatus
                                                    .APPROVED)
                    .filter(r -> !data(r).isBefore(start) && !data(r).isAfter(end))
                    .sorted(java.util.Comparator.comparing(this::data))
                    .map(r -> new ApprovedLesson(r.id(), data(r), r.content(), r.approvedAt()))
                    .toList();
        }

        @Override
        public List<ApprovedLesson> listApproved(UUID teacherId, UUID studentId) {
            return listApprovedInPeriod(
                    teacherId, studentId, java.time.LocalDate.MIN, java.time.LocalDate.MAX);
        }

        @Override
        public int countOpenInPeriod(
                UUID teacherId,
                UUID studentId,
                java.time.LocalDate start,
                java.time.LocalDate end) {
            return (int)
                    forPair(teacherId, studentId)
                            .filter(r -> r.status().isOpen())
                            .filter(r -> !data(r).isBefore(start) && !data(r).isAfter(end))
                            .count();
        }

        @Override
        public List<UUID> findRetentionCandidates(
                br.com.felipeblaft.reports.domain.privacy.RetentionPolicy policy,
                Instant now,
                int limit,
                UUID afterId) {
            return reports.values().stream()
                    .filter(
                            r ->
                                    r.retentionAction(policy, now)
                                            != br.com.felipeblaft.reports.domain.privacy
                                                    .RetentionDecision.KEEP)
                    .map(LessonReport::id)
                    .filter(id -> afterId == null || id.compareTo(afterId) > 0)
                    .sorted()
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<UUID> findIdsByStudent(UUID studentId) {
            return reports.values().stream()
                    .filter(r -> r.studentId().equals(studentId))
                    .map(LessonReport::id)
                    .sorted()
                    .toList();
        }

        @Override
        public Map<String, Integer> countByStudentAndStatus(UUID studentId) {
            Map<String, Integer> counts = new java.util.TreeMap<>();
            reports.values().stream()
                    .filter(r -> r.studentId().equals(studentId))
                    .forEach(r -> counts.merge(r.status().name(), 1, Integer::sum));
            return counts;
        }

        @Override
        public Optional<java.time.LocalDate> lessonFirstDate(UUID teacherId, UUID studentId) {
            return lessons.values().stream()
                    .filter(a -> a.teacherId().equals(teacherId) && a.studentId().equals(studentId))
                    .map(Lesson::lessonDate)
                    .min(java.util.Comparator.naturalOrder());
        }
    }

    public static class InMemorySemesterReportRepository
            implements br.com.felipeblaft.reports.port.SemesterReportRepository {
        public final Map<UUID, br.com.felipeblaft.reports.domain.semester.SemesterReport> reports =
                new LinkedHashMap<>();

        @Override
        public void save(br.com.felipeblaft.reports.domain.semester.SemesterReport report) {
            boolean conflict =
                    reports.values().stream()
                            .filter(r -> !r.id().equals(report.id()))
                            .filter(
                                    r ->
                                            r.teacherId().equals(report.teacherId())
                                                    && r.studentId().equals(report.studentId())
                                                    && r.period().equals(report.period()))
                            .anyMatch(
                                    r ->
                                            r.status() == report.status()
                                                    && (r.status()
                                                                    == br.com.felipeblaft.reports
                                                                            .domain.report
                                                                            .ReportStatus.APPROVED
                                                            || r.status()
                                                                    == br.com.felipeblaft.reports
                                                                            .domain.report
                                                                            .ReportStatus
                                                                            .AWAITING_REVIEW));
            if (conflict) {
                throw new ConcurrencyConflictException("semester_report");
            }
            reports.put(report.id(), report);
        }

        @Override
        public Optional<br.com.felipeblaft.reports.domain.semester.SemesterReport> searchById(
                UUID id) {
            return Optional.ofNullable(reports.get(id));
        }

        @Override
        public Optional<br.com.felipeblaft.reports.domain.semester.SemesterReport> searchForUpdate(
                UUID id) {
            return searchById(id);
        }

        private Optional<br.com.felipeblaft.reports.domain.semester.SemesterReport> doTrio(
                UUID teacherId,
                UUID studentId,
                String periodKey,
                java.util.function.Predicate<br.com.felipeblaft.reports.domain.report.ReportStatus>
                        status) {
            return reports.values().stream()
                    .filter(r -> r.teacherId().equals(teacherId) && r.studentId().equals(studentId))
                    .filter(r -> r.period().key().equals(periodKey))
                    .filter(r -> status.test(r.status()))
                    .findFirst();
        }

        @Override
        public List<UUID> findIdsByStudent(UUID studentId) {
            return reports.values().stream()
                    .filter(r -> r.studentId().equals(studentId))
                    .map(br.com.felipeblaft.reports.domain.semester.SemesterReport::id)
                    .sorted()
                    .toList();
        }

        @Override
        public Map<String, Integer> countByStudentAndStatus(UUID studentId) {
            Map<String, Integer> counts = new java.util.TreeMap<>();
            reports.values().stream()
                    .filter(r -> r.studentId().equals(studentId))
                    .forEach(r -> counts.merge(r.status().name(), 1, Integer::sum));
            return counts;
        }

        @Override
        public Optional<br.com.felipeblaft.reports.domain.semester.SemesterReport> searchApproved(
                UUID teacherId, UUID studentId, String periodKey) {
            return doTrio(
                    teacherId,
                    studentId,
                    periodKey,
                    s -> s == br.com.felipeblaft.reports.domain.report.ReportStatus.APPROVED);
        }

        @Override
        public List<UUID> findRetentionCandidates(
                br.com.felipeblaft.reports.domain.privacy.RetentionPolicy policy,
                Instant now,
                int limit,
                UUID afterId) {
            return reports.values().stream()
                    .filter(
                            r ->
                                    r.retentionAction(policy, now)
                                            != br.com.felipeblaft.reports.domain.privacy
                                                    .RetentionDecision.KEEP)
                    .map(br.com.felipeblaft.reports.domain.semester.SemesterReport::id)
                    .filter(id -> afterId == null || id.compareTo(afterId) > 0)
                    .sorted()
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<br.com.felipeblaft.reports.domain.semester.SemesterReport> searchOpen(
                UUID teacherId, UUID studentId, String periodKey) {
            return doTrio(
                    teacherId,
                    studentId,
                    periodKey,
                    br.com.felipeblaft.reports.domain.report.ReportStatus::isOpen);
        }

        @Override
        public List<br.com.felipeblaft.reports.domain.semester.SemesterReport> listApproved(
                UUID teacherId, UUID studentId) {
            return reports.values().stream()
                    .filter(r -> r.teacherId().equals(teacherId) && r.studentId().equals(studentId))
                    .filter(
                            r ->
                                    r.status()
                                            == br.com.felipeblaft.reports.domain.report.ReportStatus
                                                    .APPROVED)
                    .toList();
        }
    }

    public static class InMemoryReportVersionRepository implements ReportVersionRepository {
        public final List<ReportVersion> versions = new ArrayList<>();

        @Override
        public void save(ReportVersion version) {
            if (search(version.reportType(), version.reportId(), version.number()).isPresent()) {
                throw new IllegalStateException("versão imutável já existe");
            }
            versions.add(version);
        }

        @Override
        public Optional<ReportVersion> search(ReportType type, UUID reportId, int number) {
            return versions.stream()
                    .filter(
                            v ->
                                    v.reportType() == type
                                            && v.reportId().equals(reportId)
                                            && v.number() == number)
                    .findFirst();
        }

        @Override
        public int markContentRemoved(ReportType type, UUID reportId, Instant now) {
            int count = 0;
            for (int i = 0; i < versions.size(); i++) {
                ReportVersion v = versions.get(i);
                if (v.reportType() == type && v.reportId().equals(reportId)) {
                    versions.set(i, v.withContentRemoved(now));
                    count++;
                }
            }
            return count;
        }
    }

    public static class InMemoryReportInteractionRepository implements ReportInteractionRepository {
        public final List<ReportInteraction> interactions = new ArrayList<>();

        @Override
        public void save(ReportInteraction interaction) {
            interactions.add(interaction);
        }

        @Override
        public Optional<ReportInteraction> searchById(UUID id) {
            return interactions.stream().filter(i -> i.id().equals(id)).findFirst();
        }

        @Override
        public int removeText(ReportType type, UUID reportId, Instant now) {
            int count = 0;
            for (int i = 0; i < interactions.size(); i++) {
                ReportInteraction x = interactions.get(i);
                if (x.reportType() == type && x.reportId().equals(reportId) && x.text() != null) {
                    interactions.set(i, x.withTextRemoved(now));
                    count++;
                }
            }
            return count;
        }

        @Override
        public Optional<ReportInteraction> last(ReportType type, UUID reportId) {
            List<ReportInteraction> ofReport =
                    interactions.stream()
                            .filter(i -> i.reportType() == type && i.reportId().equals(reportId))
                            .toList();
            return ofReport.isEmpty() ? Optional.empty() : Optional.of(ofReport.getLast());
        }
    }

    public static class InMemoryPdfDocumentRepository implements PdfDocumentRepository {
        public final Map<String, PdfDocument> documents = new HashMap<>();

        @Override
        public void save(PdfDocument document) {
            documents.put(document.versionId() + ":" + document.type(), document);
        }

        @Override
        public Optional<PdfDocument> search(UUID versionId, DocumentType type) {
            return Optional.ofNullable(documents.get(versionId + ":" + type));
        }

        /** Contexto de relatório de cada PDF, registrado pelo teste (o banco faz o join). */
        public record Context(
                ReportType reportType,
                UUID reportId,
                boolean approvedVersion,
                Instant reportApprovedAt) {}

        public final Map<UUID, Context> contexts = new HashMap<>();
        public final java.util.Set<UUID> filesRemoved = new java.util.HashSet<>();

        public void link(
                PdfDocument pdf,
                ReportType type,
                UUID reportId,
                boolean approvedVersion,
                Instant reportApprovedAt) {
            save(pdf);
            contexts.put(pdf.id(), new Context(type, reportId, approvedVersion, reportApprovedAt));
        }

        private PdfDocument byId(UUID id) {
            return documents.values().stream()
                    .filter(d -> d.id().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public List<UUID> findRetentionCandidates(
                br.com.felipeblaft.reports.domain.privacy.RetentionPolicy policy,
                Instant now,
                int limit,
                UUID afterId) {
            return contexts.entrySet().stream()
                    .filter(
                            e -> {
                                PdfDocument pdf = byId(e.getKey());
                                return pdf.retentionAction(
                                                policy,
                                                now,
                                                e.getValue().approvedVersion(),
                                                e.getValue().reportApprovedAt())
                                        != br.com.felipeblaft.reports.domain.privacy
                                                .RetentionDecision.KEEP;
                            })
                    .map(Map.Entry::getKey)
                    .filter(id -> afterId == null || id.compareTo(afterId) > 0)
                    .sorted()
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<br.com.felipeblaft.reports.port.RetentionPdfCandidate> findForRetention(
                UUID pdfId) {
            Context c = contexts.get(pdfId);
            return c == null
                    ? Optional.empty()
                    : Optional.of(
                            new br.com.felipeblaft.reports.port.RetentionPdfCandidate(
                                    byId(pdfId), c.approvedVersion(), c.reportApprovedAt()));
        }

        private void replace(PdfDocument updated) {
            documents.replaceAll((k, v) -> v.id().equals(updated.id()) ? updated : v);
        }

        @Override
        public void markContentRemoved(UUID pdfId, Instant now) {
            replace(byId(pdfId).withContentRemoved(now));
        }

        @Override
        public int markContentRemovedForReport(ReportType type, UUID reportId, Instant now) {
            int count = 0;
            for (Map.Entry<UUID, Context> e : contexts.entrySet()) {
                Context c = e.getValue();
                if (c.reportType() == type
                        && c.reportId().equals(reportId)
                        && byId(e.getKey()).contentRemovedAt() == null) {
                    markContentRemoved(e.getKey(), now);
                    count++;
                }
            }
            return count;
        }

        @Override
        public int countByReport(ReportType type, UUID reportId) {
            return (int)
                    contexts.values().stream()
                            .filter(c -> c.reportType() == type && c.reportId().equals(reportId))
                            .count();
        }

        @Override
        public List<br.com.felipeblaft.reports.port.PendingFileRemoval> findPendingFileRemovals(
                int limit) {
            return documents.values().stream()
                    .filter(d -> d.contentRemovedAt() != null && !filesRemoved.contains(d.id()))
                    .sorted(java.util.Comparator.comparing(PdfDocument::id))
                    .limit(limit)
                    .map(
                            d ->
                                    new br.com.felipeblaft.reports.port.PendingFileRemoval(
                                            d.id(), d.storageKey()))
                    .toList();
        }

        @Override
        public void markFileRemoved(UUID pdfId, Instant now) {
            filesRemoved.add(pdfId);
        }
    }
}
