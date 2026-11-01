package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.SwiftPorts;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.SwiftToolchainInspection;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.swift.SwiftBuildToolConfigurator;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.project.FileSystemSwiftSourceDirectories;
import dev.ward.infrastructure.project.FileSystemSwiftToolConfigs;
import dev.ward.infrastructure.project.XcodebuildSchemes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@code ward init} on the offline SwiftPM fixture. The resolve is stubbed, so the test proves
 * what init writes, not that SwiftPM can fetch SwiftLint.
 */
class SwiftInitCliTest {
  @TempDir Path root;

  @Test
  void swiftPackageGetsTheStandardGatesAndWardOwnedFilesAndKeepsItsOwnPackage() throws IOException {
    copyFixture("swift-package", root);
    final String packageBefore = Files.readString(root.resolve("Package.swift"));
    final FakeCommandExecutor resolve =
        new FakeCommandExecutor(new CommandResult(0, "", "", Duration.ZERO));
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new SwiftBuildToolConfigurator(resolve, mac()), "swift")
            .withSwiftPorts(
                new SwiftPorts(
                    new XcodebuildSchemes(new FakeCommandExecutor(), new ForyJsonCodec()),
                    new FileSystemSwiftToolConfigs(),
                    new FileSystemSwiftSourceDirectories()));

    final int exit = init.run("", "--preset", "standard", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml)
        .contains("swift-format", "swiftlint", "swift-build", "swift-test", "swift-coverage")
        .doesNotContain("xcodebuild-test");
    assertThat(root.resolve(".ward/swift-tools/Package.swift")).exists();
    assertThat(root.resolve(".swiftlint.yml")).exists();
    assertThat(root.resolve(".swift-format")).exists();
    assertThat(Files.readString(root.resolve("Package.swift"))).isEqualTo(packageBefore);
    assertThat(init.output()).contains(".ward/swift-tools/Package.swift").doesNotContain("pom.xml");
  }

  @Test
  void strictSwiftPackageGetsHarmonizeRulesForTheChosenStyle() throws IOException {
    copyFixture("swift-package", root);
    final InitCommandFixture init = swiftInit();

    final int exit = init.run("", "--preset", "strict", "--architecture", "hexagonal", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[checks.harmonize]", "uses = \"harmonize\"");
    assertThat(
            Files.readString(
                root.resolve(".ward/swift-architecture/Tests/ArchitectureRules/LayerRules.swift")))
        .contains("func domainLayerRespectsRules()", "func applicationLayerRespectsRules()");
    assertThat(root.resolve(".harmonize.yaml")).exists();
    assertThat(Files.readString(root.resolve(".gitignore")))
        .contains(".ward/swift-architecture/.build/");
  }

  @Test
  void standardSwiftPackageWithoutHarmonizeWritesNoArchitecturePackage() throws IOException {
    copyFixture("swift-package", root);
    final InitCommandFixture init = swiftInit();

    final int exit = init.run("", "--preset", "standard", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml"))).doesNotContain("harmonize");
    assertThat(root.resolve(".ward/swift-architecture")).doesNotExist();
    assertThat(root.resolve(".harmonize.yaml")).doesNotExist();
  }

  private InitCommandFixture swiftInit() throws IOException {
    final FakeCommandExecutor resolve =
        new FakeCommandExecutor(new CommandResult(0, "", "", Duration.ZERO));
    return InitCommandFixture.repository(
            root, new SwiftBuildToolConfigurator(resolve, mac()), "swift")
        .withSwiftPorts(
            new SwiftPorts(
                new XcodebuildSchemes(new FakeCommandExecutor(), new ForyJsonCodec()),
                new FileSystemSwiftToolConfigs(),
                new FileSystemSwiftSourceDirectories()));
  }

  @Test
  void xcodeAppGetsItsSchemeTestGateAndTheSchemeOptionForSeveralSchemes() throws IOException {
    copyFixture("swift-xcode-two-schemes", root);
    final FakeCommandExecutor resolve =
        new FakeCommandExecutor(new CommandResult(0, "", "", Duration.ZERO));
    final String listing = "{\"project\": {\"schemes\": [\"Shop\", \"ShopWidgets\"]}}";
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new SwiftBuildToolConfigurator(resolve, mac()), "swift")
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
        .doesNotContain("swift-build");
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
