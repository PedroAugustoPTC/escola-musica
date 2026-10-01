package br.com.felipeblaft.relatorios.casodeuso.carga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conteúdo do arquivo de carga inicial (contracts/carga-inicial.md), já lido pelo adaptador. */
public record ArquivoCarga(List<ProfessorCarga> professores, List<AlunoCarga> alunos) {

    public ArquivoCarga {
        professores = professores == null ? List.of() : List.copyOf(professores);
        alunos = alunos == null ? List.of() : List.copyOf(alunos);
    }

    public record ProfessorCarga(String email, String nome, Boolean ativo) {}

    /** Datas chegam como texto (AAAA-MM-DD) para que a validação aconteça no núcleo. */
    public record AlunoCarga(
            String cpf,
            String nome,
            String dataNascimento,
            String nomeResponsavel,
            String instrumento,
            List<String> professores) {

        public AlunoCarga {
            professores =
                    professores == null
                            ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(professores));
        }
    }
}
