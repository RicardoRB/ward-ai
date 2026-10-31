package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@code ward init} and then {@code ward check --profile ci} on the offline Swift fixtures in
 * child JVMs, with {@code swift}, {@code xcodebuild} and {@code xcrun} stubbed on the PATH. The
 * stubs prove what Ward runs and reads, not that the Swift toolchain itself passes.
 */
class SwiftCiIntegrationTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final long TIMEOUT_SECONDS = 300;

  /** Where {@code swift package resolve} leaves SwiftLint; the gate runs it from there. */
  private static final String SWIFTLINT_BINARY =
      ".ward/swift-tools/.build/artifacts/swiftlintplugins/SwiftLintBinary/"
          + "SwiftLintBinary.artifactbundle/macos/swiftlint";

  private static final String SWIFT_STUB =
      "#!/bin/sh\n"
          + "case \"$*\" in\n"
          + "  *--show-codecov-path*) echo .build/codecov/shop.json ;;\n"
          + "esac\n"
          + "exit 0\n";
  private static final String XCODEBUILD_STUB =
      "#!/bin/sh\n"
          + "case \"$*\" in\n"
          + "  -version) echo 'Xcode 26.6' ;;\n"
          + "  *-list*) echo '{\"project\": {\"name\": \"Shop\", \"schemes\": [\"Shop\"]}}' ;;\n"
          + "esac\n"
          + "exit 0\n";
  private static final String XCRUN_STUB =
      "#!/bin/sh\n"
          + "case \"$*\" in\n"
          + "  *\"xccov view\"*) cat \"$WARD_STUB_DIR/xccov.json\" ;;\n"
          + "  *\"simctl list runtimes\"*) echo '{\"runtimes\": [{\"identifier\": "
          + "\"com.apple.CoreSimulator.SimRuntime.iOS-26-6\", \"name\": \"iOS 26.6\", "
          + "\"platform\": \"iOS\", \"version\": \"26.6\", \"isAvailable\": true}]}' ;;\n"
          + "  *\"simctl list devices\"*) echo '{\"devices\": {\"com.apple.CoreSimulator."
          + "SimRuntime.iOS-26-6\": [{\"udid\": \"0000-1\", \"name\": \"iPhone 17\", "
          + "\"state\": \"Shutdown\", \"isAvailable\": true, \"deviceTypeIdentifier\": \"com.apple.CoreSimulator.SimDeviceType.iPhone-17\"}]}}' ;;\n"
          + "esac\n"
          + "exit 0\n";

  @TempDir Path root;

  @Test
  void swiftPackageInitializesAndPassesTheCiProfile() throws Exception {
    final Path project = project("swift-package", "llvm-cov-export-full-coverage.json");
    stubs(project, "swift", SWIFT_STUB);

    assertThat(run(project, "init", "--preset", "standard", "--yes").exitCode()).isZero();
    final Result check = run(project, "check", "--profile", "ci");

    assertThat(check.output()).contains("Quality Gate: PASSED");
    assertThat(check.exitCode()).isZero();
  }

  @Test
  void xcodeAppInitializesAndPassesTheCiProfile() throws Exception {
    final Path project = project("swift-xcode-app", "xccov-report-full-coverage.json");
    stubs(project, "xcodebuild", XCODEBUILD_STUB);
    Files.writeString(project.resolve("bin/xcrun"), XCRUN_STUB);
    Files.writeString(project.resolve("bin/swift"), "#!/bin/sh\nexit 0\n");
    project.resolve("bin/xcrun").toFile().setExecutable(true);
    project.resolve("bin/swift").toFile().setExecutable(true);

    assertThat(run(project, "init", "--preset", "standard", "--yes").exitCode()).isZero();
    final Result check = run(project, "check", "--profile", "ci");

    assertThat(check.output()).contains("Quality Gate: PASSED");
    assertThat(check.exitCode()).isZero();
  }

  /**
   * Copies a fixture project into the temporary root with a git directory and a coverage export.
   */
  private Path project(final String fixture, final String coverage) throws IOException {
    final Path project = Files.createDirectories(root.resolve("project"));
    try (java.util.stream.Stream<Path> files = Files.walk(FIXTURES.resolve(fixture))) {
      for (final Path file : files.toList()) {
        final Path target = project.resolve(FIXTURES.resolve(fixture).relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
    Files.createDirectories(project.resolve(".git/hooks"));
    Files.createDirectories(project.resolve(".build/codecov"));
    final Path swiftlint =
        Files.createDirectories(project.resolve(SWIFTLINT_BINARY).getParent()).resolve("swiftlint");
    Files.writeString(swiftlint, "#!/bin/sh\necho '[]'\nexit 0\n");
    swiftlint.toFile().setExecutable(true);
    Files.copy(
        FIXTURES.resolve("swift").resolve(coverage), project.resolve(".build/codecov/shop.json"));
    Files.createDirectories(root.resolve("stubs"));
    Files.copy(FIXTURES.resolve("swift").resolve(coverage), root.resolve("stubs/xccov.json"));
    return project;
  }

  private void stubs(final Path project, final String name, final String script)
      throws IOException {
    final Path bin = Files.createDirectories(project.resolve("bin"));
    final Path swift = bin.resolve("swift");
    Files.writeString(swift, "swift".equals(name) ? script : SWIFT_STUB);
    swift.toFile().setExecutable(true);
    if (!"swift".equals(name)) {
      final Path tool = bin.resolve(name);
      Files.writeString(tool, script);
      tool.toFile().setExecutable(true);
    }
  }

  private Result run(final Path project, final String... args)
      throws IOException, InterruptedException {
    final List<String> command = new java.util.ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add("dev.ward.WardApplication");
    command.addAll(List.of(args));
    final ProcessBuilder builder =
        new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
    final Map<String, String> environment = builder.environment();
    environment.put(
        "PATH", project.resolve("bin") + java.io.File.pathSeparator + environment.get("PATH"));
    environment.put("WARD_STUB_DIR", root.resolve("stubs").toString());
    final Process process = builder.start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    return new Result(process.exitValue(), output);
  }

  private record Result(int exitCode, String output) {}
}
