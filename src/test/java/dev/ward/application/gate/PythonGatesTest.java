package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardConfiguration;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every Python gate type named in {@code uses} is a known type the factory builds. */
class PythonGatesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ruff-format",
        "ruff",
        "mypy",
        "pytest",
        "coverage",
        "mutmut",
        "import-linter",
        "pip-audit"
      })
  void pythonTypeIsAcceptedAndBuilt(final String type) {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThat(
            factory.create(
                new WardConfiguration(1, Map.of(type, MutationGateFixtures.gate(type, null)))))
        .as("gate for %s", type)
        .singleElement();
  }
}
