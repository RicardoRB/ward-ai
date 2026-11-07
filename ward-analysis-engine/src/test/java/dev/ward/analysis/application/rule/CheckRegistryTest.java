package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckRegistryTest {

  private static final CheckFactory FACTORY = toml -> root -> List.of();

  @Test
  void findsARegisteredCheckAndListsItsName() {
    final CheckRegistry registry = new CheckRegistry(List.of(() -> Map.of("one", FACTORY)));

    assertThat(registry.find("one")).containsSame(FACTORY);
    assertThat(registry.find("two")).isEmpty();
    assertThat(registry.names()).containsExactly("one");
  }

  @Test
  void twoProvidersRegisteringTheSameNameFail() {
    final CheckProvider first = () -> Map.of("same", FACTORY);
    final CheckProvider second = () -> Map.of("same", FACTORY);

    assertThatThrownBy(() -> new CheckRegistry(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'same'");
  }

  @Test
  void unionRejectsARepeatedName() {
    final Map<String, CheckFactory> part = Map.of("same", FACTORY);

    assertThatThrownBy(() -> CheckRegistry.union(List.of(part, part)))
        .isInstanceOf(IllegalStateException.class);
  }
}
