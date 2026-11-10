package dev.ward.analysis.domain.format;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Runs a formatter and accepts its result only when the output keeps the meaning of the input: it
 * parses without syntax errors, keeps every non-whitespace token (comments included) and the
 * parse-tree rule kinds in order, and formats to itself again. Any failure yields {@link
 * FormatResult.Refused}, so the caller never writes the file.
 */
public final class FormatSafety {

  private static final String NEWLINE = "\n";
  private static final String CRLF = "\r\n";
  private static final int FIRST_LINE = 1;

  private final FormatSyntax syntax;

  /**
   * Creates the checks for one language's parser.
   *
   * @param syntax the parser adapter for the language
   * @throws NullPointerException if the syntax is missing
   */
  public FormatSafety(final FormatSyntax syntax) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
  }

  /**
   * Formats a source text and checks the result.
   *
   * @param formatter the formatter to run
   * @param options the resolved option values
   * @param source the source text, with {@code \n} line endings
   * @return {@link FormatResult.Formatted} when the output passes every check, {@link
   *     FormatResult.Unchanged} when the source is already formatted, or {@link
   *     FormatResult.Refused} with the first differing line otherwise
   * @throws NullPointerException if an argument is missing
   */
  public FormatResult formatSafely(
      final SourceFormatter formatter, final FormatOptions options, final String source) {
    Objects.requireNonNull(formatter, "formatter");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(source, "source");
    final FormatSyntaxTree input = syntax.parse(source);
    final Optional<FormatResult.Refused> inputError =
        syntaxRefusal(input, "input has a syntax error");
    if (inputError.isPresent()) {
      return inputError.get();
    }
    final FormatResult result = formatter.format(source, options);
    if (result instanceof FormatResult.Formatted formatted) {
      return accept(formatter, options, input, source, formatted.text());
    }
    return result;
  }

  /**
   * Parses a text with the language's parser, for callers that compare comments or tokens.
   *
   * @param source the source text
   * @return the syntax view
   */
  public FormatSyntaxTree parse(final String source) {
    return syntax.parse(source);
  }

  private FormatResult accept(
      final SourceFormatter formatter,
      final FormatOptions options,
      final FormatSyntaxTree input,
      final String source,
      final String output) {
    if (output.equals(source)) {
      return new FormatResult.Unchanged();
    }
    final Optional<FormatResult.Refused> refusal = verify(formatter, options, input, output);
    return refusal
        .<FormatResult>map(refused -> refused)
        .orElseGet(() -> new FormatResult.Formatted(output));
  }

  private Optional<FormatResult.Refused> verify(
      final SourceFormatter formatter,
      final FormatOptions options,
      final FormatSyntaxTree input,
      final String output) {
    final FormatSyntaxTree parsed = syntax.parse(output);
    Optional<FormatResult.Refused> refusal =
        syntaxRefusal(parsed, "formatted output has a syntax error");
    if (refusal.isEmpty()) {
      refusal =
          mismatch(input.tokens(), parsed.tokens(), FormatSafety::tokenKey, FormatToken::line)
              .map(line -> new FormatResult.Refused("formatting changed a token", line));
    }
    if (refusal.isEmpty()) {
      refusal =
          mismatch(input.rules(), parsed.rules(), FormatRule::kind, FormatRule::line)
              .map(line -> new FormatResult.Refused("formatting changed the syntax tree", line));
    }
    if (refusal.isEmpty()) {
      refusal = unstable(formatter, options, output);
    }
    return refusal;
  }

  private static Optional<FormatResult.Refused> syntaxRefusal(
      final FormatSyntaxTree tree, final String reason) {
    final OptionalInt errorLine = tree.errorLine();
    if (errorLine.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new FormatResult.Refused(reason, errorLine.getAsInt()));
  }

  private static Object tokenKey(final FormatToken token) {
    return List.of(token.type(), token.text().replace(CRLF, NEWLINE));
  }

  /** Returns the line in {@code actual} of the first item that differs, if any. */
  private static <T> Optional<Integer> mismatch(
      final List<T> expected,
      final List<T> actual,
      final Function<T, Object> key,
      final ToIntFunction<T> line) {
    final int shared = Math.min(expected.size(), actual.size());
    for (int i = 0; i < shared; i++) {
      if (!key.apply(expected.get(i)).equals(key.apply(actual.get(i)))) {
        return Optional.of(line.applyAsInt(actual.get(i)));
      }
    }
    if (expected.size() == actual.size()) {
      return Optional.empty();
    }
    return Optional.of(actual.isEmpty() ? FIRST_LINE : line.applyAsInt(actual.getLast()));
  }

  private static Optional<FormatResult.Refused> unstable(
      final SourceFormatter formatter, final FormatOptions options, final String output) {
    final FormatResult again = formatter.format(output, options);
    if (again instanceof FormatResult.Unchanged) {
      return Optional.empty();
    }
    if (again instanceof FormatResult.Formatted formatted) {
      final int line = firstDifferentLine(output, formatted.text());
      return Optional.of(new FormatResult.Refused("formatting is not stable", line));
    }
    final FormatResult.Refused refused = (FormatResult.Refused) again;
    return Optional.of(
        new FormatResult.Refused(
            "formatted output cannot be formatted again: " + refused.reason(), refused.line()));
  }

  private static int firstDifferentLine(final String first, final String second) {
    final String[] before = first.split(NEWLINE, -1);
    final String[] after = second.split(NEWLINE, -1);
    final int shared = Math.min(before.length, after.length);
    for (int i = 0; i < shared; i++) {
      if (!before[i].equals(after[i])) {
        return i + FIRST_LINE;
      }
    }
    return shared + FIRST_LINE;
  }
}
