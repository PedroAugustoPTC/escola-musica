package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;

/** Conversão entre as entidades JPA de cadastro e os objetos de domínio. */
final class RegistryMapper {

    private RegistryMapper() {}

    static Teacher toDomain(TeacherEntity e) {
        return new Teacher(
                e.getId(),
                e.getName(),
                e.getEmail(),
                e.isActive(),
                e.getAccessCodeHash(),
                e.getAccessCodeExpiresAt(),
                e.getAccessCodeRenewedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    static TeacherEntity toEntity(Teacher p) {
        return new TeacherEntity(
                p.id(),
                p.name(),
                p.email(),
                p.active(),
                p.accessCodeHash(),
                p.accessCodeExpiresAt(),
                p.accessCodeRenewedAt(),
                p.createdAt(),
                p.updatedAt());
    }

    static Student toDomain(StudentEntity e) {
        return new Student(
                e.getId(),
                e.getName(),
                e.getBirthDate(),
                e.getCpfEncrypted(),
                e.getCpfHash(),
                e.getGuardianName(),
                e.getInstrument(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    static StudentEntity toEntity(Student a) {
        return new StudentEntity(
                a.id(),
                a.name(),
                a.birthDate(),
                a.encryptedCpf(),
                a.cpfHash(),
                a.guardianName(),
                a.instrument(),
                a.active(),
                a.createdAt(),
                a.updatedAt());
    }

    static TeacherStudentEntity toEntity(TeacherStudent a) {
        return new TeacherStudentEntity(a.id(), a.teacherId(), a.studentId(), a.createdAt());
    }
}
