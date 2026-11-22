package dev.ward.domain.apicompat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real cargo-semver-checks on the baseline and the revision of every {@code
 * api-compat/rust} fixture and compares whether it finds a breaking change with whether the native
 * {@code api-compat} check reports one. Each fixture crate is a directory with {@code src/lib.rs}; a
 * manifest is written for it. A difference must be listed with its reason in {@code
 * parity/cargo-semver-checks-report.txt}, and a listed one must still occur. Skipped unless {@code
 * -Dward.parity.cargo-semver-checks} names the {@code cargo-semver-checks} executable, which needs
 * {@code cargo} on the path and rustdoc JSON support (nightly or Rust 1.86+ stable).
 */
@EnabledIfSystemProperty(named = "ward.parity.cargo-semver-checks", matches = ".+")
class CargoSemverChecksParitySmokeTest {
  private static final Path ROOT = Path.of("src/test/resources/api-compat/rust");
  private static final Path REPORT =
      Path.of("src/test/resources/parity/cargo-semver-checks-report.txt");
  private static final String MANIFEST =
      "[package]\nname = \"fixture\"\nversion = \"0.1.0\"\nedition = \"2021\"\n";
  private static final String TOOL_ONLY = "tool-only";
  private static final String WARD_ONLY = "ward-only";
  private static final long MINUTES = 10;
  private static final int BREAKING = 1;
  private static final int COMPATIBLE = 0;

  @TempDir Path work;

  @Test
  @DisplayName("parity with cargo-semver-checks")
  void everyDifferenceFromTheToolIsListedWithItsReason() throws IOException, InterruptedException {
    final Set<String> differences = new TreeSet<>();
    final List<Path> fixtures;
    try (Stream<Path> directories = Files.list(ROOT)) {
      fixtures = directories.filter(Files::isDirectory).sorted().toList();
    }
    for (final Path fixture : fixtures) {
      final boolean ward = !ApiCompatFixturesTest.compare(fixture).isEmpty();
      final boolean tool = toolFindsBreak(fixture);
      if (ward != tool) {
        differences.add(fixture.getFileName() + "\t" + (tool ? TOOL_ONLY : WARD_ONLY));
      }
    }
    final Set<String> listed = listed();

    assertThat(differences).as(
        "differences not listed in cargo-semver-checks-report.txt"
    ).isSubsetOf(listed);
    assertThat(listed).as("listed differences that no longer occur").isSubsetOf(differences);
  }

  private boolean toolFindsBreak(final Path fixture) throws IOException, InterruptedException {
    boolean broken = false;
    for (final Path crate : crates(fixture.resolve("baseline"))) {
      final Path relative = fixture.resolve("baseline").relativize(crate);
      final Path base = copy(fixture.resolve("baseline"), relative, "baseline");
      final Path revision = copy(fixture.resolve("revision"), relative, "revision");
      broken |= run(fixture, base, revision) == BREAKING;
    }
    return broken;
  }

  private List<Path> crates(final Path side) throws IOException {
    try (Stream<Path> files = Files.walk(side)) {
      return files
          .filter(path -> path.endsWith("src/lib.rs"))
          .map(path -> path.getParent().getParent())
          .toList();
    }
  }

  private Path copy(final Path side, final Path relative, final String label) throws IOException {
    final Path target = work.resolve(
        label + "-" + side.getParent().getFileName() + "-" + relative.toString().replace('/', '_')
    );
    final Path source = side.resolve(relative);
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final Path to = target.resolve(source.relativize(file));
        Files.createDirectories(to.getParent());
        Files.copy(file, to);
      }
    }
    Files.writeString(target.resolve("Cargo.toml"), MANIFEST, StandardCharsets.UTF_8);
    return target;
  }

  private int run(final Path fixture, final Path base, final Path revision)
      throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>();
    command.add(System.getProperty("ward.parity.cargo-semver-checks"));
    command.add("semver-checks");
    command.add("--manifest-path");
    command.add(revision.resolve("Cargo.toml").toString());
    command.add("--baseline-root");
    command.add(base.toString());
    final Process process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(work.resolve(fixture.getFileName() + ".log").toFile())
            .start();
    assertThat(process.waitFor(MINUTES, TimeUnit.MINUTES)).as(
        "cargo-semver-checks finished"
    ).isTrue();
    assertThat(process.exitValue()).as("tool ran on " + fixture.getFileName()).isIn(
        COMPATIBLE,
        BREAKING
    );
    return process.exitValue();
  }

  private static Set<String> listed() throws IOException {
    final Set<String> found = new TreeSet<>();
    for (final String line : Files.readAllLines(REPORT)) {
      if (!line.isBlank() && !line.startsWith("#")) {
        final String[] columns = line.split("\t");
        found.add(columns[0] + "\t" + columns[1]);
      }
    }
    return found;
  }
}
