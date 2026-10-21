package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import java.util.UUID;

/**
 * Único caminho para encerrar um vínculo do Telegram, por qualquer causa ("sair", inativação,
 * reemissão do código, desvinculação): remove o vínculo, descarta o cadastro de aluno em andamento
 * e as ações pendentes do chat. Esqueleto da Fundação; a regra é do fluxo W-B.
 */
public class TelegramLinkTerminationService {

    public TelegramLinkTerminationService(
            TelegramLinkRepository links,
            StudentRegistrationDraftRepository drafts,
            ChannelActionRepository actions,
            ClockPort clock) {}

    /** Encerra o vínculo dado. */
    public void terminate(TelegramLink link) {
        throw new UnsupportedOperationException("TelegramLinkTerminationService: fluxo W-B");
    }

    /** Encerra o vínculo do professor, se houver; devolve {@code true} se havia. */
    public boolean terminateOfTeacher(UUID teacherId) {
        throw new UnsupportedOperationException("TelegramLinkTerminationService: fluxo W-B");
    }
}
