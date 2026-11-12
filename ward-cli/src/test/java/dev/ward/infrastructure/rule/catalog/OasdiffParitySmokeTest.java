package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real oasdiff on the base and revision of every breaking-change fixture and compares its
 * error-level results (check id, method, path) with what all Ward rules report on the same pair.
 * Every difference must be listed with its reason in {@code parity/oasdiff-approximations.txt}, and
 * every listed difference must still occur. Skipped unless {@code -Dward.parity.oasdiff} names the
 * oasdiff executable; the pinned version is 1.37.0.
 */
final class OasdiffParitySmokeTest {

  private static final String PROPERTY = "ward.parity.oasdiff";
  private static final String APPROXIMATIONS =
      "src/test/resources/parity/oasdiff-approximations.txt";
  private static final int ERROR_LEVEL = 3;
  private static final String OASDIFF_ONLY = "oasdiff-only";
  private static final String WARD_ONLY = "ward-only";

  /** Whether the opt-in property names an oasdiff executable. */
  static boolean oasdiffConfigured() {
    return !System.getProperty(PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("oasdiffConfigured")
  void everyDifferenceFromOasdiffIsListedWithItsReason() throws IOException {
    final String oasdiff = System.getProperty(PROPERTY);
    final Set<String> differences = new TreeSet<>();
    try (Stream<Path> directories = Files.list(BreakingFixtures.root())) {
      for (final Path directory : directories.sorted().toList()) {
        differences.addAll(differences(oasdiff, directory));
      }
    }

    final Set<String> listed = approximations();
    assertThat(differences)
        .as("differences not listed in oasdiff-approximations.txt")
        .isSubsetOf(listed);
    assertThat(listed).as("listed differences that no longer occur").isSubsetOf(differences);
  }

  private static Set<String> differences(final String oasdiff, final Path directory)
      throws IOException {
    final String name = directory.getFileName().toString();
    final Set<String> theirs = new TreeSet<>(oasdiffResults(oasdiff, directory));
    final Set<String> ours = new TreeSet<>();
    BreakingFixtures.findings(directory, "revision.yaml", "")
        .forEach(finding -> ours.add(BreakingFixtures.operation(finding)));
    final Set<String> differences = new TreeSet<>();
    theirs.stream()
        .filter(result -> !ours.contains(result))
        .forEach(result -> differences.add(name + "\t" + OASDIFF_ONLY + "\t" + result));
    ours.stream()
        .filter(result -> !theirs.contains(result))
        .forEach(result -> differences.add(name + "\t" + WARD_ONLY + "\t" + result));
    return differences;
  }

  /** The error-level results as {@code id, method, path}, as {@link BreakingFixtures#operation}. */
  private static List<String> oasdiffResults(final String oasdiff, final Path directory)
      throws IOException {
    final Process process =
        new ProcessBuilder(
                oasdiff, "breaking", "-f", "json", "--fail-on", "ERR", "base.yaml", "revision.yaml")
            .directory(directory.toFile())
            .redirectErrorStream(false)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try {
      process.waitFor();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
    final List<String> results = new ArrayList<>();
    if (output.isBlank()) {
      return results;
    }
    final JsonTree changes = JsonTree.parse(output);
    for (int index = 0; index < changes.size(); index++) {
      final JsonTree change = changes.get(index);
      if (change.get("level").asInt() == ERROR_LEVEL) {
        results.add(
            change.get("id").asString()
                + "\t"
                + text(change, "operation")
                + "\t"
                + text(change, "path"));
      }
    }
    return results;
  }

  private static String text(final JsonTree change, final String field) {
    return change.has(field) ? change.get(field).asString() : "";
  }

  /** The listed differences without their reason: fixture, kind, id, method and path. */
  private static Set<String> approximations() throws IOException {
    final Set<String> listed = new TreeSet<>();
    for (final String line : Files.readAllLines(Path.of(APPROXIMATIONS), StandardCharsets.UTF_8)) {
      if (!line.isBlank() && !line.startsWith("#")) {
        final String[] columns = line.split("\t", -1);
        listed.add(String.join("\t", List.of(columns).subList(0, columns.length - 1)));
      }
    }
    return listed;
  }
}
