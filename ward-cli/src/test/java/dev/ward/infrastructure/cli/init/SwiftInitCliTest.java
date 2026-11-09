package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.SwiftPorts;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.swift.SwiftToolchainInspection;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.swift.SwiftBuildToolConfigurator;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.project.swift.FileSystemSwiftSourceDirectories;
import dev.ward.infrastructure.project.swift.FileSystemSwiftToolConfigs;
import dev.ward.infrastructure.project.swift.XcodebuildSchemes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@code ward init} on the offline Swift fixtures, and checks what init writes. */
class SwiftInitCliTest {
  @TempDir Path root;

  @Test
  void swiftPackageGetsTheStandardGatesAndWardOwnedFilesAndKeepsItsOwnPackage() throws IOException {
    copyFixture("swift-package", root);
    final String packageBefore = Files.readString(root.resolve("Package.swift"));
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new SwiftBuildToolConfigurator(mac()), "swift")
            .withSwiftPorts(
                new SwiftPorts(
                    new XcodebuildSchemes(new FakeCommandExecutor(), new ForyJsonCodec()),
                    new FileSystemSwiftToolConfigs(),
                    new FileSystemSwiftSourceDirectories()));

    final int exit = init.run("", "--preset", "standard", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml)
        .contains("ward-format", "swift-build", "swift-test", "swift-coverage")
        .doesNotContain("swiftlint", "xcodebuild-test");
    assertThat(Files.readString(root.resolve("ward-rules.toml")))
        .contains("languages = [\"java\", \"swift\"]");
    assertThat(root.resolve(".ward/swift-tools")).doesNotExist();
    assertThat(root.resolve(".swiftlint.yml")).doesNotExist();
    assertThat(root.resolve(".swift-format")).doesNotExist();
    assertThat(Files.readString(root.resolve("Package.swift"))).isEqualTo(packageBefore);
    assertThat(init.output()).doesNotContain("SwiftLint", "pom.xml");
  }

  @Test
  void standardSwiftPackageWritesNoArchitecturePackage() throws IOException {
    copyFixture("swift-package", root);
    final InitCommandFixture init = swiftInit();

    final int exit = init.run("", "--preset", "standard", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml"))).doesNotContain("harmonize");
    assertThat(root.resolve(".ward/swift-architecture")).doesNotExist();
    assertThat(root.resolve(".harmonize.yaml")).doesNotExist();
  }

  @Test
  void strictSwiftPackageWritesTheLayerBoundariesAndNoExternalRules() throws IOException {
    copyFixture("swift-package", root);
    final InitCommandFixture init = swiftInit();

    final int exit = init.run("", "--preset", "strict", "--architecture", "hexagonal", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[[checks.ward.layers]]")
        .doesNotContain("harmonize");
    assertThat(root.resolve(".ward/swift-architecture")).doesNotExist();
  }

  private InitCommandFixture swiftInit() throws IOException {
    return InitCommandFixture.repository(root, new SwiftBuildToolConfigurator(mac()), "swift")
        .withSwiftPorts(
            new SwiftPorts(
                new XcodebuildSchemes(new FakeCommandExecutor(), new ForyJsonCodec()),
                new FileSystemSwiftToolConfigs(),
                new FileSystemSwiftSourceDirectories()));
  }

  @Test
  void xcodeAppGetsItsSchemeTestGateAndTheSchemeOptionForSeveralSchemes() throws IOException {
    copyFixture("swift-xcode-two-schemes", root);
    final String listing = "{\"project\": {\"schemes\": [\"Shop\", \"ShopWidgets\"]}}";
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new SwiftBuildToolConfigurator(mac()), "swift")
            .withSwiftPorts(
                new SwiftPorts(
                    new XcodebuildSchemes(
                        new FakeCommandExecutor(new CommandResult(0, listing, "", Duration.ZERO)),
                        new ForyJsonCodec()),
                    new FileSystemSwiftToolConfigs(),
                    new FileSystemSwiftSourceDirectories()));

    final int refused = init.run("", "--preset", "standard", "--yes");
    assertThat(refused).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("Shop, ShopWidgets").contains("--scheme");
    assertThat(root.resolve("ward.toml")).doesNotExist();

    final int exit = init.run("", "--preset", "standard", "--yes", "--scheme", "ShopWidgets");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("xcodebuild-test")
        .contains("ShopWidgets")
        .contains("iOS Simulator")
        .doesNotContain("swift-build", "swiftlint");
  }

  private static SwiftToolchainInspection mac() {
    return new SwiftToolchainInspection() {
      @Override
      public Optional<String> xcodeVersion() {
        return Optional.of("26.6");
      }

      @Override
      public boolean macOs() {
        return true;
      }
    };
  }

  private static void copyFixture(final String name, final Path destination) throws IOException {
    final Path source = Path.of("src/test/resources/fixtures").resolve(name);
    try (java.util.stream.Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path target = destination.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
  }
}
