package br.com.felipeblaft.reports.inbound.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Update recebido da API de bots (só os campos usados). Outros tipos de update são confirmados com
 * 200 e ignorados (contracts/telegram-conversa.md §1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTelegram(
        @JsonProperty("update_id") long updateId,
        TelegramMessage message,
        @JsonProperty("callback_query") CallbackTelegram callbackQuery) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") long messageId,
            TelegramUser from,
            ChatTelegram chat,
            String text,
            TelegramFile voice,
            TelegramFile audio,
            TelegramFile video,
            @JsonProperty("video_note") TelegramFile videoNote,
            TelegramFile document,
            List<Object> photo,
            Object sticker,
            Object location,
            Object contact) {

        /**
         * Primeiro arquivo de áudio ou vídeo da mensagem, se houver (voz, áudio, vídeo, documento).
         */
        public TelegramFile audioFile() {
            if (voice != null) {
                return voice;
            }
            if (audio != null) {
                return audio;
            }
            if (video != null) {
                return video;
            }
            if (videoNote != null) {
                return videoNote;
            }
            if (document != null && document.mimeType() != null) {
                String mime = document.mimeType();
                if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                    return document;
                }
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUser(long id, @JsonProperty("is_bot") boolean bot) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatTelegram(long id, String type) {

        public boolean privateChat() {
            return "private".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramFile(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_unique_id") String fileUniqueId,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("file_size") Long fileSize,
            @JsonProperty("file_name") String fileName,
            Integer duration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackTelegram(
            String id, TelegramUser from, TelegramMessage message, String data) {}
}
