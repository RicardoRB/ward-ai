package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a function whose parameters repeat a name, like Pylint's {@code duplicate-argument-name}.
 * The finding sits at the function, as Pylint places it. Shortcut: each parameter is read as its
 * leading identifier, so annotations and defaults are not parsed.
 */
public final class DuplicateArgumentNameCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of("funcdef", "lambdef");

  /** The index of the last letter of {@code lambda}, before its parameters. */
  private static final int LAMBDA_OPEN = 5;

  private static final Pattern IDENTIFIER = Pattern.compile("^\\*{0,2}([A-Za-z_]\\w*)");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public DuplicateArgumentNameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      if (repeatsName(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Whether the parameter list of the function names one parameter twice. */
  static boolean repeatsName(final SyntaxNode function) {
    final Set<String> seen = new HashSet<>();
    for (final String parameter : parameters(function)) {
      final Matcher matcher = IDENTIFIER.matcher(parameter);
      if (matcher.find() && !seen.add(matcher.group(1))) {
        return true;
      }
    }
    return false;
  }

  /** The parameter texts between the parentheses, or the lambda's parameters before the colon. */
  private static List<String> parameters(final SyntaxNode function) {
    final String text = SyntaxWalk.text(function);
    final int open = "lambdef".equals(function.kind()) ? LAMBDA_OPEN : text.indexOf('(');
    final int close =
        "lambdef".equals(function.kind())
            ? text.indexOf(':')
            : ThreadInstantiationCheck.matchingClose(text, open);
    if (open < 0 || close <= open) {
      return List.of();
    }
    return ThreadInstantiationCheck.topLevelArguments(text.substring(open + 1, close));
  }
}
