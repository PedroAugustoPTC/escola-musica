package br.com.felipeblaft.relatorios.casodeuso.carga;

import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga.AlunoCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.ArquivoCarga.ProfessorCarga;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.CodigoEmitido;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.Contagem;
import br.com.felipeblaft.relatorios.casodeuso.carga.ResultadoCarga.ErroCarga;
import br.com.felipeblaft.relatorios.dominio.acesso.CodigoAcesso;
import br.com.felipeblaft.relatorios.dominio.cadastro.Aluno;
import br.com.felipeblaft.relatorios.dominio.cadastro.Cpf;
import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.dominio.cadastro.ProfessorAluno;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.servico.EmissaoCodigoAcessoServico;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Carga inicial de professores, alunos e associações (FR-011, R-14). Valida o arquivo inteiro antes
 * de gravar qualquer coisa (tudo ou nada); é idempotente por e-mail e por hash do CPF; nunca apaga.
 * Professor novo recebe código, devolvido em claro só no resultado.
 */
public class CarregarCadastroInicialCasoDeUso {

    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ProfessorRepositorio professores;
    private final AlunoRepositorio alunos;
    private final AssociacaoRepositorio associacoes;
    private final ProtecaoDadosPorta protecao;
    private final EmissaoCodigoAcessoServico emissao;
    private final RelogioPorta relogio;

    public CarregarCadastroInicialCasoDeUso(
            ProfessorRepositorio professores,
            AlunoRepositorio alunos,
            AssociacaoRepositorio associacoes,
            ProtecaoDadosPorta protecao,
            EmissaoCodigoAcessoServico emissao,
            RelogioPorta relogio) {
        this.professores = Objects.requireNonNull(professores);
        this.alunos = Objects.requireNonNull(alunos);
        this.associacoes = Objects.requireNonNull(associacoes);
        this.protecao = Objects.requireNonNull(protecao);
        this.emissao = Objects.requireNonNull(emissao);
        this.relogio = Objects.requireNonNull(relogio);
    }

    public ResultadoCarga executar(ArquivoCarga arquivo, boolean simular) {
        Instant agora = relogio.agora();
        LocalDate hoje = relogio.hojeNoFusoDaEscola();
        List<ErroCarga> erros = new ArrayList<>();
        Map<Integer, Cpf> cpfsValidos = new HashMap<>();
        Map<Integer, LocalDate> nascimentos = new HashMap<>();
        validarProfessores(arquivo.professores(), erros);
        validarAlunos(arquivo, hoje, erros, cpfsValidos, nascimentos);

        if (!erros.isEmpty()) {
            return new ResultadoCarga(
                    simular, new Contagem(0, 0, 0), new Contagem(0, 0, 0), 0, erros, List.of());
        }

        List<CodigoEmitido> codigos = new ArrayList<>();
        Map<String, Professor> professoresPorEmail = new LinkedHashMap<>();
        int[] contagemProfessores = new int[3];
        for (ProfessorCarga carga : arquivo.professores()) {
            String email = Professor.normalizarEmail(carga.email());
            String nome = carga.nome().strip();
            boolean ativo = carga.ativo() == null || carga.ativo();
            var existente = professores.buscarPorEmail(email);
            Professor professor;
            if (existente.isPresent()) {
                professor = existente.get();
                if (professor.atualizarCadastro(nome, ativo, agora)) {
                    contagemProfessores[1]++;
                    gravar(simular, () -> professores.salvar(professor));
                } else {
                    contagemProfessores[2]++;
                }
            } else {
                professor = Professor.novo(nome, email, ativo, agora);
                contagemProfessores[0]++;
                if (!simular) {
                    CodigoAcesso codigo = emissao.emitir(professor, agora);
                    professores.salvar(professor);
                    codigos.add(
                            new CodigoEmitido(
                                    email, codigo.valor(), professor.codigoAcessoExpiraEm()));
                }
            }
            professoresPorEmail.put(email, professor);
        }

        int[] contagemAlunos = new int[3];
        int associacoesCriadas = 0;
        List<AlunoCarga> listaAlunos = arquivo.alunos();
        for (int i = 0; i < listaAlunos.size(); i++) {
            AlunoCarga carga = listaAlunos.get(i);
            Cpf cpf = cpfsValidos.get(i);
            LocalDate nascimento = nascimentos.get(i);
            String nome = carga.nome().strip();
            String responsavel = textoOpcional(carga.nomeResponsavel());
            String instrumento = textoOpcional(carga.instrumento());
            String hash = protecao.hashCpf(cpf);
            var existente = alunos.buscarPorCpfHash(hash);
            Aluno aluno;
            boolean novo = existente.isEmpty();
            if (!novo) {
                aluno = existente.get();
                if (aluno.atualizarCadastro(nome, nascimento, responsavel, instrumento, agora)) {
                    contagemAlunos[1]++;
                    gravar(simular, () -> alunos.salvar(aluno));
                } else {
                    contagemAlunos[2]++;
                }
            } else {
                aluno =
                        Aluno.novo(
                                nome,
                                nascimento,
                                simular ? new byte[0] : protecao.cifrarCpf(cpf),
                                hash,
                                responsavel,
                                instrumento,
                                agora);
                contagemAlunos[0]++;
                gravar(simular, () -> alunos.salvar(aluno));
            }
            Set<String> emailsDoAluno = new HashSet<>();
            for (String emailProfessor : carga.professores()) {
                String email = Professor.normalizarEmail(emailProfessor);
                if (!emailsDoAluno.add(email)) {
                    continue;
                }
                Professor professor =
                        professoresPorEmail.computeIfAbsent(
                                email,
                                chave ->
                                        professores
                                                .buscarPorEmail(chave)
                                                .orElseThrow(
                                                        () ->
                                                                new ExcecaoDominio(
                                                                        CodigoErro
                                                                                .PROFESSOR_INEXISTENTE)));
                if (novo || !associacoes.existe(professor.id(), aluno.id())) {
                    associacoesCriadas++;
                    gravar(
                            simular,
                            () ->
                                    associacoes.salvar(
                                            ProfessorAluno.nova(
                                                    professor.id(), aluno.id(), agora)));
                }
            }
        }

        return new ResultadoCarga(
                simular,
                new Contagem(
                        contagemProfessores[0], contagemProfessores[1], contagemProfessores[2]),
                new Contagem(contagemAlunos[0], contagemAlunos[1], contagemAlunos[2]),
                associacoesCriadas,
                List.of(),
                codigos);
    }

