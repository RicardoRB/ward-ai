package dev.ward.infrastructure.dependency.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SwiftDependencySourceTest {
  private static final String PACKAGE_SWIFT_LOG =
      "let p = Package(dependencies: [.package(url: \"https://github.com/apple/swift-log.git\", from: \"1.0.0\")])";

  private static DependencyInventory read(final Path dir, final BuildTool tool) {
    return new SwiftDependencySource(new ForyJsonCodec())
        .read(new Project(dir.toAbsolutePath(), Language.SWIFT, tool, Framework.NONE));
  }

  @ParameterizedTest
  @CsvSource({"swiftpm,SWIFTPM", "xcode,XCODE"})
  void everySourceMatchesItsGoldenInventory(final String fixture, final BuildTool tool) {
    final Path dir = InventoryFixtures.fixture(fixture);

    InventoryFixtures.assertMatches(read(dir, tool), dir, "expected.txt");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "https://github.com/apple/swift-nio.git|github.com/apple/swift-nio",
        "https://user@GitHub.com/Apple/Swift-NIO.git/|github.com/apple/swift-nio",
        "git@github.com:apple/swift-nio.git|github.com/apple/swift-nio",
        "ssh://git@github.com/apple/swift-nio|github.com/apple/swift-nio",
        "https://github.com/vapor/vapor|github.com/vapor/vapor"
      })
  void locationsAreReducedToTheOsvName(final String location, final String name) {
    assertThat(SwiftLocations.normalize(location)).isEqualTo(name);
  }

  @Test
  void aVersionOneFileIsReadByRepositoryUrl(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("Package.swift"), PACKAGE_SWIFT_LOG);
    Files.writeString(
        dir.resolve("Package.resolved"),
        "{\"object\":{\"pins\":[{\"package\":\"swift-log\",\"repositoryURL\":"
            + "\"https://github.com/apple/swift-log.git\",\"state\":{\"branch\":null,"
            + "\"revision\":\"r\",\"version\":\"1.6.1\"}}]},\"version\":1}");

    final Dependency entry =
        read(dir, BuildTool.SWIFTPM).modules().getFirst().dependencies().getFirst();

    assertThat(entry.id().name()).isEqualTo("github.com/apple/swift-log");
    assertThat(entry.id().version()).isEqualTo("1.6.1");
    assertThat(entry.released()).isTrue();
    assertThat(entry.direct()).isTrue();
  }

  @Test
  void aNonLiteralPackageArgumentIsAnError(@TempDir final Path dir) throws IOException {
    Files.writeString(
        dir.resolve("Package.swift"),
        "let p = Package(dependencies: [.package(url: base, from: \"1\")])");
    Files.writeString(dir.resolve("Package.resolved"), "{\"pins\":[],\"version\":2}");

    assertThatThrownBy(() -> read(dir, BuildTool.SWIFTPM))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("string literal");
  }

  @Test
  void aMissingResolvedFileNamesTheCommand(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("Package.swift"), PACKAGE_SWIFT_LOG);

    assertThatThrownBy(() -> read(dir, BuildTool.SWIFTPM))
        .hasMessageContaining("swift package resolve");
  }

  @Test
  void aDeclaredPackageWithoutAPinFails(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("Package.swift"), PACKAGE_SWIFT_LOG);
    Files.writeString(dir.resolve("Package.resolved"), "{\"pins\":[],\"version\":2}");

    assertThatThrownBy(() -> read(dir, BuildTool.SWIFTPM))
        .hasMessageContaining("github.com/apple/swift-log");
  }

  @Test
  void anUnknownVersionNamesTheFile(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("Package.swift"), PACKAGE_SWIFT_LOG);
    Files.writeString(dir.resolve("Package.resolved"), "{\"pins\":[],\"version\":9}");

    assertThatThrownBy(() -> read(dir, BuildTool.SWIFTPM))
        .hasMessageContaining("Package.resolved")
        .hasMessageContaining("version 9");
  }

  @Test
  void aProjectWithoutPackagesIsEmpty(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("Package.swift"), "let p = Package(name: \"A\")");

    assertThat(read(dir, BuildTool.SWIFTPM).entryCount()).isZero();
  }
}
