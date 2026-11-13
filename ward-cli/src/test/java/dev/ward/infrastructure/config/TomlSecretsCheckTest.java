package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

/** The {@code secrets} check type and its keys in {@code ward.toml}. */
class TomlSecretsCheckTest {

  private static final LocalDate SINCE = LocalDate.of(2026, 1, 1);

  private static GateConfiguration secrets(final String body) {
    return new TomlConfigurationReader()
        .parse(Toml.parse("version = 2\n[checks.secrets]\n" + body))
        .checks()
        .get("secrets");
  }

  @Test
  void aSecretsCheckNeedsNoCommand() {
    final GateConfiguration check = secrets("uses = \"secrets\"\n");

    assertThat(check.type()).isEqualTo("secrets");
    assertThat(check.command()).isEmpty();
    assertThat(check.secrets().scanHistory()).isFalse();
  }

  @Test
  void theGitleaksTypeIsRejectedAsUnknown() {
    assertThatThrownBy(() -> secrets("uses = \"gitleaks\"\ncommand = \"gitleaks detect\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.secrets.uses")
        .hasMessageContaining("secrets");
  }

  @Test
  void aCommandThatRunsGitleaksIsAPlainCommandCheck() {
    final GateConfiguration check = secrets("command = \"gitleaks detect --no-git\"\n");

    assertThat(check.type()).isEqualTo("command");
  }

  @Test
  void readsTheHistoryAndExcludeOptions() {
    final GateConfiguration check =
        secrets(
            "uses = \"secrets\"\nscan-history = true\nhistory-since = \"2026-01-01\"\n"
                + "exclude-paths = [\"fixtures/**\"]\n");

    assertThat(check.secrets().scanHistory()).isTrue();
    assertThat(check.secrets().historySince()).isEqualTo(Optional.of(SINCE));
    assertThat(check.secrets().excludePaths()).isEqualTo(List.of("fixtures/**"));
  }

  @Test
  void sinceWithoutHistoryIsAConfigurationErrorNamingTheKey() {
    assertThatThrownBy(() -> secrets("uses = \"secrets\"\nhistory-since = \"2026-01-01\"\n"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.secrets.history-since");
  }

  @Test
  void rejectsBadValuesAndUnknownKeys() {
    final List<String> bodies =
        List.of(
            "scan-history = \"yes\"\n",
            "scan-history = true\nhistory-since = \"yesterday\"\n",
            "exclude-paths = \"x\"\n",
            "exclude-paths = [1]\n",
            "command = \"x\"\n",
            "extra = 1\n");
    for (final String body : bodies) {
      assertThatThrownBy(() -> secrets("uses = \"secrets\"\n" + body))
          .as(body)
          .isInstanceOf(WardException.class)
          .hasMessageContaining("checks.secrets");
    }
  }
}
