package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Fails the build when an analysis language has no native formatter. OpenAPI documents are data,
 * and Ward does not format YAML or JSON. Rust and C# are read by the analysis engine since
 * stories R2 and C2 of epic #642 and get their formatters in stories R3 and C3, which remove them
 * from the list below.
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
                    AnalysisLanguage.RUST,
                    AnalysisLanguage.CSHARP
                )
            )
        );
  }
}
