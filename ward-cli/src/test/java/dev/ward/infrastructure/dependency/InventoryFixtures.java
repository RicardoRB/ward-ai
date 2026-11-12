package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Renders inventories as golden text and compares them with the committed {@code expected.txt}. */
public final class InventoryFixtures {
  /** The folder with one project per lockfile format. */
  public static final Path ROOT = Path.of("src/test/resources/fixtures/dependency-inventory");

  private static final String WRITE = "ward.dependency.write";

  private InventoryFixtures() {}

  /** Returns the fixture project directory {@code name}. */
  public static Path fixture(final String name) {
    return ROOT.resolve(name);
  }

  /** Renders one line per entry: module, package, relation, scope, release, edges and files. */
  public static String render(final DependencyInventory inventory) {
    final List<String> lines = new ArrayList<>();
    for (final ModuleDependencies module : inventory.modules()) {
      for (final Dependency entry : module.dependencies()) {
        lines.add(
            "["
                + module.modulePath()
                + "] "
                + entry.id().ecosystem()
                + " "
                + entry.id().name()
                + " "
                + entry.id().version()
                + " "
                + entry.relation()
                + " "
                + entry.scope()
                + (entry.released() ? "" : " unreleased")
                + " from="
                + entry.source()
                + (entry.declaredIn().isEmpty() ? "" : " in=" + entry.declaredIn())
                + requiredBy(entry));
      }
      if (module.dependencies().isEmpty()) {
        lines.add("[" + module.modulePath() + "] (none)");
      }
    }
    return String.join("\n", lines) + "\n";
  }

  private static String requiredBy(final Dependency entry) {
    return entry.requiredBy().isEmpty()
        ? ""
        : " by="
            + entry.requiredBy().stream()
                .sorted()
                .map(PackageId::name)
                .collect(Collectors.joining(","));
  }

  /** Asserts that {@code inventory} equals {@code <fixture>/<file>}, or writes it on request. */
  public static void assertMatches(
      final DependencyInventory inventory, final Path fixture, final String file) {
    final Path expected = fixture.resolve(file);
    final String actual = render(inventory);
    try {
      if (Boolean.getBoolean(WRITE)) {
        Files.writeString(expected, actual);
      }
      assertThat(actual).isEqualTo(Files.readString(expected));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
