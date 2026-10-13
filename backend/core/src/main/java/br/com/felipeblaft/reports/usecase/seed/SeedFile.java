package br.com.felipeblaft.reports.usecase.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conteúdo do arquivo de carga inicial (contracts/carga-inicial.md), já lido pelo adaptador. */
public record SeedFile(List<SeedTeacher> professores, List<SeedStudent> alunos) {

    public SeedFile {
        professores = professores == null ? List.of() : List.copyOf(professores);
        alunos = alunos == null ? List.of() : List.copyOf(alunos);
    }

    public record SeedTeacher(String email, String nome, Boolean ativo) {}

    /** Datas chegam como texto (AAAA-MM-DD) para que a validação aconteça no núcleo. */
    public record SeedStudent(
            String cpf,
            String nome,
            String dataNascimento,
            String nomeResponsavel,
            String instrumento,
            List<String> professores) {

        public SeedStudent {
            professores =
                    professores == null
                            ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(professores));
        }
    }
}
