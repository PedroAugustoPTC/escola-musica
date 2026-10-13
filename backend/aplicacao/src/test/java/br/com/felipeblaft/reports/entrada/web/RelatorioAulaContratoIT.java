package br.com.felipeblaft.reports.entrada.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.ContratoApiBase;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Contrato dos endpoints de relatório de aula (registro, respostas, alteração, aprovação,
 * cancelamento).
 */
class RelatorioAulaContratoIT extends ContratoApiBase {

    private Student matteo;
    private Student helena;
    private Cookie sessao;
    private Cookie sessaoBruno;

    @BeforeEach
    void carregar() throws Exception {
        Teacher clara = cenario.clara();
        Teacher bruno = cenario.bruno();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(bruno, "Helena Prado", "Flauta");
        sessao = entrar(CenarioIT.CODIGO_CLARA);
        sessaoBruno = entrar(CenarioIT.CODIGO_BRUNO);
    }

    private static MockMultipartFile audio(String tipo, byte[] conteudo) {
        return new MockMultipartFile("audio", "aula.webm", tipo, conteudo);
    }

    private static MockMultipartFile audio() {
        return audio("audio/webm;codecs=opus", NormalizacaoAudioTeste.AUDIO_COM_FALA);
    }

    private UUID registrar() throws Exception {
        String corpo =
                mvc.perform(
                                autenticada(
                                        multipart(
                                                        "/api/v1/alunos/{id}/relatorios-aula",
                                                        matteo.id())
                                                .file(audio()),
                                        sessao))
                        .andExpect(status().isAccepted())
                        .andExpect(respostaSegueContrato())
                        .andExpect(jsonPath("$.trabalhoId").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(corpo.replaceAll(".*\"trabalhoId\":\"([^\"]+)\".*", "$1"));
    }

    private UUID relatorioComPerguntas() throws Exception {
        registrar();
        cenario.processarFila();
        return jdbc.queryForObject("select id from relatorio_aula", UUID.class);
    }

    private UUID relatorioEmConferencia() throws Exception {
        UUID id = relatorioComPerguntas();
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/respostas", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        cenario.processarFila();
        return id;
    }

    @Test
    void registrarAceitaOAudioEDevolveOTrabalho() throws Exception {
        UUID trabalhoId = registrar();

        mvc.perform(get("/api/v1/trabalhos/{id}", trabalhoId).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.tipo").value("REGISTRO_AULA"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.etapa").value("ENVIANDO"))
                .andExpect(jsonPath("$.demorando").value(false));
    }

