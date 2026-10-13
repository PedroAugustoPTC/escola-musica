package br.com.felipeblaft.reports.entrada.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Início, busca de aluno (#10), sair (#2), ajuda, tipo não suportado e botão antigo. */
class InicioBuscaConversaIT extends TelegramConversaBase {

    @MockitoSpyBean InterpretadorConversaTelegram interpretador;

    @BeforeEach
    void carregar() throws Exception {
        Teacher clara = cenario.clara();
        Teacher bruno = cenario.bruno();
        cenario.aluno(clara, "Matteo Mafra", "Piano");
        cenario.aluno(clara, "Olavo Torrente", "Violão");
        cenario.aluno(clara, "Miguel de Castro Torrente", "Violino");
        cenario.aluno(bruno, "Helena Prado", "Flauta");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
    }

    @Test
    void erroInesperadoRespondeErroInternoENaoReenviaNaRepeticaoDoUpdate() throws Exception {
        doThrow(new IllegalStateException("falha com o aluno Matteo Mafra"))
                .when(interpretador)
                .interpretar(any());

        String texto = mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"text\": \"oi\"");
        enviarUpdate(texto);
        assertThat(textosEnviados())
                .containsExactly("Ocorreu um erro inesperado. Tente novamente mais tarde.");
        enviarUpdate(texto);
        assertThat(textosEnviados()).hasSize(1);

        long idToque = proximoUpdate();
        String toque =
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "v1:inicio:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(idToque, idToque, USUARIO_CLARA, USUARIO_CLARA);
        enviarUpdate(toque);
        assertThat(chamadas("answerCallbackQuery")).hasSize(1);
        assertThat(textosEnviados()).hasSize(2);
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.");
        enviarUpdate(toque);
        assertThat(chamadas("answerCallbackQuery")).hasSize(1);
        assertThat(textosEnviados()).hasSize(2);
    }

    @Test
    void inicioEMenuMostramOBlocoDeInicio() throws Exception {
        for (String comando : new String[] {"início", "/inicio", "MENU", "/start"}) {
            enviarTexto(USUARIO_CLARA, comando);
            assertThat(ultimaMensagem().get("text").asString())
                    .contains("<b>Olá, Clara</b>", "3 aluno(s)");
        }
    }

    @Test
    void buscaComUmaVariasENenhumaCorrespondencia() throws Exception {
        enviarTexto(USUARIO_CLARA, "matteo");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Aula de hoje, 21/09/2026, com Matteo Mafra.")
                .contains("até 50 MB");

        enviarTexto(USUARIO_CLARA, "torrente");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Encontrei mais de um aluno. Qual deles?");
        assertThat(rotulosBotoes(ultimaMensagem()))
                .containsExactly("Miguel de Castro Torrente", "Olavo Torrente", "Início");
        assertThat(botoes(ultimaMensagem())).startsWith("v1:aluno:0", "v1:aluno:1");

        enviarCallback(USUARIO_CLARA, "v1:aluno:1");
        assertThat(ultimaMensagem().get("text").asString()).contains("com Olavo Torrente");

        enviarTexto(USUARIO_CLARA, "Helena");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Nenhum aluno encontrado para \"Helena\".");
        assertThat(rotulosBotoes(ultimaMensagem())).doesNotContain("Helena Prado").hasSize(4);
    }

    @Test
    void registrarAulaPedeOAluno() throws Exception {
        enviarTexto(USUARIO_CLARA, "registrar aula");
        assertThat(ultimaMensagem().get("text").asString()).isEqualTo("Para qual aluno?");
        enviarCallback(USUARIO_CLARA, "v1:reg:");
        assertThat(ultimaMensagem().get("text").asString()).isEqualTo("Para qual aluno?");
        enviarCallback(USUARIO_CLARA, "v1:aluno:0");
        assertThat(ultimaMensagem().get("text").asString()).contains("com Matteo Mafra");
    }

    @Test
    void sairPedeConfirmacaoEDesfazOVinculo() throws Exception {
        enviarTexto(USUARIO_CLARA, "sair");
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Deseja sair?");
        assertThat(botoes(ultimaMensagem())).contains("v1:sairok:");

        enviarTexto(USUARIO_CLARA, "não");
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class))
                .isEqualTo(1);

        enviarTexto(USUARIO_CLARA, "sair");
        enviarTexto(USUARIO_CLARA, "Sim");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Você saiu. Para voltar, envie o seu código de acesso.");
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class))
                .isZero();

        enviarTexto(USUARIO_CLARA, "início");
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Olá! Para usar o bot");
    }

    @Test
    void sairPeloBotao() throws Exception {
        enviarTexto(USUARIO_CLARA, "sair");
        enviarCallback(USUARIO_CLARA, "v1:sairok:");
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class))
                .isZero();
    }

    @Test
    void ajudaTipoNaoSuportadoEBotaoAntigo() throws Exception {
        enviarTexto(USUARIO_CLARA, "/help");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Posso ajudar a registrar aulas");

        enviarUpdate(
                mensagemJson(
                        proximoUpdate(),
                        USUARIO_CLARA,
                        "\"location\": {\"latitude\": 1, \"longitude\": 2}"));
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Não consigo usar este tipo de mensagem aqui. Envie o nome de um aluno, um áudio de aula ou escolha uma ação.");

        enviarCallback(USUARIO_CLARA, "v1:aluno:9");
        assertThat(textosEnviados())
                .contains("Esta opção não vale mais. Veja abaixo a etapa atual.");
        enviarCallback(USUARIO_CLARA, "v1:sairok:");
        enviarCallback(USUARIO_CLARA, "lixo");
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class))
                .isEqualTo(1);

        enviarTexto(USUARIO_CLARA, "aprovar");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Esta ação não está disponível nesta etapa do relatório.");
    }

    @Test
    void usoPeloTelegramRenovaOCodigo() throws Exception {
        relogio.avancar(java.time.Duration.ofDays(6));
        enviarTexto(USUARIO_CLARA, "início");
        java.time.Instant expira =
                jdbc.queryForObject(
                                "select codigo_acesso_expira_em from professor where email = 'clara@exemplo.test'",
                                java.sql.Timestamp.class)
                        .toInstant();
        assertThat(expira).isEqualTo(relogio.now().plus(java.time.Duration.ofDays(7)));
    }
}
