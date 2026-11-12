package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.process.Command;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Shared helpers of the opt-in parity tests. */
final class ParitySupport {
  static final JsonCodec CODEC = new ForyJsonCodec();
  static final ProcessCommandExecutor PROCESSES = new ProcessCommandExecutor();
  private static final String PROPERTY = "ward.dependency.parity.";

  private ParitySupport() {}

  /** Skips the calling test unless {@code -Dward.dependency.parity.<tool>=true} is set. */
  static void enabled(final String tool) {
    assumeTrue(Boolean.getBoolean(PROPERTY + tool), "set -D" + PROPERTY + tool + "=true");
  }

  static String run(final Path dir, final String... arguments) {
    final CommandResult result = PROCESSES.execute(new Command(List.of(arguments), dir));
    assertThat(result.succeeded())
        .as(String.join(" ", arguments) + ": " + result.stderr())
        .isTrue();
    return result.stdout();
  }

  static Path copy(final String fixture, final Path work) throws IOException {
    InventoryFixtures.copy(InventoryFixtures.fixture(fixture).toAbsolutePath(), work);
    return work;
  }

  static Set<String> inventorySet(final DependencyInventory inventory, final Ecosystem ecosystem) {
    return inventory.distinct().stream()
        .filter(id -> id.ecosystem() == ecosystem)
        .map(id -> id.name() + "@" + id.version())
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