    @Test
    void registrarRecusaAlunoNaoAssociadoFormatoTamanhoERelatorioEmAberto() throws Exception {
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", helena.id())
                                        .file(audio()),
                                sessao))
                .andExpect(status().isForbidden())
                .andExpect(respostaSegueContrato())
                .andExpect(jsonPath("$.codigo").value("ALUNO_NAO_ASSOCIADO"));
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "notas.txt",
                                                        "text/plain",
                                                        new byte[] {1})),
                                sessao))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(respostaSegueContrato())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_SUPORTADO"));
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(audio("audio/mpeg", new byte[50 * 1024 * 1024 + 1])),
                                sessao))
                .andExpect(status().isContentTooLarge())
                .andExpect(respostaSegueContrato())
                .andExpect(jsonPath("$.codigo").value("AUDIO_MUITO_GRANDE"))
                .andExpect(jsonPath("$.detalhes.limiteMb").value(50));

        registrar();
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/alunos/{id}/relatorios-aula", matteo.id())
                                        .file(audio()),
                                sessao))
                .andExpect(status().isConflict())
                .andExpect(respostaSegueContrato())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_EM_ABERTO"))
                .andExpect(jsonPath("$.mensagem", startsWith("Você tem um relatório em aberto.")))
                .andExpect(jsonPath("$.detalhes.relatorioEmAberto.canalOrigem").value("WEB"));
    }

    @Test
    void obterRelatorioComPerguntasEOutroProfessorRecebe404() throws Exception {
        UUID id = relatorioComPerguntas();

        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_RESPOSTAS"))
                .andExpect(jsonPath("$.alunoNome").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.professorNome").value("Clara Souza"))
                .andExpect(jsonPath("$.dataAula").value("2026-09-21"))
                .andExpect(jsonPath("$.perguntasPendentes", hasSize(2)))
                .andExpect(jsonPath("$.versaoVigente").value(0))
                .andExpect(jsonPath("$.pdfDisponivel").value(false))
                .andExpect(jsonPath("$.canalOrigem").value("WEB"));
        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void respostasPorTextoEAudioERespostaVazia() throws Exception {
        UUID id = relatorioComPerguntas();

        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/respostas", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("RESPOSTA_VAZIA"));
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/relatorios-aula/{id}/respostas", id)
                                        .file(audio()),
                                sessao))
                .andExpect(status().isAccepted())
                .andExpect(respostaSegueContrato());
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/respostas", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Estudou bem.\"}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("PROCESSAMENTO_EM_ANDAMENTO"));
    }

    @Test
    void alteracoesComVersaoBaseENovaTentativaForaDeEtapa() throws Exception {
        UUID id = relatorioEmConferencia();

        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CONFERENCIA"))
                .andExpect(jsonPath("$.versaoVigente").value(1))
                .andExpect(jsonPath("$.pdfDisponivel").value(true))
                .andExpect(jsonPath("$.ultimaInteracao.tipo").value("RESPOSTA_PERGUNTAS"))
                .andExpect(jsonPath("$.ultimaInteracao.texto").value("Estudou bem."));
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/alteracoes", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"Diga que foi ótima.\",\"versaoBase\":3}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"))
                .andExpect(jsonPath("$.detalhes.versaoVigente").value(1));
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/alteracoes", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\":\"   \",\"versaoBase\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("ALTERACAO_VAZIA"));
        mvc.perform(autenticada(post("/api/v1/relatorios-aula/{id}/nova-tentativa", id), sessao))
                .andExpect(status().isConflict())
                .andExpect(segueContrato());
        mvc.perform(
                        autenticada(
                                multipart("/api/v1/relatorios-aula/{id}/alteracoes", id)
                                        .file(audio())
                                        .param("versaoBase", "1"),
                                sessao))
                .andExpect(status().isAccepted())
                .andExpect(respostaSegueContrato());
        cenario.processarFila();

        mvc.perform(get("/api/v1/relatorios-aula/{id}", id).cookie(sessao))
                .andExpect(jsonPath("$.versaoVigente").value(2))
                .andExpect(jsonPath("$.ultimaInteracao.meio").value("AUDIO"));
    }

    @Test
    void aprovarCitandoAVersaoESegundaAprovacaoRecusada() throws Exception {
        UUID id = relatorioEmConferencia();

        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/aprovacao", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.versaoAprovada").value(1))
                .andExpect(jsonPath("$.aprovadoEm").value("2026-09-21T13:00:00Z"));
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/aprovacao", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_JA_APROVADO"));
    }

    @Test
    void cancelarEDepoisNaoAceitaMaisAcoes() throws Exception {
        UUID id = relatorioEmConferencia();

        mvc.perform(autenticada(post("/api/v1/relatorios-aula/{id}/cancelamento", id), sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        mvc.perform(autenticada(post("/api/v1/relatorios-aula/{id}/cancelamento", id), sessao))
                .andExpect(status().isNoContent())
                .andExpect(segueContrato());
        mvc.perform(
                        autenticada(post("/api/v1/relatorios-aula/{id}/aprovacao", id), sessao)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"versaoConfirmada\":1}"))
                .andExpect(status().isConflict())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.codigo").value("RELATORIO_CANCELADO"));
    }

    @Test
    void trabalhoDeOutroProfessorNaoExiste() throws Exception {
        UUID trabalhoId = registrar();

        mvc.perform(get("/api/v1/trabalhos/{id}", trabalhoId).cookie(sessaoBruno))
                .andExpect(status().isNotFound())
                .andExpect(segueContrato());
        cenario.processarFila();
        mvc.perform(get("/api/v1/trabalhos/{id}", trabalhoId).cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(segueContrato())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.resultadoCodigo").value("PERGUNTAS_PENDENTES"))
                .andExpect(jsonPath("$.relatorioTipo").value("AULA"));
    }
}
