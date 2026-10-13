package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ProfessorRepositorioAdaptador implements TeacherRepository {

    private final ProfessorJpa jpa;

    ProfessorRepositorioAdaptador(ProfessorJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Teacher> searchById(UUID id) {
        return jpa.findById(id).map(CadastroMapeador::paraDominio);
    }

    @Override
    public Optional<Teacher> searchByEmail(String emailNormalizado) {
        return jpa.findByEmail(emailNormalizado).map(CadastroMapeador::paraDominio);
    }

    @Override
    public Optional<Teacher> searchByAccessCodeHash(String hash) {
        return jpa.findByCodigoAcessoHash(hash).map(CadastroMapeador::paraDominio);
    }

    @Override
    public void save(Teacher professor) {
        jpa.save(CadastroMapeador.paraEntidade(professor));
    }
}
