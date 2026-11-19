package dev.ward.infrastructure.dependency.cargo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.PackageId;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.project.rust.CargoWorkspaces;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CargoDependencySourceTest {
  private static DependencyInventory read(final Path dir) {
    return new CargoDependencySource().read(CargoWorkspaces.project(dir.toAbsolutePath()));
  }

  @DisplayName("the inventory equals the package manager")
  @ParameterizedTest
  @ValueSource(strings = {"cargo", "cargo-workspace"})
  void theInventoryEqualsThePackageManager(final String fixture) throws IOException {
    final Path dir = InventoryFixtures.fixture(fixture);

    final DependencyInventory inventory = read(dir);

    final Set<String> listed =
        new TreeSet<>(Files.readAllLines(dir.resolve("cargo-metadata.txt")));
    final Set<String> actual =
        inventory.distinct().stream()
            .peek(id -> assertThat(id.ecosystem()).isEqualTo(Ecosystem.CRATES_IO))
            .map(id -> id.name() + "@" + id.version())
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(actual).isEqualTo(listed);
    InventoryFixtures.assertMatches(inventory, dir, "expected.txt");
  }

  @Test
  void aProjectWithoutALockfileNamesTheCommandThatCreatesIt(@TempDir final Path dir)
      throws IOException {
    Files.writeString(
        dir.resolve("Cargo.toml"),
        "[package]\nname='a'\nversion='0.1.0'\n[dependencies]\nitoa='1'\n"
    );

    assertThatThrownBy(() -> read(dir))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("cargo generate-lockfile");
  }

  @Test
  void aGitDependencyIsUnreleased(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("Cargo.toml"),
        "[package]\nname='a'\nversion='0.1.0'\n[dependencies]\nfoo={git='https://example.test/foo'}\n"
    );
    Files.writeString(
        dir.resolve("Cargo.lock"),
        "version = 4\n[[package]]\nname = \"a\"\nversion = \"0.1.0\"\ndependencies = [\"foo\"]\n"
            + "[[package]]\nname = \"foo\"\nversion = \"0.2.0\"\n"
            + "source = \"git+https://example.test/foo#abc\"\n"
    );

    assertThat(read(dir).modules().getFirst().dependencies())
        .singleElement()
        .satisfies(entry -> assertThat(entry.released()).isFalse())
        .extracting(Dependency::id)
        .isEqualTo(new PackageId(Ecosystem.CRATES_IO, "foo", "0.2.0"));
  }

  @Test
  void aTruncatedLockfileNamesTheFile(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("Cargo.toml"),
        "[package]\nname='a'\nversion='0.1.0'\n[dependencies]\nitoa='1'\n"
    );
    Files.writeString(dir.resolve("Cargo.lock"), "version = 4\n[[package\n");

    assertThatThrownBy(() -> read(dir)).hasMessageContaining("Cargo.lock");
  }

  @Test
  void twoVersionsOfOneCrateAreTwoEntries(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("Cargo.toml"),
        "[package]\nname='a'\nversion='0.1.0'\n[dependencies]\nrand='0.8'\n"
    );
    Files.writeString(
        dir.resolve("Cargo.lock"),
        "version = 4\n[[package]]\nname = \"a\"\nversion = \"0.1.0\"\n"
            + "dependencies = [\"rand 0.8.5\", \"rand 0.7.3\"]\n"
            + "[[package]]\nname = \"rand\"\nversion = \"0.8.5\"\nsource = \"registry+x\"\n"
            + "[[package]]\nname = \"rand\"\nversion = \"0.7.3\"\nsource = \"registry+x\"\n"
    );

    final List<String> names =
        read(dir).distinct().stream().map(id -> id.name() + "@" + id.version()).toList();

    assertThat(names).containsExactlyInAnyOrder("rand@0.8.5", "rand@0.7.3");
  }
}
