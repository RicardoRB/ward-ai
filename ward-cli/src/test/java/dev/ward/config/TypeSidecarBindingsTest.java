package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests that the engine has a type sidecar for Python, which needs no process. */
class TypeSidecarBindingsTest {

  @Test
  void pythonHasItsNativeSidecar() {
    final Map<AnalysisLanguage, TypeSidecar> sidecars =
        TypeSidecarBindings.forProject(Path.of("."), "test", new AntlrLanguageFrontend());

    assertThat(sidecars).containsKey(AnalysisLanguage.PYTHON);
    assertThat(sidecars.get(AnalysisLanguage.PYTHON).start()).isPresent();
  }
}
