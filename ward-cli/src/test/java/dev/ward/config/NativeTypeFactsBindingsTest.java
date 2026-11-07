package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks that the engine's native type-fact registry selects each native resolver. */
final class NativeTypeFactsBindingsTest {

  @Test
  void eachNativeResolverIsRegisteredForItsLanguages() {
    final Map<AnalysisLanguage, ?> sources =
        NativeTypeFactsBindings.forProject(
            Path.of("."), new AntlrLanguageFrontend(), Optional.empty());

    assertThat(sources)
        .containsKeys(
            AnalysisLanguage.JAVA,
            AnalysisLanguage.KOTLIN,
            AnalysisLanguage.SWIFT,
            AnalysisLanguage.PYTHON);
    assertThat(sources.get(AnalysisLanguage.SWIFT)).isInstanceOf(NativeSwiftTypes.class);
  }
}
