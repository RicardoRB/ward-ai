package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.CrapOptions;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

class TomlCrapCheckTest {

  private static final int CUSTOM = 15;

  private static GateConfiguration crap(final String body) {
    return new TomlConfigurationReader()
        .parse(Toml.parse("version = 2\n[checks.crap]\nuses = \"crap\"\n" + body + "\n"))
        .checks()
        .get("crap");
  }

  @Test
  void acceptsACheckWithNoCommandAndDefaults() {
    final GateConfiguration check = crap("");

    assertThat(check.type()).isEqualTo("crap");
    assertThat(check.command()).isEmpty();
    assertThat(check.crap()).isEqualTo(CrapOptions.defaults());
    assertThat(check.crap().max()).isEqualTo(CrapOptions.DEFAULT_MAX);
  }

  @Test
  void readsALimitAndAStringOrArrayOfReports() {
    assertThat(crap("max = 15\nreport = \"reports/lcov.info\"").crap())
        .isEqualTo(new CrapOptions(java.util.List.of("reports/lcov.info"), CUSTOM));
    assertThat(
            crap("report = [\"api/./coverage/lcov.info\", \"web/coverage/lcov.info\"]")
                .crap()
                .reports())
        .containsExactly("api/coverage/lcov.info", "web/coverage/lcov.info");
  }

  @Test
  void rejectsALimitBelowOneOrOfTheWrongType() {
    for (final String value : new String[] {"0", "-3", "\"x\"", "2.5"}) {
      assertThatThrownBy(() -> crap("max = " + value))
          .isInstanceOf(WardException.class)
          .hasMessageContaining("checks.crap.max");
    }
  }

  @Test
  void rejectsReportsOutsideTheProjectOrOfTheWrongShape() {
    for (final String value :
        new String[] {"\"../other/lcov.info\"", "\"/tmp/x\"", "\"\"", "[]", "[1]", "3"}) {
      assertThatThrownBy(() -> crap("report = " + value))
          .isInstanceOf(WardException.class)
          .hasMessageContaining("checks.crap.report");
    }
  }

  @Test
  void otherChecksCarryNoCrapOptionsAndTheTypeIsNeverInferredFromACommand() {
    final GateConfiguration tests =
        new TomlConfigurationReader()
            .parse(Toml.parse("version = 2\n[checks.crap]\ncommand = \"crap run\"\n"))
            .checks()
            .get("crap");

    assertThat(tests.type()).isNotEqualTo("crap");
    assertThat(tests.crap()).isNull();
  }
}
