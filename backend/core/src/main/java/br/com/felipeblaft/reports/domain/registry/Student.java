package br.com.felipeblaft.reports.domain.registry;

import br.com.felipeblaft.reports.domain.common.GeneratorId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

/**
 * Aluno (data-model.md §2). O CPF só existe cifrado e como hash; nunca é lido de volta para
 * exibição nem enviado à IA (FR-011, FR-023).
 */
public class Student {

    public static final int ADULT_AGE = 18;

    private final UUID id;
    private String name;
    private LocalDate birthDate;
    private final byte[] encryptedCpf;
    private final String cpfHash;
    private String guardianName;
    private String instrument;
    private final boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public Student(
            UUID id,
            String name,
            LocalDate birthDate,
            byte[] encryptedCpf,
            String cpfHash,
            String guardianName,
            String instrument,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.birthDate = Objects.requireNonNull(birthDate);
        this.encryptedCpf = Objects.requireNonNull(encryptedCpf).clone();
        this.cpfHash = Objects.requireNonNull(cpfHash);
        this.guardianName = guardianName;
        this.instrument = instrument;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Student create(
            String name,
            LocalDate birthDate,
            byte[] encryptedCpf,
            String cpfHash,
            String guardianName,
            String instrument,
            Instant now) {
        return new Student(
                GeneratorId.create(),
                name,
                birthDate,
                encryptedCpf,
                cpfHash,
                guardianName,
                instrument,
                true,
                now,
                now);
    }

    /** Idade completa em anos na data informada. */
    public int ageAt(LocalDate data) {
        return age(birthDate, data);
    }

    public static int age(LocalDate birth, LocalDate data) {
        return Math.max(0, Period.between(birth, data).getYears());
    }

    /** Menores de 18 anos na data de referência exigem o nome do responsável. */
    public static boolean requiresGuardian(LocalDate birth, LocalDate reference) {
        return age(birth, reference) < ADULT_AGE;
    }

    /** Atualiza os dados cadastrais pela carga; devolve {@code true} se algo mudou. */
    public boolean updateRegistry(
            String newName,
            LocalDate newBirthDate,
            String newGuardian,
            String newInstrument,
            Instant now) {
        if (name.equals(newName)
                && birthDate.equals(newBirthDate)
                && Objects.equals(guardianName, newGuardian)
                && Objects.equals(instrument, newInstrument)) {
            return false;
        }
        name = newName;
        birthDate = newBirthDate;
        guardianName = newGuardian;
        instrument = newInstrument;
        updatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public byte[] encryptedCpf() {
        return encryptedCpf.clone();
    }

    public String cpfHash() {
        return cpfHash;
    }

    public String guardianName() {
        return guardianName;
    }

    public String instrument() {
        return instrument;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Student[id=" + id + "]";
    }
}
