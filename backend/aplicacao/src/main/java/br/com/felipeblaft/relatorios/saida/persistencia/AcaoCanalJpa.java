package br.com.felipeblaft.relatorios.saida.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AcaoCanalJpa extends JpaRepository<AcaoCanalEntidade, String> {

    /** Insere a ação; devolve 0 se o id já existia (nunca sobrescreve, FR-014). */
    @Modifying
    @Query(
            value =
                    "insert into acao_canal (id, lote_id, canal, origem, update_id, chat_id,"
                            + " professor_id, trabalho_id, tipo, payload, compensacao, estado,"
                            + " tentativas, proxima_tentativa_em, prazo_em, versao, criado_em,"
                            + " entregue_em, finalizado_em, codigo_motivo)"
                            + " values (:id, :loteId, :canal, :origem, :updateId, :chatId,"
                            + " :professorId, :trabalhoId, :tipo, cast(:payload as jsonb),"
                            + " cast(:compensacao as jsonb), :estado, :tentativas,"
                            + " :proximaTentativaEm, :prazoEm, :versao, :criadoEm, :entregueEm,"
                            + " :finalizadoEm, :codigoMotivo)"
                            + " on conflict (id) do nothing",
            nativeQuery = true)
    @SuppressWarnings("java:S107")
    int inserirSeNova(
            String id,
            String loteId,
            String canal,
            String origem,
            Long updateId,
            long chatId,
            UUID professorId,
            UUID trabalhoId,
            String tipo,
            String payload,
            String compensacao,
            String estado,
            int tentativas,
            Instant proximaTentativaEm,
            Instant prazoEm,
            int versao,
            Instant criadoEm,
            Instant entregueEm,
            Instant finalizadoEm,
            String codigoMotivo);

    List<AcaoCanalEntidade> findByUpdateIdOrderBySeq(Long updateId);

    List<AcaoCanalEntidade> findByLoteIdOrderBySeq(String loteId);

    /**
     * Primeira ação devida que seja a primeira pendente do seu lote (um despachante nunca pega o
     * meio de um lote que outro está reservando) e que não esteja atrás, no mesmo chat, de ação de
     * outro lote ainda {@code PENDENTE} ou {@code ENTREGUE} há menos que a espera máxima (portão de
     * ordem, FR-016). {@code skip locked} faz dois despachantes nunca disputarem a mesma linha.
     */
    @Query(
            value =
                    "select a.lote_id from acao_canal a"
                            + " where a.estado = 'PENDENTE' and a.proxima_tentativa_em <= :agora"
                            + " and a.seq = (select min(p.seq) from acao_canal p"
                            + "              where p.lote_id = a.lote_id and p.estado = 'PENDENTE')"
                            + " and not exists (select 1 from acao_canal b"
                            + "   where b.chat_id = a.chat_id and b.seq < a.seq"
                            + "     and b.lote_id <> a.lote_id"
                            + "     and (b.estado = 'PENDENTE'"
                            + "          or (b.estado = 'ENTREGUE' and b.entregue_em > :limiteConfirmacao)))"
                            + " order by a.seq limit 1 for update of a skip locked",
            nativeQuery = true)
    Optional<String> proximoLoteDevido(Instant agora, Instant limiteConfirmacao);

    /** Lease: empurra a próxima tentativa do lote para o futuro e avança a versão. */
    @Modifying
    @Query(
            value =
                    "update acao_canal set proxima_tentativa_em = :leaseAte, versao = versao + 1"
                            + " where lote_id = :loteId and estado = 'PENDENTE'",
            nativeQuery = true)
    int reservarLote(String loteId, Instant leaseAte);

    /** Grava o novo estado só se a versão ainda for a esperada (controle otimista). */
    @Modifying
    @Query(
            value =
                    "update acao_canal set estado = :estado, tentativas = :tentativas,"
                            + " proxima_tentativa_em = :proximaTentativaEm, entregue_em = :entregueEm,"
                            + " finalizado_em = :finalizadoEm, codigo_motivo = :codigoMotivo,"
                            + " payload = cast(:payload as jsonb),"
                            + " compensacao = cast(:compensacao as jsonb), versao = versao + 1"
                            + " where id = :id and versao = :versaoEsperada",
            nativeQuery = true)
    @SuppressWarnings("java:S107")
    int atualizarSeVersao(
            String id,
            int versaoEsperada,
            String estado,
            int tentativas,
            Instant proximaTentativaEm,
            Instant entregueEm,
            Instant finalizadoEm,
            String codigoMotivo,
            String payload,
            String compensacao);

    @Modifying
    @Query(
            value =
                    "update acao_canal set payload = null, compensacao = null, versao = versao + 1"
                            + " where finalizado_em is not null and finalizado_em < :limite"
                            + " and (payload is not null or compensacao is not null)",
            nativeQuery = true)
    int zerarConteudoFinalizadoAte(Instant limite);

    @Modifying
    @Query(
            value =
                    "delete from acao_canal where finalizado_em is not null"
                            + " and finalizado_em < :limite",
            nativeQuery = true)
    int removerFinalizadasAte(Instant limite);

    @Query("select count(a) from AcaoCanalEntidade a where a.estado = 'PENDENTE'")
    long contarPendentes();

    @Query("select min(a.criadoEm) from AcaoCanalEntidade a where a.estado = 'PENDENTE'")
    Optional<Instant> criadaMaisAntigaPendente();

    /** Ações entregues antes do limite e ainda sem confirmação. */
    @Query(
            "select count(a) from AcaoCanalEntidade a where a.estado = 'ENTREGUE'"
                    + " and a.entregueEm < :limite")
    long contarEntreguesDesde(Instant limite);
}
