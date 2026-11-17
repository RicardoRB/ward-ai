package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Checks that the engine's native type-fact registry selects each native resolver. */
final class NativeTypeFactsBindingsTest {
  @Test
  void eachNativeResolverIsRegisteredForItsLanguages() {
    final Map<AnalysisLanguage, ?> sources =
        NativeTypeFactsBindings.forProject(
            Path.of("."),
            new AntlrLanguageFrontend(),
            Optional.empty(),
            Optional.of(Path.of(System.getProperty("java.home")))
        );

    assertThat(sources)
        .containsKeys(
            AnalysisLanguage.JAVA,
            AnalysisLanguage.KOTLIN,
            AnalysisLanguage.SWIFT,
            AnalysisLanguage.PYTHON
        );
    assertThat(sources.get(AnalysisLanguage.SWIFT)).isInstanceOf(NativeSwiftTypes.class);
  }

  @Test
  @DisplayName("no JDK at all")
  void noJdkAtAllLeavesJavaAndKotlinWithoutTypeFacts() {
    // Given a Java project and no JDK anywhere Ward looks
    // When the native type facts are wired
    final Map<AnalysisLanguage, ?> sources =
        NativeTypeFactsBindings.forProject(
            Path.of("."),
            new AntlrLanguageFrontend(),
            Optional.empty(),
            Optional.empty()
        );

    // Then Java and Kotlin have no source, so their TYPES rules are reported as not run
    assertThat(sources).doesNotContainKeys(AnalysisLanguage.JAVA, AnalysisLanguage.KOTLIN);
    assertThat(sources).containsKeys(AnalysisLanguage.SWIFT, AnalysisLanguage.PYTHON);
  }
}
