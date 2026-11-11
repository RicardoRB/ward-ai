package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Pylint, with every extension loaded, on the fixtures of each implemented Python row
 * that has a Pylint counterpart, and compares what it reports with the committed report. The report
 * lists every row where Pylint's lines differ from the fixture's {@code expected.txt}, with the
 * kind of difference. Skipped unless {@code -Dward.pylint.binary} names a Pylint executable;
 * regenerate the report with {@code -Dward.parity.write=true}.
 */
final class PylintParitySmokeTest {

  private static final String PYLINT_PROPERTY = "ward.pylint.binary";
  private static final String WRITE_PROPERTY = "ward.parity.write";
  private static final String REPORT_FILE = "src/test/resources/parity/pylint-report.txt";
  private static final Pattern ROW = Pattern.compile(":(\\d+): \\[");
  private static final int LINE_GROUP = 1;
  private static final String EXTENSIONS =
      "pylint.extensions.bad_builtin,pylint.extensions.broad_try_clause,"
          + "pylint.extensions.check_elif,pylint.extensions.code_style,"
          + "pylint.extensions.comparison_placement,pylint.extensions.confusing_elif,"
          + "pylint.extensions.consider_refactoring_into_while_condition,"
          + "pylint.extensions.consider_ternary_expression,pylint.extensions.dict_init_mutate,"
          + "pylint.extensions.docparams,pylint.extensions.docstyle,pylint.extensions.dunder,"
          + "pylint.extensions.empty_comment,pylint.extensions.eq_without_hash,"
          + "pylint.extensions.for_any_all,pylint.extensions.magic_value,"
          + "pylint.extensions.mccabe,pylint.extensions.no_self_use,"
          + "pylint.extensions.overlapping_exceptions,pylint.extensions.private_import,"
          + "pylint.extensions.redefined_loop_name,pylint.extensions.redefined_variable_type,"
          + "pylint.extensions.set_membership,pylint.extensions.typing,"
          + "pylint.extensions.while_used";

  /** Whether the opt-in property names a Pylint binary. */
  static boolean pylintConfigured() {
    return !System.getProperty(PYLINT_PROPERTY, "").isBlank();
  }

  @Test
  @EnabledIf("pylintConfigured")
  void theParityReportMatchesTheCommittedOne() throws IOException {
    final String pylint = System.getProperty(PYLINT_PROPERTY);
    final Path root = RuleFixtureSet.root().resolve("python");
    final List<String> lines =
        new RuleCatalogReader()
            .load().rows().parallelStream()
                .filter(
                    row ->
                        row.languages(CatalogStatus.IMPLEMENTED).contains(AnalysisLanguage.PYTHON))
                .filter(row -> Files.exists(root.resolve(row.name()).resolve("violating.py")))
                .flatMap(row -> differences(pylint, row, root.resolve(row.name())))
                .sorted()
                .toList();
    final String report =
        "# row, pylint id, difference: tool-silent (Pylint reports nothing on the violating"
            + " fixture), anchor-differs (same number of findings on other lines),"
            + " count-differs, tool-extra (Pylint reports the passing fixture).\n"
            + "# Regenerate with -Dward.parity.write=true.\n"
            + String.join("\n", lines)
            + "\n";
    if (Boolean.getBoolean(WRITE_PROPERTY)) {
      Files.writeString(Path.of(REPORT_FILE), report, StandardCharsets.UTF_8);
    }
    assertThat(report).isEqualTo(committed());
  }

  private static String committed() throws IOException {
    return Files.readString(Path.of(REPORT_FILE), StandardCharsets.UTF_8);
  }

  private static Stream<String> differences(
      final String pylint, final CatalogRow row, final Path directory) {
    return row.counterparts().getOrDefault("pylint", List.of()).stream()
        .map(
            id ->
                difference(pylint, id, directory).map(kind -> row.name() + "\t" + id + "\t" + kind))
        .flatMap(Optional::stream);
  }

  private static Optional<String> difference(
      final String pylint, final String id, final Path directory) {
    try {
      final List<String> kinds = kinds(pylint, id, directory);
      return kinds.isEmpty() ? Optional.empty() : Optional.of(String.join(",", kinds));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static List<String> kinds(final String pylint, final String id, final Path directory)
      throws IOException, InterruptedException {
    final List<Integer> expected = expectedLines(directory.resolve("expected.txt"));
    final List<Integer> violating = reportedLines(pylint, id, directory.resolve("violating.py"));
    final List<String> kinds = new ArrayList<>();
    if (violating.isEmpty()) {
      kinds.add("tool-silent");
    } else if (!violating.equals(expected)) {
      kinds.add(violating.size() == expected.size() ? "anchor-differs" : "count-differs");
    }
    if (!reportedLines(pylint, id, directory.resolve("passing.py")).isEmpty()) {
      kinds.add("tool-extra");
    }
    return kinds;
  }

  private static List<Integer> expectedLines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .map(line -> Integer.parseInt(line.substring(0, line.indexOf(':'))))
        .toList();
  }

  private static List<Integer> reportedLines(final String pylint, final String id, final Path file)
      throws IOException, InterruptedException {
    final Process process =
        new ProcessBuilder(
                pylint,
                "--rcfile=/dev/null",
                "--load-plugins=" + EXTENSIONS,
                "--disable=all",
                "--enable=" + id,
                "--output-format=parseable",
                "--score=n",
                "--persistent=n",
                file.toString())
            .redirectErrorStream(false)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    process.waitFor();
    final List<Integer> lines = new ArrayList<>();
    final Matcher matcher = ROW.matcher(output);
    while (matcher.find()) {
      lines.add(Integer.parseInt(matcher.group(LINE_GROUP)));
    }
    lines.sort(Comparator.naturalOrder());
    return lines;
  }
}