    private static void gravar(boolean simular, Runnable gravacao) {
        if (!simular) {
            gravacao.run();
        }
    }

    private void validarProfessores(List<ProfessorCarga> lista, List<ErroCarga> erros) {
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < lista.size(); i++) {
            ProfessorCarga carga = lista.get(i);
            String email = Professor.normalizarEmail(carga.email());
            if (email == null || !FORMATO_EMAIL.matcher(email).matches()) {
                erros.add(new ErroCarga("professores", i, "email", CodigoErro.EMAIL_INVALIDO));
            } else if (!emails.add(email)) {
                erros.add(
                        new ErroCarga(
                                "professores", i, "email", CodigoErro.EMAIL_DUPLICADO_NO_ARQUIVO));
            }
            if (textoOpcional(carga.nome()) == null) {
                erros.add(new ErroCarga("professores", i, "nome", CodigoErro.NOME_OBRIGATORIO));
            }
        }
    }

    private void validarAlunos(
            ArquivoCarga arquivo,
            LocalDate hoje,
            List<ErroCarga> erros,
            Map<Integer, Cpf> cpfsValidos,
            Map<Integer, LocalDate> nascimentos) {
        Set<String> emailsDoArquivo = new HashSet<>();
        for (ProfessorCarga professor : arquivo.professores()) {
            String email = Professor.normalizarEmail(professor.email());
            if (email != null) {
                emailsDoArquivo.add(email);
            }
        }
        Set<Cpf> cpfsVistos = new HashSet<>();
        List<AlunoCarga> lista = arquivo.alunos();
        for (int i = 0; i < lista.size(); i++) {
            AlunoCarga carga = lista.get(i);
            if (textoOpcional(carga.nome()) == null) {
                erros.add(new ErroCarga("alunos", i, "nome", CodigoErro.NOME_OBRIGATORIO));
            }
            try {
                Cpf cpf = Cpf.de(carga.cpf());
                if (cpfsVistos.add(cpf)) {
                    cpfsValidos.put(i, cpf);
                } else {
                    erros.add(
                            new ErroCarga("alunos", i, "cpf", CodigoErro.CPF_DUPLICADO_NO_ARQUIVO));
                }
            } catch (ExcecaoDominio erro) {
                erros.add(new ErroCarga("alunos", i, "cpf", CodigoErro.CPF_INVALIDO));
            }
            LocalDate nascimento = lerData(carga.dataNascimento());
            if (nascimento == null || nascimento.isAfter(hoje)) {
                erros.add(
                        new ErroCarga(
                                "alunos",
                                i,
                                "dataNascimento",
                                CodigoErro.DATA_NASCIMENTO_INVALIDA));
            } else {
                nascimentos.put(i, nascimento);
                if (Aluno.exigeResponsavel(nascimento, hoje)
                        && textoOpcional(carga.nomeResponsavel()) == null) {
                    erros.add(
                            new ErroCarga(
                                    "alunos",
                                    i,
                                    "nomeResponsavel",
                                    CodigoErro.RESPONSAVEL_OBRIGATORIO));
                }
            }
            List<String> emails = carga.professores();
            for (int j = 0; j < emails.size(); j++) {
                String email = Professor.normalizarEmail(emails.get(j));
                boolean existe =
                        email != null
                                && (emailsDoArquivo.contains(email)
                                        || professores.buscarPorEmail(email).isPresent());
                if (!existe) {
                    erros.add(
                            new ErroCarga(
                                    "alunos",
                                    i,
                                    "professores[" + j + "]",
                                    CodigoErro.PROFESSOR_INEXISTENTE));
                }
            }
        }
    }

    private static LocalDate lerData(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException erro) {
            return null;
        }
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
