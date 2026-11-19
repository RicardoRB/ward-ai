package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Fails the build when an analysis language has no native formatter. OpenAPI documents are data,
 * and Ward does not format YAML or JSON. Rust is read by the analysis engine since story R2 of
 * epic #642 and gets its formatter in story R3, which removes it from the list below.
 */
class FormatterRegistryContractTest {
  @Test
  void everyAnalysisLanguageHasAFormatter() {
    assertThat(FormatterBindingsModule.formatterRegistry().languages())
        .as("every AnalysisLanguage needs a formatter in FormatterBindingsModule")
        .isEqualTo(
            EnumSet.complementOf(
                EnumSet.of(
                    AnalysisLanguage.OPENAPI,
                    AnalysisLanguage.ANDROID,
                    AnalysisLanguage.RUST
                )
            )
        );
  }
}
