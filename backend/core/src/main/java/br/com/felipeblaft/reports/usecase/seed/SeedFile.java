package br.com.felipeblaft.reports.usecase.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conteúdo do arquivo de carga inicial (contracts/carga-inicial.md), já lido pelo adaptador. */
public record SeedFile(List<SeedTeacher> teachers, List<SeedStudent> students) {

    public SeedFile {
        teachers = teachers == null ? List.of() : List.copyOf(teachers);
        students = students == null ? List.of() : List.copyOf(students);
    }

    /** Só as contagens: o arquivo carrega CPF, nomes e e-mails. */
    @Override
    public String toString() {
        return "SeedFile[teachers=" + teachers.size() + ", students=" + students.size() + "]";
    }

    public record SeedTeacher(String email, String name, Boolean active) {

        /** Sem e-mail nem nome. */
        @Override
        public String toString() {
            return "SeedTeacher[active=" + active + "]";
        }
    }

    /** Datas chegam como texto (AAAA-MM-DD) para que a validação aconteça no núcleo. */
    public record SeedStudent(
            String cpf,
            String name,
            String birthDate,
            String guardianName,
            String instrument,
            List<String> teachers) {

        public SeedStudent {
            teachers =
                    teachers == null
                            ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(teachers));
        }

        /** Sem CPF, nome, data de nascimento, responsável, instrumento nem e-mails. */
        @Override
        public String toString() {
            return "SeedStudent[teachers=" + teachers.size() + "]";
        }
    }
}
