package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reports a call of {@code compile}, {@code matches}, {@code split}, {@code replaceAll} or {@code
 * replaceFirst} whose first argument is a string literal that is not a valid regular expression.
 */
public final class InvalidRegexLiteralCheck implements UnitCheck {
  private static final String METHOD_CALL = "methodCall";
  private static final int LITERAL = 2;
  private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");
  private static final Pattern CALL =
      Pattern.compile(
          "^(compile|matches|split|replaceAll|replaceFirst)\\((\"(?:[^\"\\\\]|\\\\.)*\")");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public InvalidRegexLiteralCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD_CALL))) {
      final Matcher call = CALL.matcher(SyntaxWalk.text(visit.node()));
      if (call.find() && !valid(unescape(call.group(LITERAL)))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean valid(final String regex) {
    try {
      Pattern.compile(regex);
      return true;
    } catch (PatternSyntaxException invalid) {
      return false;
    }
  }

  /** Returns the value of the string literal {@code quoted}, with its common escapes undone. */
  private static String unescape(final String quoted) {
    final String body = quoted.substring(1, quoted.length() - 1);
    return ESCAPE
        .matcher(body)
        .replaceAll(
            found -> Matcher.quoteReplacement(String.valueOf(escaped(found.group(1).charAt(0)))));
  }

  private static char escaped(final char c) {
    return switch (c) {
      case 'n' -> '\n';
      case 't' -> '\t';
      default -> c;
    };
  }
}
