package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.AccessScenario;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FixedAccessCodeGenerator;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TeacherAccessAuditService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cenário dos testes de acesso e de gestão de professores: repositórios em memória, o serviço de
 * encerramento do vínculo, a revogação de sessões, a emissão de código e o registro de autoria.
 * Nenhuma cascata de banco é simulada: o serviço apaga o rascunho por conta própria.
 */
public class TeacherScenario extends AccessScenario {

    /** Rascunhos de cadastro de aluno em memória, um por vínculo. */
    public static final class InMemoryDrafts implements StudentRegistrationDraftRepository {

        private final Map<UUID, StudentRegistrationDraft> data = new LinkedHashMap<>();

        @Override
        public Optional<StudentRegistrationDraft> searchByLinkId(UUID linkId) {
            return Optional.ofNullable(data.get(linkId));
        }

        @Override
        public void save(StudentRegistrationDraft draft) {
            data.put(draft.linkId(), draft);
        }

        @Override
        public void removeByLinkId(UUID linkId) {
            data.remove(linkId);
        }

        @Override
        public int removeExpired(Instant now) {
            int before = data.size();
            data.values().removeIf(draft -> !draft.expiresAt().isAfter(now));
            return before - data.size();
        }

        public int size() {
            return data.size();
        }
    }

    public final InMemoryDrafts drafts = new InMemoryDrafts();
    public final InMemoryChannelActionRepository actions = new InMemoryChannelActionRepository();
    public final NoAdministratorSessions administratorSessions = new NoAdministratorSessions();
    public final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    public final FixedAccessCodeGenerator generator = new FixedAccessCodeGenerator();

    public final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    public final ActorAuthorizationService actorAuthorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    public final TeacherAccessAuditService audit =
            new TeacherAccessAuditService(authorship, protection);
    public final AccessCodeIssuanceService issuance =
            new AccessCodeIssuanceService(generator, protection, teachers, parameters);
    public final SessionRevocationService revocation =
            new SessionRevocationService(sessions, administratorSessions, clock);
    public final TelegramLinkTerminationService termination =
            new TelegramLinkTerminationService(links, drafts, actions, clock);

    /** Vincula a conta do Telegram ao professor. */
    public TelegramLink link(Teacher teacher, long telegramUserId, long chatId) {
        TelegramLink link = TelegramLink.create(teacher.id(), telegramUserId, chatId, clock.now());
        links.save(link);
        return link;
    }

    /** Cadastro de aluno em andamento do vínculo. */
    public StudentRegistrationDraft draft(TelegramLink link) {
        StudentRegistrationDraft draft =
                new StudentRegistrationDraft(
                        link.id(),
                        StudentRegistrationDraft.Kind.CREATE,
                        null,
                        Set.of(StudentRegistrationDraft.Field.NAME),
                        "Aluno em andamento",
                        null,
                        LocalDate.of(2012, 1, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        clock.now(),
                        clock.now(),
                        clock.now().plus(Duration.ofMinutes(30)));
        drafts.save(draft);
        return draft;
    }

    /** Ação de saída ainda pendente no chat. */
    public ChannelAction pendingAction(String id, long chatId) {
        ChannelAction action =
                ChannelAction.create(
                        id,
                        "batch-" + id,
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        chatId,
                        null,
                        null,
                        ChannelActionType.MESSAGE,
                        "{\"text\":\"olá\"}",
                        null,
                        clock.now(),
                        new RetryPolicy(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofHours(24)));
        actions.saveBatch(List.of(action));
        return action;
    }
}
