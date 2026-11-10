package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** Fails the build when an analysis language has no native formatter. */
class FormatterRegistryContractTest {

  @Test
  void everyAnalysisLanguageHasAFormatter() {
    assertThat(FormatterBindingsModule.formatterRegistry().languages())
        .as("every AnalysisLanguage needs a formatter in FormatterBindingsModule")
        .isEqualTo(EnumSet.allOf(AnalysisLanguage.class));
  }
}
