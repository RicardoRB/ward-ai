package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.breaking.BreakingFinding;
import dev.ward.analysis.domain.openapi.breaking.BreakingRules;
import dev.ward.analysis.domain.openapi.breaking.DocumentPair;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The fixtures of the breaking-change rules under {@code breaking-rules/openapi/<check id>/}:
 * {@code base.yaml}, {@code revision.yaml} (the violating pair), {@code passing.yaml} (a compatible
 * revision of the base), {@code expected.txt} (one {@code line:column} per finding of the rule in
 * the revision) and {@code oasdiff.txt} (the error-level results of the real oasdiff run on the
 * violating pair, as tab-separated check id, method and path).
 */
final class BreakingFixtures {

  /** The base document of every fixture. */
  static final String BASE = "base.yaml";

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
  private static final String PREFIX = "ward:openapi/";

  private BreakingFixtures() {}

  /** Runs every rule on the pair and keeps the findings of {@code id}, or all when it is empty. */
  static List<BreakingFinding> findings(
      final Path directory, final String revision, final String id) {
    final OpenApiDocument base = load(directory, BASE);
    final OpenApiDocument revised = load(directory, revision);
    return BreakingRules.run(new DocumentPair(base, revised, TODAY)).stream()
        .filter(finding -> id.isEmpty() || finding.ruleId().equals(id))
        .toList();
  }

  /** The {@code line:column} text of where a finding points. */
  static String position(final BreakingFinding finding) {
    return finding.at().anchor().startLine() + ":" + finding.at().anchor().startColumn();
  }

  /** The check id, method and path of a finding, in the format of {@code oasdiff.txt}. */
  static String operation(final BreakingFinding finding) {
    return finding.ruleId().substring(PREFIX.length())
        + "\t"
        + finding.method()
        + "\t"
        + finding.path();
  }

  /** The recorded oasdiff results of one check id, sorted. */
  static List<String> oasdiff(final Path directory, final String name) throws IOException {
    return lines(directory.resolve("oasdiff.txt")).stream()
        .filter(line -> line.startsWith(name + "\t"))
        .sorted()
        .toList();
  }

  /** The non-blank lines of a file. */
  static List<String> lines(final Path file) throws IOException {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isBlank())
        .toList();
  }

  /** The root directory of the fixtures on the test classpath. */
  static Path root() {
    try {
      return Path.of(BreakingFixtures.class.getResource("/breaking-rules/openapi").toURI());
    } catch (final URISyntaxException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static OpenApiDocument load(final Path directory, final String file) {
    final LoadResult result =
        OpenApiDocuments.load(
            file,
            path -> {
              try {
                return Optional.of(
                    Files.readString(directory.resolve(path), StandardCharsets.UTF_8));
              } catch (final IOException unreadable) {
                return Optional.empty();
              }
            });
    assertThat(result.errors()).as("load errors of %s/%s", directory.getFileName(), file).isEmpty();
    return result.document().orElseThrow();
  }
}
