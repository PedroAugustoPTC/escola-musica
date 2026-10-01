package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso.Historico;
import br.com.felipeblaft.relatorios.casodeuso.historico.ConsultarHistoricoAlunoCasoDeUso.ItemHistorico;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.entrada.web.seguranca.ProfessorAutenticado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Histórico de relatórios aprovados do aluno (FR-047), com os mesmos rótulos do bot. */
@RestController
public class HistoricoControlador {

    public record ItemHistoricoDto(
            String tipo,
            UUID relatorioId,
            String rotulo,
            LocalDate dataAula,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            Instant aprovadoEm) {}

    public record HistoricoDto(AlunoResumoDto aluno, List<ItemHistoricoDto> itens) {}

    private final ConsultarHistoricoAlunoCasoDeUso consultar;
    private final FormatacaoMensagens formatacao;

    public HistoricoControlador(
            ConsultarHistoricoAlunoCasoDeUso consultar, FormatacaoMensagens formatacao) {
        this.consultar = consultar;
        this.formatacao = formatacao;
    }

    @GetMapping("/api/v1/alunos/{alunoId}/historico")
    public HistoricoDto historico(
            @AuthenticationPrincipal ProfessorAutenticado professor, @PathVariable UUID alunoId) {
        Historico historico = consultar.executar(professor.id(), alunoId);
        return new HistoricoDto(
                InicioMapeador.aluno(historico.aluno()),
                historico.itens().stream().map(this::item).toList());
    }

    private ItemHistoricoDto item(ItemHistorico item) {
        return item.tipo() == TipoRelatorio.AULA
                ? new ItemHistoricoDto(
                        item.tipo().name(),
                        item.relatorioId(),
                        formatacao.rotuloHistorico(item),
                        item.dataAula(),
                        null,
                        null,
                        item.aprovadoEm())
                : new ItemHistoricoDto(
                        item.tipo().name(),
                        item.relatorioId(),
                        formatacao.rotuloHistorico(item),
                        null,
                        item.periodo().inicio(),
                        item.periodo().fim(),
                        item.aprovadoEm());
    }
}
