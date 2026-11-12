package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PresetGateOrderTest {
  private static final List<String> STANDARD =
      List.of("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage");

  @Test
  void explicitGatesFollowPresetGatesWithoutDuplicates() {
    assertThat(PresetGateOrder.withExplicitGates(STANDARD, List.of("trivy", "tests")))
        .containsExactly(
            "compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage", "trivy");
  }

  @Test
  void explicitGatesKeepTheirGivenOrder() {
    assertThat(PresetGateOrder.withExplicitGates(STANDARD, List.of("sonar", "trivy", "sonar")))
        .endsWith("sonar", "trivy");
  }

  @Test
  void noExplicitGatesReturnsThePresetGateSet() {
    assertThat(PresetGateOrder.withExplicitGates(STANDARD, List.of()))
        .containsExactlyElementsOf(STANDARD);
  }

  @Test
  void selectedPresetGatesComeFirstInPresetOrder() {
    assertThat(PresetGateOrder.orderSelected(STANDARD, List.of("trivy", "pmd", "tests")))
        .containsExactly("tests", "pmd", "trivy");
  }

  @Test
  void unselectedPresetGatesAreNotAdded() {
    assertThat(PresetGateOrder.orderSelected(STANDARD, List.of("compile")))
        .containsExactly("compile");
  }
}
