package dev.ward.domain.license;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.LicenseSettings;
import org.junit.jupiter.api.Test;

class LicenseSettingsTest {

  @Test
  void defaultsDenyGplAndAgplWithTheClasspathException() {
    final LicenseSettings defaults = LicenseSettings.defaults();

    assertThat(defaults.mode()).isEqualTo(LicenseSettings.Mode.DENY);
    assertThat(defaults.patterns()).containsExactly("GPL-*", "AGPL-*");
    assertThat(defaults.exceptions()).containsExactly("Classpath-exception-2.0");
    assertThat(defaults.scopes()).isEmpty();
    assertThat(defaults.unknownFails()).isTrue();
  }

  @Test
  void validatesPatternSyntax() {
    assertThat(LicenseSettings.validPattern("GPL-*")).isTrue();
    assertThat(LicenseSettings.validPattern("Apache-2.0")).isTrue();
    assertThat(LicenseSettings.validPattern("*GPL")).isFalse();
    assertThat(LicenseSettings.validPattern("")).isFalse();
    assertThat(LicenseSettings.validPattern("a b")).isFalse();
  }

  @Test
  void expressionsRenderWithTheParenthesesPrecedenceNeeds() {
    assertThat(LicenseExpressionParser.parse("(A OR B) AND C").orElseThrow().render())
        .isEqualTo("(A OR B) AND C");
    assertThat(LicenseExpressionParser.parse("A AND (B OR C)").orElseThrow().render())
        .isEqualTo("A AND (B OR C)");
  }
}
