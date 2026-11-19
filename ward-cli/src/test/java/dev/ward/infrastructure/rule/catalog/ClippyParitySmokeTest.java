package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Clippy on the fixtures of each implemented Rust row that has a Clippy counterpart,
 * with only that lint on, and compares the lines it reports with the fixture's {@code
 * expected.txt}. The committed report lists every row where they differ, and each difference must
 * have its reason in {@code clippy-approximations.txt}. Skipped unless {@code
 * -Dward.parity.clippy} names the {@code clippy-driver} executable; regenerate the report with
 * {@code -Dward.parity.write=true}.
 */
final class ClippyParitySmokeTest {
  private static final String CLIPPY_PROPERTY = "ward.parity.clippy";
  private static final String WRITE_PROPERTY = "ward.parity.write";
  private static final String REPORT_FILE = "src/test/resources/parity/clippy-report.txt";
  private static final String REASONS_FILE =
      "src/test/resources/parity/clippy-approximations.txt";
  private static final String TAB = "\t";

  /** Whether the opt-in property names a clippy-driver binary. */
  static boolean clippyConfigured() {
    return !System.getProperty(CLIPPY_PROPERTY, "").isBlank();
  }

  @Test
  @DisplayName("parity with Clippy")
  @EnabledIf("clippyConfigured")
  void theParityReportMatchesTheCommittedOneAndEveryDifferenceHasAReason() throws IOException {
    final String clippy = System.getProperty(CLIPPY_PROPERTY);
    final Path root = RuleFixtureSet.root().resolve("rust");
    final List<String> lines =
        new RuleCatalogReader()
            .load().rows().parallelStream()
            .filter(row -> row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.RUST))
            .flatMap(row -> differences(clippy, row, root.resolve(directoryName(row))))
            .sorted()
            .toList();
    final String report =
        "# rule, clippy lint, difference: tool-silent (Clippy reports nothing on the violating"
            + " fixture), anchor-differs (same number of findings on other lines),"
            + " count-differs, tool-extra (Clippy reports the passing fixture).\n"
            + "# Regenerate with -Dward.parity.write=true; reasons are in clippy-approximations.txt.\n"
            + String.join("\n", lines)
            + "\n";
    if (Boolean.getBoolean(WRITE_PROPERTY)) {
      Files.writeString(Path.of(REPORT_FILE), report, StandardCharsets.UTF_8);
    }
    assertThat(report).isEqualTo(Files.readString(Path.of(REPORT_FILE), StandardCharsets.UTF_8));
    assertThat(withoutReason(lines)).isEmpty();
  }

  private static String directoryName(final CatalogRow row) {
    final String id = row.idFor(AnalysisLanguage.RUST);
    return id.substring(id.indexOf('/') + 1);
  }

  private static List<String> withoutReason(final List<String> lines) throws IOException {
    final Map<String, String> reasons = new HashMap<>();
    for (final String line : Files.readAllLines(Path.of(REASONS_FILE), StandardCharsets.UTF_8)) {
      if (!line.startsWith("#") && !line.isBlank()) {
        final String[] parts = line.split(TAB, 3);
        reasons.put(parts[0] + TAB + parts[1], parts[2]);
      }
    }
    return lines.stream()
        .filter(line -> !reasons.containsKey(line.substring(0, line.lastIndexOf(TAB))))
        .toList();
  }

  private static Stream<String> differences(
      final String clippy,
      final CatalogRow row,
      final Path directory
  ) {
    return row.counterparts().getOrDefault("clippy", List.of()).stream()
        .map(
            id ->
                difference(clippy, id, directory)
                    .map(kind -> directory.getFileName() + TAB + id + TAB + kind)
        )
        .flatMap(Optional::stream);
  }

  private static Optional<String> difference(
      final String clippy,
      final String id,
      final Path directory
  ) {
    try {
      final List<String> kinds = kinds(clippy, id, directory);
      return kinds.isEmpty() ? Optional.empty() : Optional.of(String.join(",", kinds));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static List<String> kinds(final String clippy, final String id, final Path directory)
      throws IOException, InterruptedException {
    final List<Integer> expected = expectedLines(directory.resolve("expected.txt"));
    final List<Integer> violating = ClippyRun.reportedLines(clippy, id, directory.resolve("violating.rs"));
    final List<String> kinds = new ArrayList<>();
    if (violating.isEmpty()) {
      kinds.add("tool-silent");
    } else if (!violating.equals(expected)) {
      kinds.add(violating.size() == expected.size() ? "anchor-differs" : "count-differs");
    }
    if (!ClippyRun.reportedLines(clippy, id, directory.resolve("passing.rs")).isEmpty()) {
      kinds.add("tool-extra");
    }
    return kinds;
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .distinct()
        .toList();
  }
}
