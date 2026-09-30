package br.com.felipeblaft.relatorios.entrada.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Sequência 5.1 e estado #1 de contracts/telegram-conversa.md. */
class VinculacaoConversaIT extends TelegramConversaBase {

    private Professor clara;

    @BeforeEach
    void carregar() {
        clara = cenario.clara();
        cenario.aluno(clara, "Matteo Mafra", "Piano");
        cenario.aluno(clara, "Olavo Torrente", "Violão");
    }

    @Test
    void naoVinculadoRecebePedidoDeCodigo() throws Exception {
        enviarTexto(USUARIO_CLARA, "olá, bom dia!");
        enviarTexto(USUARIO_CLARA, "/start");
        assertThat(textosEnviados())
                .containsOnly(
                        "Olá! Para usar o bot, envie o código de acesso que a secretaria da escola te passou.");
        assertThat(jdbc.queryForObject("select count(*) from tentativa_acesso", Integer.class)).isZero();
    }

    @Test
    void codigoValidoVinculaEMostraOInicio() throws Exception {
        enviarTexto(USUARIO_CLARA, "k7qm-2xpa");

        String texto = ultimaMensagem().get("text").asString();
        assertThat(texto)
                .startsWith("Conta vinculada com sucesso, Clara!")
                .contains("<b>Olá, Clara</b>")
                .contains("Nenhum relatório em aberto.")
                .contains("2 aluno(s) associado(s)")
                .contains("• Matteo Mafra · Piano");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:reg:", "v1:sem:", "v1:hist:");
        assertThat(ultimaMensagem().get("parse_mode").asString()).isEqualTo("HTML");
        assertThat(ultimaMensagem().get("chat_id").asLong()).isEqualTo(USUARIO_CLARA);
        assertThat(jdbc.queryForObject("select professor_id from vinculo_telegram", java.util.UUID.class))
                .isEqualTo(clara.id());
    }

    @Test
    void codigoErradoVencidoEBloqueio() throws Exception {
        enviarTexto(USUARIO_CLARA, "ZZZZZZZZ");
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Código inválido.");
        for (int i = 0; i < 4; i++) {
            enviarTexto(USUARIO_CLARA, "ZZZZ-ZZZZ");
        }
        enviarTexto(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em 15 minuto(s).");
        relogio.avancar(Duration.ofDays(7));
        enviarTexto(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Este código venceu após 7 dias sem uso.");
    }

    @Test
    void conflitosDeVinculo() throws Exception {
        cenario.bruno();
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        enviarTexto(USUARIO_BRUNO, CenarioIT.CODIGO_CLARA);
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Seu cadastro já está vinculado a outra conta do Telegram. Procure a secretaria da escola.");
    }

    @Test
    void segredoErradoEUpdateRepetido() throws Exception {
        mvc.perform(
                        post("/telegram/webhook")
                                .header("X-Telegram-Bot-Api-Secret-Token", "errado")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        long update = proximoUpdate();
        String json = mensagemJson(update, USUARIO_CLARA, "\"text\": \"oi\"");
        enviarUpdate(json);
        enviarUpdate(json);
        assertThat(textosEnviados()).hasSize(1);
        enviarUpdate("{ nao é json");
        enviarUpdate("{\"update_id\": " + proximoUpdate() + ", \"edited_message\": {}}");
        assertThat(textosEnviados()).hasSize(1);
    }

    @Test
    void grupoRecusadoEOutrosTiposPedemCodigo() throws Exception {
        enviarUpdate(mensagemJson(proximoUpdate(), USUARIO_CLARA, "group", "\"text\": \"K7QM2XPA\""));
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("Eu só atendo em conversa privada. Fale comigo diretamente.");
        enviarUpdate(mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"sticker\": {\"file_id\": \"x\"}"));
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Olá! Para usar o bot");
        enviarCallback(USUARIO_CLARA, "v1:reg:");
        assertThat(chamadas("answerCallbackQuery")).hasSize(1);
        assertThat(ultimaMensagem().get("text").asString()).startsWith("Olá! Para usar o bot");
        assertThat(jdbc.queryForObject("select count(*) from vinculo_telegram", Integer.class)).isZero();
    }
}
