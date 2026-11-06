package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.TypeSidecar;
import dev.ward.infrastructure.analysis.swift.NativeSwiftTypes;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks that the engine's per-language type provider selects the native Swift resolver. */
final class TypeSidecarBindingsTest {

  @Test
  void swiftTypesComeFromTheNativeResolver() {
    final Map<AnalysisLanguage, TypeSidecar> sidecars =
        TypeSidecarBindings.forProject(Path.of("."), "0.6.0");

    assertThat(sidecars.get(AnalysisLanguage.SWIFT)).isInstanceOf(NativeSwiftTypes.class);
  }
}
