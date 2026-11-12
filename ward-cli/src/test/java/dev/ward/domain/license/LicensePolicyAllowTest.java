package dev.ward.domain.license;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.LicenseSettings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicensePolicyAllowTest {

  private static LicenseExpression expression(final String text) {
    return LicenseExpressionParser.parse(text).orElseThrow();
  }

  private static LicensePolicy allow(final String... ids) {
    return new LicensePolicy(
        new LicenseSettings(LicenseSettings.Mode.ALLOW, List.of(ids), List.of(), Set.of(), true));
  }

  @Test
  void allowListFailsWhatItDoesNotList() {
    final LicenseVerdict verdict = allow("Apache-2.0", "MIT").check(expression("EPL-2.0"));

    assertThat(verdict.outcome()).isEqualTo(LicenseVerdict.Outcome.NOT_ALLOWED);
    assertThat(verdict.license()).isEqualTo("EPL-2.0");
    assertThat(allow("Apache-2.0", "MIT").check(expression("MIT")).ok()).isTrue();
  }

  @Test
  void allowListUsesAlternativesAndFamilies() {
    assertThat(allow("Apache-2.0").check(expression("Apache-2.0 OR LGPL-2.1-or-later")).ok())
        .isTrue();
    assertThat(allow("Apache-2.0").check(expression("Apache-2.0 AND LGPL-2.1-or-later")).ok())
        .isFalse();
    assertThat(allow("LGPL-*").check(expression("LGPL-3.0-only")).ok()).isTrue();
    assertThat(allow("LicenseRef-*").check(expression("LicenseRef-Acme")).ok()).isTrue();
  }

  @Test
  void patternsMatchWithoutRegardToCase() {
    assertThat(allow("mit").check(expression("MIT")).ok()).isTrue();
  }
}
