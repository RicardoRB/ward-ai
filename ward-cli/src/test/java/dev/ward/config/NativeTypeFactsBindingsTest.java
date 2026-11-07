package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Checks that the engine's native type-fact registry selects the native Swift resolver. */
final class NativeTypeFactsBindingsTest {

  @Test
  void swiftTypesComeFromTheNativeResolver() {
    assertThat(NativeTypeFactsBindings.forProject(Path.of(".")).get(AnalysisLanguage.SWIFT))
        .isInstanceOf(NativeSwiftTypes.class);
  }
}
