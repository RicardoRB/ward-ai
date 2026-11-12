package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every Python gate type named in {@code uses} is a known type the factory builds. */
class PythonGatesTest {

  @ParameterizedTest
  @ValueSource(strings = {"mypy", "pytest", "coverage", "mutmut"})
  void pythonTypeIsAcceptedAndBuilt(final String type) {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThat(
            factory.create(
                new WardConfiguration(1, Map.of(type, MutationGateFixtures.gate(type, null)))))
        .as("gate for %s", type)
        .singleElement();
  }

  @Test
  void ruffIsNoLongerAGateType() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(
            () ->
                factory.create(
                    new WardConfiguration(
                        1, Map.of("ruff", MutationGateFixtures.gate("ruff", null)))))
        .isInstanceOf(WardException.class);
  }
}
