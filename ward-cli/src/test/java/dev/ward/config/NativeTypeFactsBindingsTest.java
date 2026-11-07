package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests that Python is registered on the native type-fact seam. */
class NativeTypeFactsBindingsTest {

  @Test
  void pythonHasItsNativeTypeFacts() {
    final Map<AnalysisLanguage, TypeFacts> sources =
        NativeTypeFactsBindings.forProject(Path.of("."), new AntlrLanguageFrontend());

    assertThat(sources).containsKey(AnalysisLanguage.PYTHON);
  }
}
