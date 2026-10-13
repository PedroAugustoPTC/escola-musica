package br.com.felipeblaft.reports.saida.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "vinculo_telegram")
public class VinculoTelegramEntidade {

    @Id UUID id;

    @Column(name = "professor_id", nullable = false)
    UUID professorId;

    @Column(name = "telegram_usuario_id", nullable = false)
    long telegramUsuarioId;

    @Column(name = "chat_id", nullable = false)
    long chatId;

    @Column(name = "vinculado_em", nullable = false)
    Instant vinculadoEm;

    @Column(name = "aluno_selecionado_id")
    UUID alunoSelecionadoId;

    @Column(nullable = false)
    String fluxo;

    @Column(name = "etapa_fluxo", nullable = false)
    String etapaFluxo;

    @Column(name = "dados_fluxo", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    String dadosFluxo;

    @Column(name = "audio_pendente_file_id")
    String audioPendenteFileId;

    @Column(name = "audio_pendente_file_unique_id")
    String audioPendenteFileUniqueId;

    @Column(name = "audio_pendente_mime")
    String audioPendenteMime;

    @Column(name = "audio_pendente_tamanho")
    Long audioPendenteTamanho;

    @Column(name = "audio_pendente_recebido_em")
    Instant audioPendenteRecebidoEm;

    @Column(name = "relatorio_foco_tipo")
    String relatorioFocoTipo;

    @Column(name = "relatorio_foco_id")
    UUID relatorioFocoId;

    @Column(name = "versao_apresentada")
    Integer versaoApresentada;

    @Column(name = "atualizado_em", nullable = false)
    Instant atualizadoEm;

    @Version
    @Column(name = "bloqueio_otimista", nullable = false)
    long bloqueioOtimista;

    protected VinculoTelegramEntidade() {}
}
