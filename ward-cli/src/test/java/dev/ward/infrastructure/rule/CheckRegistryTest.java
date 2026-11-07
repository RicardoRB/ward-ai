package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckRegistryTest {

  private static final CheckFactory FACTORY = toml -> root -> List.of();

  @Test
  void bundledProvidersRegisterEveryCheckNameOnce() {
    assertThat(BundledLanguages.checkRegistry().names()).contains("lines", "static-import");
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
