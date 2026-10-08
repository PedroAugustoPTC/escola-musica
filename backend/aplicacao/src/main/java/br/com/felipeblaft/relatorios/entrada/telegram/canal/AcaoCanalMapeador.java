package br.com.felipeblaft.relatorios.entrada.telegram.canal;

import br.com.felipeblaft.relatorios.casodeuso.canal.EmitirDocumentoCanalCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.entrada.telegram.ApresentadorTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.ReferenciaDocumento;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.AcaoDocumentoDto;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.AcaoEdicaoDto;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.AcaoMensagemDto;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.AcaoMensagemEtapaDto;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.AcaoRespostaCallbackDto;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.BotaoDto;
import br.com.felipeblaft.relatorios.entrada.telegram.canal.AcaoDto.TecladoDto;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traduz as saídas do interpretador ({@link SaidaTelegram}) para ações v1 (R-03). É só formato: não
 * tem regra de conversa (FR-005). Textos acima do limite do Telegram são divididos aqui (uma
 * mensagem por parte, teclado só na última), os ids são atribuídos <em>depois</em> da expansão, o
 * PDF vira um identificador opaco e a compensação de cada edição é gerada junto.
 */
@Component
public class AcaoCanalMapeador {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PDF = "application/pdf";

    /**
     * @param noResposta a ação completa (com {@code acaoId}), como vai na resposta HTTP
     * @param payload a ação sem {@code acaoId}, como é guardada na outbox
     * @param compensacao a ação alternativa se a execução falhar por mensagem não editável, ou nulo
     */
    public record AcaoMapeada(
            String acaoId,
            ObjectNode noResposta,
            TipoAcaoCanal tipo,
            long chatId,
            UUID trabalhoId,
            String payload,
            String compensacao) {

        /** Ação pronta para a outbox. */
        public NovaAcao paraNovaAcao(UUID professorId) {
            return new NovaAcao(
                    acaoId, tipo, chatId, professorId, trabalhoId, payload, compensacao);
        }
    }

    private final EmitirDocumentoCanalCasoDeUso emitirDocumento;

    public AcaoCanalMapeador(EmitirDocumentoCanalCasoDeUso emitirDocumento) {
        this.emitirDocumento = emitirDocumento;
    }

    /**
     * @param chatDoUpdate chat de uma {@code RespostaCallback}, que não carrega chat próprio
     * @param idDaPosicao gera o {@code acaoId} a partir da posição na lista já expandida
     */
    public List<AcaoMapeada> mapear(
            List<SaidaTelegram> saidas, long chatDoUpdate, IntFunction<String> idDaPosicao) {
        List<AcaoMapeada> acoes = new ArrayList<>();
        for (SaidaTelegram saida : saidas) {
            switch (saida) {
                case SaidaTelegram.Mensagem m -> mensagem(acoes, m, idDaPosicao);
                case SaidaTelegram.Edicao e -> edicao(acoes, e, idDaPosicao);
                case SaidaTelegram.Documento d -> documento(acoes, d, idDaPosicao);
                case SaidaTelegram.MensagemEtapa e -> etapa(acoes, e, idDaPosicao);
                case SaidaTelegram.RespostaCallback r ->
                        callback(acoes, r, chatDoUpdate, idDaPosicao);
            }
        }
        return acoes;
    }

    /** Reconstitui a ação completa a partir do {@code payload} guardado (reentrega e despacho). */
    public static ObjectNode comAcaoId(String acaoId, String payload) {
        ObjectNode completo = JSON.createObjectNode();
        completo.put("acaoId", acaoId);
        JsonNode lido = JSON.readTree(payload);
        if (lido instanceof ObjectNode objeto) {
            completo.setAll(objeto);
        }
        return completo;
    }

    // ---- por tipo

    private void mensagem(
            List<AcaoMapeada> acoes, SaidaTelegram.Mensagem m, IntFunction<String> id) {
        List<String> partes = ApresentadorTelegram.dividir(m.texto());
        for (int i = 0; i < partes.size(); i++) {
            boolean ultima = i == partes.size() - 1;
            TecladoDto teclado = ultima ? teclado(m.teclado(), false) : null;
            String acaoId = id.apply(acoes.size());
            acoes.add(
                    montar(
                            new AcaoMensagemDto(
                                    acaoId,
                                    AcaoDto.TIPO_MENSAGEM,
                                    m.chatId(),
                                    partes.get(i),
                                    teclado),
                            TipoAcaoCanal.MENSAGEM,
                            m.chatId(),
                            null,
                            null));
        }
    }

