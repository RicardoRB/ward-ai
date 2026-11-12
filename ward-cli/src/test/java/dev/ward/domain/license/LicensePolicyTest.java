package dev.ward.domain.license;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.LicenseSettings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LicensePolicyTest {

  private final LicensePolicy defaults = new LicensePolicy(LicenseSettings.defaults());

  private static LicenseExpression expression(final String text) {
    return LicenseExpressionParser.parse(text).orElseThrow();
  }

  @Test
  void defaultDeniesGplAndAgplFamilies() {
    final LicenseVerdict gpl = defaults.check(expression("GPL-3.0-only"));
    final LicenseVerdict agpl = defaults.check(expression("AGPL-3.0-or-later"));

    assertThat(gpl.outcome()).isEqualTo(LicenseVerdict.Outcome.DENIED);
    assertThat(gpl.entry()).isEqualTo("deny GPL-*");
    assertThat(gpl.license()).isEqualTo("GPL-3.0-only");
    assertThat(agpl.outcome()).isEqualTo(LicenseVerdict.Outcome.DENIED);
  }

  @Test
  void gplFamilyDoesNotMatchLgpl() {
    assertThat(defaults.check(expression("LGPL-2.1-or-later")).ok()).isTrue();
    assertThat(defaults.check(expression("LGPL-3.0-only")).ok()).isTrue();
  }

  @Test
  void deprecatedIdentifierIsDeniedOnceNormalizedByTheFamily() {
    assertThat(defaults.check(expression("GPL-2.0-only")).ok()).isFalse();
  }

  @Test
  void orPassesWhenOneAlternativeIsNotDenied() {
    assertThat(defaults.check(expression("MIT OR GPL-3.0-only")).ok()).isTrue();
    assertThat(defaults.check(expression("GPL-3.0-only OR MIT")).ok()).isTrue();
    assertThat(defaults.check(expression("GPL-3.0-only OR AGPL-3.0-only")).ok()).isFalse();
  }

  @Test
  void andNeedsBothOperands() {
    assertThat(defaults.check(expression("(MIT OR GPL-3.0-only) AND BSD-3-Clause")).ok()).isTrue();
    assertThat(defaults.check(expression("MIT AND GPL-3.0-only")).ok()).isFalse();
    assertThat(defaults.check(expression("GPL-3.0-only AND MIT")).ok()).isFalse();
  }

  @Test
  void classpathExceptionWaivesTheDenyByDefaultOnly() {
    final LicenseExpression gpl = expression("GPL-2.0-only WITH Classpath-exception-2.0");
    final LicensePolicy none =
        new LicensePolicy(
            new LicenseSettings(
                LicenseSettings.Mode.DENY,
                LicenseSettings.DEFAULT_DENY,
                List.of(),
                Set.of(),
                true));

    assertThat(defaults.check(gpl).ok()).isTrue();
    assertThat(none.check(gpl).ok()).isFalse();
  }
}
