package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.dominio.telegram.AudioPendente;
import br.com.felipeblaft.relatorios.dominio.telegram.ConversaTelegram;
import br.com.felipeblaft.relatorios.dominio.telegram.EtapaFluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.FluxoConversa;
import br.com.felipeblaft.relatorios.dominio.telegram.RelatorioFoco;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

/** Conversão do vínculo e da conversa (colunas planas + {@code dados_fluxo} em jsonb). */
final class VinculoTelegramMapeador {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {};

    private VinculoTelegramMapeador() {}

    static VinculoTelegram paraDominio(VinculoTelegramEntidade e) {
        AudioPendente audio =
                e.audioPendenteFileId == null
                        ? null
                        : new AudioPendente(
                                e.audioPendenteFileId,
                                e.audioPendenteFileUniqueId,
                                e.audioPendenteMime,
                                e.audioPendenteTamanho == null ? 0 : e.audioPendenteTamanho,
                                e.audioPendenteRecebidoEm);
        RelatorioFoco foco =
                e.relatorioFocoId == null
                        ? null
                        : new RelatorioFoco(
                                TipoRelatorio.valueOf(e.relatorioFocoTipo), e.relatorioFocoId);
        ConversaTelegram conversa =
                new ConversaTelegram(
                        e.alunoSelecionadoId,
                        FluxoConversa.valueOf(e.fluxo),
                        EtapaFluxoConversa.valueOf(e.etapaFluxo),
                        JsonPersistencia.ler(e.dadosFluxo, MAPA),
                        audio,
                        foco,
                        e.versaoApresentada);
        return new VinculoTelegram(
                e.id,
                e.professorId,
                e.telegramUsuarioId,
                e.chatId,
                e.vinculadoEm,
                conversa,
                e.atualizadoEm,
                e.bloqueioOtimista);
    }

    static VinculoTelegramEntidade paraEntidade(VinculoTelegram v) {
        ConversaTelegram c = v.conversa();
        VinculoTelegramEntidade e = new VinculoTelegramEntidade();
        e.id = v.id();
        e.professorId = v.professorId();
        e.telegramUsuarioId = v.telegramUsuarioId();
        e.chatId = v.chatId();
        e.vinculadoEm = v.vinculadoEm();
        e.alunoSelecionadoId = c.alunoSelecionadoId();
        e.fluxo = c.fluxo().name();
        e.etapaFluxo = c.etapa().name();
        e.dadosFluxo = JsonPersistencia.escrever(c.dados());
        c.audioPendente()
                .ifPresent(
                        a -> {
                            e.audioPendenteFileId = a.fileId();
                            e.audioPendenteFileUniqueId = a.fileUniqueId();
                            e.audioPendenteMime = a.mime();
                            e.audioPendenteTamanho = a.tamanho();
                            e.audioPendenteRecebidoEm = a.recebidoEm();
                        });
        c.relatorioFoco()
                .ifPresent(
                        f -> {
                            e.relatorioFocoTipo = f.tipo().name();
                            e.relatorioFocoId = f.id();
                        });
        e.versaoApresentada = c.versaoApresentada();
        e.atualizadoEm = v.atualizadoEm();
        e.bloqueioOtimista = v.versao();
        return e;
    }
}
