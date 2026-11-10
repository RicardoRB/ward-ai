package dev.ward.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.project.Toolchain;
import org.junit.jupiter.api.Test;

class AnalysisLanguageToolchainsTest {

  @Test
  void mapsEveryLanguageToItsToolchain() {
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.KOTLIN))
        .isEqualTo(Toolchain.JVM);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.JAVA))
        .isEqualTo(Toolchain.JVM);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.TYPESCRIPT))
        .isEqualTo(Toolchain.NODE);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.JAVASCRIPT))
        .isEqualTo(Toolchain.NODE);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.PYTHON))
        .isEqualTo(Toolchain.PYTHON);
    assertThat(AnalysisLanguageToolchains.toolchainOf(AnalysisLanguage.SWIFT))
        .isEqualTo(Toolchain.SWIFT);
  }
}
