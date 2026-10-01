package br.com.felipeblaft.relatorios.entrada.carga;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No perfil {@code carga}, executa o comando pedido na linha de comando e encerra o processo com o
 * código de saída do comando. Os argumentos de configuração do Spring ({@code --spring.*}) são
 * ignorados.
 */
@Component
@Profile("carga & !teste")
class ComandosCargaExecutor implements ApplicationRunner {

    private final CargaComando carga;
    private final EmitirCodigoComando emitirCodigo;
    private final MassaDemonstracaoComando massa;
    private final ApplicationContext contexto;

    ComandosCargaExecutor(
            CargaComando carga,
            EmitirCodigoComando emitirCodigo,
            MassaDemonstracaoComando massa,
            ApplicationContext contexto) {
        this.carga = carga;
        this.emitirCodigo = emitirCodigo;
        this.massa = massa;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments argumentosAplicacao) {
        List<String> argumentos =
                List.of(argumentosAplicacao.getSourceArgs()).stream()
                        .filter(argumento -> !argumento.startsWith("--spring."))
                        .toList();
        PrintStream saida = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String comando = argumentos.isEmpty() ? "" : argumentos.getFirst();
        int codigoSaida =
                switch (comando) {
                    case CargaComando.NOME -> carga.executar(argumentos, saida);
                    case EmitirCodigoComando.NOME -> emitirCodigo.executar(argumentos, saida);
                    case MassaDemonstracaoComando.NOME -> massa.executar(argumentos, saida);
                    default -> {
                        saida.println(
                                "Comandos: carga --arquivo <arquivo.json> [--simular] | emitir-codigo --email <email>"
                                        + " | massa --arquivo <cadastros.json>");
                        yield 2;
                    }
                };
        System.exit(SpringApplication.exit(contexto, () -> codigoSaida));
    }
}