    private void edicao(List<AcaoMapeada> acoes, SaidaTelegram.Edicao e, IntFunction<String> id) {
        String acaoId = id.apply(acoes.size());
        AcaoMensagemDto compensacao =
                new AcaoMensagemDto(
                        null,
                        AcaoDto.TIPO_MENSAGEM,
                        e.chatId(),
                        e.texto(),
                        teclado(e.teclado(), false));
        acoes.add(
                montar(
                        new AcaoEdicaoDto(
                                acaoId,
                                AcaoDto.TIPO_EDICAO,
                                e.chatId(),
                                e.mensagemId(),
                                e.texto(),
                                teclado(e.teclado(), true)),
                        TipoAcaoCanal.EDICAO,
                        e.chatId(),
                        null,
                        semAcaoId(compensacao)));
    }

    private void documento(
            List<AcaoMapeada> acoes, SaidaTelegram.Documento d, IntFunction<String> id) {
        ReferenciaDocumento referencia = d.referencia();
        if (referencia == null) {
            throw new IllegalStateException("documento sem referencia para o canal interno");
        }
        String documentoId =
                emitirDocumento.executar(
                        referencia.professorId(),
                        referencia.tipo(),
                        referencia.relatorioId(),
                        referencia.versao(),
                        d.nomeArquivo());
        acoes.add(
                montar(
                        new AcaoDocumentoDto(
                                id.apply(acoes.size()),
                                AcaoDto.TIPO_DOCUMENTO,
                                d.chatId(),
                                documentoId,
                                d.nomeArquivo(),
                                PDF,
                                d.legenda()),
                        TipoAcaoCanal.DOCUMENTO,
                        d.chatId(),
                        null,
                        null));
    }

    private void etapa(
            List<AcaoMapeada> acoes, SaidaTelegram.MensagemEtapa e, IntFunction<String> id) {
        // Numa resposta a update o interpretador já sabe a mensagem a editar (a dos botões); numa
        // ação
        // proativa o despacho a resolve de novo pelo trabalho (a mais recente).
        AcaoMensagemEtapaDto dto =
                new AcaoMensagemEtapaDto(
                        id.apply(acoes.size()),
                        AcaoDto.TIPO_MENSAGEM_ETAPA,
                        e.chatId(),
                        e.texto(),
                        e.trabalhoId().toString(),
                        e.editarMensagemId());
        // a compensação é a mesma ação sem editarMensagemId: envia mensagem nova e regrava o id
        AcaoMensagemEtapaDto semEdicao =
                new AcaoMensagemEtapaDto(
                        null,
                        AcaoDto.TIPO_MENSAGEM_ETAPA,
                        e.chatId(),
                        e.texto(),
                        e.trabalhoId().toString(),
                        null);
        acoes.add(
                montar(
                        dto,
                        TipoAcaoCanal.MENSAGEM_ETAPA,
                        e.chatId(),
                        e.trabalhoId(),
                        semAcaoId(semEdicao)));
    }

    private void callback(
            List<AcaoMapeada> acoes,
            SaidaTelegram.RespostaCallback r,
            long chatDoUpdate,
            IntFunction<String> id) {
        acoes.add(
                montar(
                        new AcaoRespostaCallbackDto(
                                id.apply(acoes.size()),
                                AcaoDto.TIPO_RESPOSTA_CALLBACK,
                                r.callbackId()),
                        TipoAcaoCanal.RESPOSTA_CALLBACK,
                        chatDoUpdate,
                        null,
                        null));
    }

    // ---- apoio

    private static AcaoMapeada montar(
            AcaoDto dto, TipoAcaoCanal tipo, long chatId, UUID trabalhoId, String compensacao) {
        ObjectNode resposta = JSON.valueToTree(dto);
        ObjectNode semId = resposta.deepCopy();
        semId.remove("acaoId");
        return new AcaoMapeada(
                dto.acaoId(), resposta, tipo, chatId, trabalhoId, semId.toString(), compensacao);
    }

    private static String semAcaoId(AcaoDto dto) {
        ObjectNode no = JSON.valueToTree(dto);
        no.remove("acaoId");
        return no.toString();
    }

    /** Teclado nulo ou vazio some do JSON; numa edição vazio é explícito (remove os botões). */
    private static TecladoDto teclado(SaidaTelegram.Teclado teclado, boolean explicitoSeVazio) {
        if (teclado == null || teclado.vazio()) {
            return explicitoSeVazio ? new TecladoDto(List.of()) : null;
        }
        return new TecladoDto(
                teclado.linhas().stream()
                        .map(
                                linha ->
                                        linha.stream()
                                                .map(b -> new BotaoDto(b.rotulo(), b.dados()))
                                                .toList())
                        .toList());
    }
}
