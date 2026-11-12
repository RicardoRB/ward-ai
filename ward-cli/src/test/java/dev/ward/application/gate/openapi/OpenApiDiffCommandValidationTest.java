package dev.ward.application.gate.openapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.gate.QualityGateFactory;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OpenApiDiffCommandValidationTest {
  private static final String LINT = "openapi-lint";
  private static final String VACUUM = "vacuum";

  private static List<String> lint(final String... flags) {
    return command(VACUUM, "lint", flags);
  }

  private static List<String> command(
      final String executable, final String subcommand, final String... flags) {
    return Stream.concat(Stream.of(executable, subcommand), Arrays.stream(flags)).toList();
  }

  @Test
  void factoryRejectsInvalidCommandWithConfigurationError() {
    final Map<String, GateConfiguration> gates =
        Map.of(
            LINT,
            new GateConfiguration(
                LINT,
                LINT,
                true,
                lint("-n", "warn"),
                Set.of("default"),
                null,
                null,
                new OpenApiGateOptions("openapi.yml", null),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null));
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(new WardConfiguration(1, gates)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("--hard-mode");
  }
}
