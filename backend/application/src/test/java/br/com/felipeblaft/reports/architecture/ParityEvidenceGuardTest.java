package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Trava do CI da remoção do caminho antigo (feature 004, FR-029): reprova o build se a evidência de
 * paridade faltar ou estiver incompleta. Sem a prova (ou a dispensa formal do mantenedor), o
 * transporte antigo não pode ficar removido.
 */
class ParityEvidenceGuardTest {

    @Test
    @DisplayName("a evidência de paridade existe na main e está completa ou formalmente dispensada")
    void parityEvidenceIsCompleteOrWaived() throws IOException {
        Path file = GlossaryFile.findRoot().resolve(ParityEvidenceGuard.PATH);
        String content = Files.exists(file) ? Files.readString(file) : null;

        List<String> violations = ParityEvidenceGuard.check(content);

        assertThat(violations).as("violações de %s (FR-029)", ParityEvidenceGuard.PATH).isEmpty();
    }
}
