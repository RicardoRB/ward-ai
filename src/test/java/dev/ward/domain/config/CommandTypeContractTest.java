package dev.ward.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.QualityGateDefinitions;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Every built-in type is either inferable from a command or written with {@code uses}. */
class CommandTypeContractTest {

  @Test
  void everyRegistryTypeIsInferableOrUsesOnly() {
    final Set<String> covered = new HashSet<>(CommandTypeInference.inferable());
    covered.addAll(CommandTypeInference.USES_ONLY);
    final Set<String> registry =
        QualityGateDefinitions.all().stream()
            .map(QualityGateDefinitions.Definition::id)
            .collect(Collectors.toSet());

    assertThat(covered).containsAll(registry);
  }

  @Test
  void inferenceRowsNameOnlyRegistryTypes() {
    final Set<String> registry =
        QualityGateDefinitions.all().stream()
            .map(QualityGateDefinitions.Definition::id)
            .collect(Collectors.toSet());

    assertThat(registry).containsAll(CommandTypeInference.inferable());
  }
}
