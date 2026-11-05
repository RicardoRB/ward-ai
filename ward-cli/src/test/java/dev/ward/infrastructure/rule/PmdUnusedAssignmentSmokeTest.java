package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Runs a real PMD command-line tool with the {@code UnusedAssignment} rule over the data-flow
 * fixtures and compares its rows with the committed reference. Skipped unless {@code
 * -Dward.pmd.binary} names the {@code pmd} executable of the PMD version the reference was written
 * with.
 */
class PmdUnusedAssignmentSmokeTest {

  private static final String BINARY_PROPERTY = "ward.pmd.binary";
  private static final String RULESET = "category/java/bestpractices.xml/UnusedAssignment";
  private static final int NO_VIOLATIONS = 0;
  private static final int VIOLATIONS_FOUND = 4;
  private static final Pattern VARIABLE = Pattern.compile("'([^']+)'");
  private static final Pattern FIELD = Pattern.compile("\"((?:[^\"]|\"\")*)\"");

  @Test
  void aRealPmdReportsTheCommittedRows()
      throws IOException, URISyntaxException, InterruptedException {
    final String binary = System.getProperty(BINARY_PROPERTY);
    assumeTrue(binary != null && !binary.isBlank(), "set -D" + BINARY_PROPERTY + " to run it");
    final Path fixtures =
        UnusedAssignmentPmdReferenceTest.resource(UnusedAssignmentPmdReferenceTest.FIXTURES);
    final Process process =
        new ProcessBuilder(
                binary,
                "check",
                "--dir",
                fixtures.toString(),
                "--rulesets",
                RULESET,
                "--format",
                "csv")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final int exit = process.waitFor();

    assertThat(exit).isIn(NO_VIOLATIONS, VIOLATIONS_FOUND);
    assertThat(rows(output)).isEqualTo(UnusedAssignmentPmdReferenceTest.pmdRows());
  }

  /**
   * Reads the file, line and variable of each PMD CSV record, in the reference's order. PMD quotes
   * every field, so each record is read as its quoted fields.
   */
  private static List<String> rows(final String csv) {
    final List<String> lines = csv.lines().toList();
    final List<String> header = fields(lines.get(0));
    return lines.stream()
        .skip(1)
        .map(PmdUnusedAssignmentSmokeTest::fields)
        .map(record -> row(record, header))
        .flatMap(Optional::stream)
        .sorted()
        .toList();
  }

  /** Returns the reference row of one record, or empty when its description names no variable. */
  private static Optional<String> row(final List<String> record, final List<String> header) {
    final Matcher variable = VARIABLE.matcher(record.get(header.indexOf("description")));
    if (!variable.find()) {
      return Optional.empty();
    }
    return Optional.of(
        UnusedAssignmentPmdReferenceTest.row(
            Path.of(record.get(header.indexOf("file"))).getFileName().toString(),
            Integer.valueOf(record.get(header.indexOf("line"))),
            variable.group(1)));
  }

  /** Returns the quoted fields of one CSV line, with doubled quotes unescaped. */
  private static List<String> fields(final String line) {
    final List<String> fields = new ArrayList<>();
    final Matcher field = FIELD.matcher(line);
    while (field.find()) {
      fields.add(field.group(1).replace("\"\"", "\""));
    }
    return fields;
  }
}
