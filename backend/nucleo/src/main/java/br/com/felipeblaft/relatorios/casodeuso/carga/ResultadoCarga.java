package br.com.felipeblaft.relatorios.casodeuso.carga;

import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import java.time.Instant;
import java.util.List;

/**
 * Resumo da carga. Os erros identificam a linha pelo índice e pelo campo, nunca pelo valor do CPF.
 * Os códigos em claro aparecem só aqui, para a saída do terminal.
 */
public record ResultadoCarga(
        boolean simulacao,
        Contagem professores,
        Contagem alunos,
        int associacoesCriadas,
        List<ErroCarga> erros,
        List<CodigoEmitido> codigosEmitidos) {

    public ResultadoCarga {
        erros = List.copyOf(erros);
        codigosEmitidos = List.copyOf(codigosEmitidos);
    }

    public boolean sucesso() {
        return erros.isEmpty();
    }

    public record Contagem(int criados, int atualizados, int inalterados) {}

    /** {@code secao} é "professores" ou "alunos"; {@code indice} começa em 0. */
    public record ErroCarga(String secao, int indice, String campo, CodigoErro codigo) {}

    public record CodigoEmitido(String email, String codigo, Instant validoAte) {

        @Override
        public String toString() {
            return "CodigoEmitido[email=" + email + ", codigo=***]";
        }
    }
}
