package br.com.felipeblaft.reports.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.saida.seguranca.AcessoDecifragem;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CadastroPersistenciaIT extends IntegracaoBase {

    @Autowired TeacherRepository professores;
    @Autowired StudentRepository alunos;
    @Autowired AssignmentRepository associacoes;
    @Autowired DataProtectionPort protecao;

    private final Instant agora = Instant.parse("2026-09-21T13:00:00.123456Z");

    @Test
    void professorIdaEVolta() {
        Teacher clara = Teacher.create("Clara Souza", "clara@exemplo.test", true, agora);
        clara.replaceCode(
                protecao.hashAccessCode(AccessCode.of("K7QM2XPA")), agora, Duration.ofDays(7));
        professores.save(clara);

        Teacher lido = professores.searchByEmail("clara@exemplo.test").orElseThrow();
        assertThat(lido.id()).isEqualTo(clara.id());
        assertThat(lido.name()).isEqualTo("Clara Souza");
        assertThat(lido.accessCodeExpiresAt())
                .isEqualTo(agora.plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS));
        assertThat(professores.searchByAccessCodeHash(clara.accessCodeHash()))
                .map(Teacher::id)
                .contains(clara.id());
        assertThat(professores.searchById(clara.id())).isPresent();

        lido.updateRegistry("Clara S.", false, agora.plusSeconds(1));
        professores.save(lido);
        assertThat(professores.searchById(clara.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void cpfCifradoNuncaLegivelNoBancoEHmacEstavel() {
        Cpf cpf = Cpf.of("529.982.247-25");
        Student matteo =
                Student.create(
                        "Matteo Mafra",
                        LocalDate.of(2014, 5, 10),
                        protecao.encryptCpf(cpf),
                        protecao.hashCpf(cpf),
                        "Resp",
                        "Piano",
                        agora);
        alunos.save(matteo);

        byte[] noBanco =
                jdbc.queryForObject(
                        "select cpf_cifrado from aluno where id = ?", byte[].class, matteo.id());
        assertThat(new String(noBanco, StandardCharsets.ISO_8859_1)).doesNotContain("52998224725");
        assertThat(noBanco.length).isGreaterThan(12 + 11);
        assertThat(protecao.encryptCpf(cpf)).isNotEqualTo(noBanco);
        assertThat(protecao.hashCpf(Cpf.of("52998224725"))).isEqualTo(matteo.cpfHash()).hasSize(64);
        assertThat(protecao.hashCpf(cpf))
                .isNotEqualTo(protecao.originHash(Channel.WEB, "52998224725"));
        assertThat(AcessoDecifragem.decifrar(protecao, noBanco)).isEqualTo("52998224725");

        String colunas =
                String.join(
                        ",",
                        jdbc.queryForList(
                                "select column_name from information_schema.columns where table_name = 'aluno'",
                                String.class));
        assertThat(colunas).doesNotContain("cpf,").contains("cpf_cifrado", "cpf_hash");

        Student lido = alunos.searchByCpfHash(matteo.cpfHash()).orElseThrow();
        assertThat(lido.name()).isEqualTo("Matteo Mafra");
        assertThat(lido.encryptedCpf()).isEqualTo(noBanco);
    }

    @Test
    void associacaoELista() {
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, agora);
        Teacher bruno = Teacher.create("Bruno", "bruno@exemplo.test", true, agora);
        professores.save(clara);
        professores.save(bruno);
        Student olavo = aluno("Olavo Torrente", "123.456.789-09");
        Student matteo = aluno("matteo Mafra", "529.982.247-25");
        Student helena = aluno("Helena Prado", "318.264.597-82");
        associacoes.save(TeacherStudent.create(clara.id(), olavo.id(), agora));
        associacoes.save(TeacherStudent.create(clara.id(), matteo.id(), agora));
        associacoes.save(TeacherStudent.create(bruno.id(), helena.id(), agora));

        assertThat(associacoes.exists(clara.id(), olavo.id())).isTrue();
        assertThat(associacoes.exists(clara.id(), helena.id())).isFalse();
        assertThat(alunos.listByTeacher(clara.id()))
                .extracting(Student::name)
                .containsExactly("matteo Mafra", "Olavo Torrente");
    }

    private Student aluno(String nome, String cpfTexto) {
        Cpf cpf = Cpf.of(cpfTexto);
        Student aluno =
                Student.create(
                        nome,
                        LocalDate.of(2000, 1, 1),
                        protecao.encryptCpf(cpf),
                        protecao.hashCpf(cpf),
                        null,
                        null,
                        agora);
        alunos.save(aluno);
        return aluno;
    }
}
