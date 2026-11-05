package dev.ward.analysis.domain.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The checks every formatter fixture must pass: the golden output through the safety checks,
 * stability of the golden output, and the comments kept as written. It reads no files, so a test
 * that loads fixtures from any folder can reuse it.
 */
public final class FormatFixtureChecks {

  private static final String COMMENT = "comment";

  private final SourceFormatter formatter;
  private final FormatSafety safety;
  private final FormatOptions options;

  /**
   * Creates the checks for one formatter.
   *
   * @param formatter the formatter under test
   * @param safety the safety checks, built on the same language's parser
   * @param options the option values the fixtures use
   * @throws NullPointerException if an argument is missing
   */
  public FormatFixtureChecks(
      final SourceFormatter formatter, final FormatSafety safety, final FormatOptions options) {
    this.formatter = Objects.requireNonNull(formatter, "formatter");
    this.safety = Objects.requireNonNull(safety, "safety");
    this.options = Objects.requireNonNull(options, "options");
  }

  /**
   * Checks one fixture case.
   *
   * @param input the input text
   * @param expected the golden output, or empty when the input must be refused
   * @return the problems found; empty when the case passes
   * @throws NullPointerException if an argument is missing
   */
  public List<String> problems(final String input, final Optional<String> expected) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(expected, "expected");
    final List<String> problems = new ArrayList<>();
    if (expected.isEmpty()) {
      checkRefused(input, problems);
      return problems;
    }
    final String golden = expected.get();
    if (golden.equals(input)
        && !(safety.formatSafely(formatter, options, input) instanceof FormatResult.Unchanged)) {
      problems.add("formatted input must be unchanged");
    }
    if (!outcome(input).equals(golden)) {
      problems.add("golden output differs: " + outcome(input));
    }
    if (!outcome(golden).equals(golden)) {
      problems.add("golden output is not stable");
    }
    if (!commentTexts(input).equals(commentTexts(golden))) {
      problems.add("comments changed");
    }
    return problems;
  }

  private void checkRefused(final String input, final List<String> problems) {
    if (!(safety.formatSafely(formatter, options, input) instanceof FormatResult.Refused)) {
      problems.add("expected the input to be refused");
    }
  }

  /** The text a source turns into through the safety checks, or a description when refused. */
  private String outcome(final String source) {
    final FormatResult result = safety.formatSafely(formatter, options, source);
    if (result instanceof FormatResult.Formatted formatted) {
      return formatted.text();
    }
    if (result instanceof FormatResult.Refused refused) {
      return "refused at line " + refused.line() + ": " + refused.reason();
    }
    return source;
  }

  private List<String> commentTexts(final String source) {
    return safety.parse(source).tokens().stream()
        .filter(token -> COMMENT.equals(token.type()))
        .map(FormatToken::text)
        .toList();
  }
}
