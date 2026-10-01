package br.com.felipeblaft.relatorios.entrada.carga;

import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conteúdos fictícios dos relatórios de aula da massa de demonstração, variados por instrumento e
 * pela posição da aula, para que o histórico e o semestral tenham textos diferentes entre si.
 */
final class ConteudosDemonstracao {

    static final List<String> PERGUNTAS =
            List.of(
                    "O aluno estudou em casa durante a semana?",
                    "Que repertório o aluno deve preparar para a próxima aula?");

    /** Repertório, técnica, dificuldade e atividade típicos de cada instrumento. */
    private record Instrumento(
            List<String> repertorio,
            List<String> tecnica,
            List<String> dificuldades,
            List<String> atividades) {}

    private static final Instrumento PADRAO =
            new Instrumento(
                    List.of(
                            "Peça de estudo do método, primeira parte",
                            "Peça de estudo do método, segunda parte",
                            "Repertório livre escolhido pelo aluno"),
                    List.of(
                            "Aquecimento e postura",
                            "Leitura rítmica com metrônomo",
                            "Escalas maiores em uma oitava"),
                    List.of("Manter o andamento estável", "Leitura à primeira vista"),
                    List.of(
                            "Estudar 15 minutos por dia com metrônomo",
                            "Revisar a peça da semana em andamento lento"));

    private static final Map<String, Instrumento> INSTRUMENTOS =
            Map.of(
                    "piano",
                    new Instrumento(
                            List.of(
                                    "Estudo nº 5 de Czerny, op. 599",
                                    "Minueto em Sol maior (Petzold)",
                                    "Sonatina em Dó maior de Clementi, 1º movimento",
                                    "Prelúdio em Dó maior de Bach"),
                            List.of(
                                    "Escala de Sol maior em duas oitavas, mãos juntas",
                                    "Arpejos de Lá menor",
                                    "Independência entre as mãos",
                                    "Dinâmica piano e forte"),
                            List.of(
                                    "Independência entre as mãos em passagens rápidas",
                                    "Leitura em clave de fá ainda lenta",
                                    "Uso do pedal sem borrar a harmonia"),
                            List.of(
                                    "Estudar os compassos difíceis com mãos separadas",
                                    "Escala de Sol maior com metrônomo a 72 bpm",
                                    "Ler uma linha do método de leitura por dia")),
                    "violão",
                    new Instrumento(
                            List.of(
                                    "Estudo em Mi menor de Carulli",
                                    "Lágrima de Tárrega",
                                    "Asa Branca (arranjo simples)",
                                    "Estudo nº 1 de Villa-Lobos (trecho inicial)"),
                            List.of(
                                    "Arpejos p-i-m-a",
                                    "Pestana na 2ª casa",
                                    "Escala cromática com alternância i-m",
                                    "Ligados ascendentes e descendentes"),
                            List.of(
                                    "Pestana ainda com cordas abafadas",
                                    "Troca rápida entre acordes",
                                    "Regularidade do polegar no baixo"),
                            List.of(
                                    "Exercício de pestana por 5 minutos diários",
                                    "Encadeamento Mi menor–Lá menor–Si7 com metrônomo",
                                    "Tocar o estudo inteiro em andamento lento")),
                    "violino",
                    new Instrumento(
                            List.of(
                                    "Minueto nº 1 do Suzuki, livro 1",
                                    "Estudo de Wohlfahrt op. 45 nº 1",
                                    "Canção folclórica com cordas soltas",
                                    "Gavota de Gossec"),
                            List.of(
                                    "Arco inteiro em cordas soltas",
                                    "Afinação da 1ª posição",
                                    "Escala de Ré maior em uma oitava",
                                    "Détaché e legato"),
                            List.of(
                                    "Afinação do 3º dedo na corda Lá",
                                    "Arco torto perto da ponta",
                                    "Postura do braço esquerdo"),
                            List.of(
                                    "Arcadas longas diante do espelho",
                                    "Escala de Ré maior com afinador",
                                    "Tocar o minueto devagar, em partes")),
                    "canto",
                    new Instrumento(
                            List.of(
                                    "Vocalises de Concone, nº 1",
                                    "Caro mio ben (Giordani)",
                                    "Canção brasileira: Azulão (Jaime Ovalle)"),
                            List.of(
                                    "Respiração costal-diafragmática",
                                    "Vocalizes em escala de cinco notas",
                                    "Ressonância e articulação das vogais"),
                            List.of(
                                    "Apoio respiratório nas frases longas",
                                    "Afinação nas notas agudas",
                                    "Tensão no maxilar"),
                            List.of(
                                    "Exercícios de respiração 10 minutos por dia",
                                    "Cantar a canção em vocalize antes do texto",
                                    "Gravar-se e ouvir a afinação")),
                    "flauta",
                    new Instrumento(
                            List.of(
                                    "Estudos melódicos de Gariboldi",
                                    "Sicilienne de Fauré (trecho)",
                                    "Melodias folclóricas em Ré maior"),
                            List.of(
                                    "Sons longos e qualidade do timbre",
                                    "Golpe de língua simples",
                                    "Escala de Ré maior em duas oitavas"),
                            List.of("Notas graves sem ar", "Respiração no meio das frases"),
                            List.of(
                                    "Sons longos diários com afinador",
                                    "Golpe de língua com metrônomo")),
                    "bateria",
                    new Instrumento(
                            List.of(
                                    "Groove de rock básico",
                                    "Levada de samba simplificada",
                                    "Viradas de uma e duas casas"),
                            List.of(
                                    "Rudimentos: toque simples e toque duplo",
                                    "Coordenação bumbo e caixa",
                                    "Contratempo no chimbal"),
                            List.of("Manter o tempo nas viradas", "Independência do pé esquerdo"),
                            List.of(
                                    "Rudimentos com metrônomo a 80 bpm",
                                    "Tocar o groove junto com uma gravação")));

    private static final List<String> EVOLUCOES =
            List.of(
                    "O aluno mostrou mais segurança em relação à aula anterior e manteve o andamento estável.",
                    "Houve boa evolução na postura e na qualidade do som; o aluno chegou com a lição estudada.",
                    "A aula foi produtiva: o aluno corrigiu rapidamente os pontos apontados.",
                    "O aluno avançou no repertório e já toca a peça inteira, ainda em andamento lento.",
                    "Progresso consistente; o estudo em casa está aparecendo na regularidade da execução.");

    private static final List<String> OBSERVACOES =
            List.of(
                    "",
                    "Chegou pontualmente e com o material.",
                    "Pediu para incluir uma música do seu gosto no repertório.",
                    "",
                    "Combinamos uma apresentação curta para a família no fim do mês.");

    private ConteudosDemonstracao() {}

    static ConteudoRelatorioAula aula(String instrumento, int indice) {
        Instrumento catalogo =
                INSTRUMENTOS.getOrDefault(
                        instrumento == null ? "" : instrumento.toLowerCase(Locale.ROOT), PADRAO);
        return new ConteudoRelatorioAula(
                List.of(item(catalogo.repertorio(), indice), item(catalogo.tecnica(), indice)),
                item(EVOLUCOES, indice),
                List.of(item(catalogo.dificuldades(), indice)),
                List.of(item(catalogo.atividades(), indice)),
                item(OBSERVACOES, indice));
    }

    private static String item(List<String> lista, int indice) {
        return lista.get(Math.floorMod(indice, lista.size()));
    }
}
