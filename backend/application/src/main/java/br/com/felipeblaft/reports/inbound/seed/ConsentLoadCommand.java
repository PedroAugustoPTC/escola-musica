package br.com.felipeblaft.reports.inbound.seed;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.ConsentGuardService;
import br.com.felipeblaft.reports.usecase.privacy.RecordConsentUseCase;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code consent-load --file <consentimentos.csv>} (contrato {@code consent-load.md}, FR-036):
 * registra os consentimentos já coletados pela escola chamando o mesmo caso de uso da Web e do
 * Telegram ({@link RecordConsentUseCase}) com o ator de sistema da carga inicial. Não tem regra
 * própria. Linha inválida não interrompe as demais; o resumo mostra só o número da linha e o código
 * do erro, nunca o valor. O arquivo não entra no repositório (caminho dentro de {@code /work} é
 * recusado) e nada dele vai para log.
 */
@Component
@Profile("seed")
public class ConsentLoadCommand {

    public static final String NAME = "consent-load";

    private static final String HEADER =
            "student_id;consenting_name;relationship;consented_on;term_version;collection_method";
    private static final String CORRELATION_ID = "load-consent";
    private static final Path REPOSITORY_VOLUME = Path.of("/work");

    private final RecordConsentUseCase record;
    private final ConsentRepository consents;
    private final StudentRepository students;
    private final ConsentGuardService guard;
    private final ClockPort clock;

    public ConsentLoadCommand(
            RecordConsentUseCase record,
            ConsentRepository consents,
            StudentRepository students,
            ConsentGuardService guard,
            ClockPort clock) {
        this.record = record;
        this.consents = consents;
        this.students = students;
        this.guard = guard;
        this.clock = clock;
    }

    public int run(List<String> arguments, PrintStream output) {
        String file = CommandArguments.value(arguments, "--file");
        if (file == null) {
            output.println("Uso: consent-load --file <consentimentos.csv>");
            return 2;
        }
        Path path = Path.of(file).toAbsolutePath().normalize();
        if (path.startsWith(REPOSITORY_VOLUME)) {
            output.println(
                    "O arquivo de consentimentos não pode ficar dentro do repositório. Use um caminho fora dele.");
            return 2;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            output.println("Não foi possível ler o arquivo informado.");
            return 2;
        }
        if (lines.isEmpty() || !HEADER.equals(withoutBom(lines.getFirst()).strip())) {
            output.println("Cabeçalho inválido. Esperado: " + HEADER);
            return 2;
        }
        return load(lines, output);
    }

    private int load(List<String> lines, PrintStream output) {
        int accepted = 0;
        int alreadyRecorded = 0;
        List<String> refused = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            int lineNumber = index + 1;
            try {
                if (loadLine(lines.get(index))) {
                    accepted++;
                } else {
                    alreadyRecorded++;
                }
            } catch (DomainException error) {
                refused.add("linha " + lineNumber + ": " + error.code().name());
            }
        }
        output.println(
                "Consentimentos aceitos: "
                        + accepted
                        + "; já registrados: "
                        + alreadyRecorded
                        + "; recusados: "
                        + refused.size()
                        + ".");
        refused.forEach(output::println);
        output.println(
                "Alunos menores ainda sem consentimento válido: " + minorsWithoutConsent() + ".");
        return refused.isEmpty() ? 0 : 1;
    }

    /** {@code true} se gravou; {@code false} se já existia o mesmo consentimento válido. */
    private boolean loadLine(String line) {
        String[] columns = line.split(";", -1);
        if (columns.length != 6) {
            throw new DomainException(br.com.felipeblaft.reports.domain.common.ErrorCode.INVALID_DATA);
        }
        UUID studentId = parseId(columns[0].strip());
        ConsentInput input =
                new ConsentInput(
                        columns[1].strip(),
                        columns[2].strip(),
                        columns[3].strip(),
                        parseVersion(columns[4].strip()),
                        columns[5].strip());
        if (alreadyRecorded(studentId, input)) {
            return false;
        }
        record.run(
                Actor.system(ActionOrigin.INITIAL_LOAD),
                new RecordConsentUseCase.Command(studentId, input, CORRELATION_ID));
        return true;
    }

    private boolean alreadyRecorded(UUID studentId, ConsentInput input) {
        LocalDate date;
        try {
            date = LocalDate.parse(input.consentedOn());
        } catch (RuntimeException invalid) {
            return false;
        }
        for (GuardianConsent existing : consents.findByStudent(studentId)) {
            if (!existing.revoked()
                    && existing.consentedOn().equals(date)
                    && java.util.Objects.equals(existing.termVersionNumber(), input.termVersion())
                    && existing.relationship().name().equals(input.relationship())) {
                return true;
            }
        }
        return false;
    }

    private int minorsWithoutConsent() {
        LocalDate today = clock.todayInSchoolTimezone();
        int count = 0;
        for (Student student : students.list(StudentScope.all(), StudentStatusFilter.ACTIVE)) {
            if (ConsentPolicy.required(student.birthDate(), today) && guard.blocks(student.id())) {
                count++;
            }
        }
        return count;
    }

    private static UUID parseId(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(br.com.felipeblaft.reports.domain.common.ErrorCode.INVALID_DATA);
        }
    }

    private static Integer parseVersion(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String withoutBom(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
