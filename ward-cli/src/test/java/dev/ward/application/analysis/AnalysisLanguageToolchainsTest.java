package dev.ward.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.project.Toolchain;
import org.junit.jupiter.api.Test;

class AnalysisLanguageToolchainsTest {

  @Test
  void mapsEveryLanguageToItsToolchain() {
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.KOTLIN))
        .contains(Toolchain.JVM);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.JAVA))
        .contains(Toolchain.JVM);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.TYPESCRIPT))
        .contains(Toolchain.NODE);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.JAVASCRIPT))
        .contains(Toolchain.NODE);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.PYTHON))
        .contains(Toolchain.PYTHON);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.SWIFT))
        .contains(Toolchain.SWIFT);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.OPENAPI)).isEmpty();
  }
}
