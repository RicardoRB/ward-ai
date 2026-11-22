package dev.ward.infrastructure.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.dependency.Ecosystem;
import dev.ward.infrastructure.dependency.cargo.CargoDependencySource;
import dev.ward.infrastructure.project.rust.CargoWorkspaces;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Compares the Cargo inventory with {@code cargo metadata}. */
class DependencyCargoParitySmokeTest {
  @ParameterizedTest
  @ValueSource(strings = {"cargo", "cargo-workspace"})
  void cargoMatchesCargoMetadata(final String fixture, @TempDir final Path work)
      throws IOException {
    ParitySupport.enabled("cargo");
    final Path dir = ParitySupport.copy(fixture, work);
    final Set<String> listed = new TreeSet<>();
    final String json =
        ParitySupport.run(dir, "cargo", "metadata", "--format-version", "1", "--locked");
    final java.util.regex.Matcher pin =
        java.util.regex.Pattern.compile(
            "\\{\"name\":\"([^\"]+)\",\"version\":\"([^\"]+)\",\"id\":\"registry"
        )
            .matcher(json);
    while (pin.find()) {
      listed.add(pin.group(1) + "@" + pin.group(2));
    }

    assertThat(
        ParitySupport.inventorySet(
            new CargoDependencySource().read(CargoWorkspaces.project(dir)),
            Ecosystem.CRATES_IO
        )
    )
        .isEqualTo(listed);
  }
}
