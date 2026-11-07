package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a call of {@code equals} on a qualifier that is not a string literal, with a string
 * literal, or a concatenation of them, as its argument. The literal belongs on the left side, where
 * a null variable cannot throw. The user option is {@code ignore-equals-ignore-case}, which skips
 * {@code equalsIgnoreCase}. Shortcut: the argument is read as written, so an assignment to a field
 * that holds a literal is not reported.
 */
public final class EqualsAvoidNullCheck implements UnitCheck {

  private static final String DOT = "'.'";
  private static final String METHOD_CALL = "methodCall";
  private static final String ARGUMENTS = "arguments";
  private static final String OPEN = "'('";
  private static final Pattern CALL =
      Pattern.compile(
          "[^\"]*\\.(equals|equalsIgnoreCase)\\((?:\"(?:[^\"\\\\]|\\\\.)*\"\\+?)+\\)",
          Pattern.DOTALL);
  private static final Set<String> EXPRESSION = Set.of("expression");

  private final boolean ignoreEqualsIgnoreCase;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoreEqualsIgnoreCase whether {@code equalsIgnoreCase} calls are not reported
   * @param message the message reported at the call
   */
  public EqualsAvoidNullCheck(final boolean ignoreEqualsIgnoreCase, final String message) {
    this.ignoreEqualsIgnoreCase = ignoreEqualsIgnoreCase;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, EXPRESSION)) {
      final SyntaxNode node = visit.node();
      if (hasDot(node) && reported(SyntaxWalk.text(node))) {
        matches.add(CheckSupport.at(openParenthesis(node), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean hasDot(final SyntaxNode node) {
    return node.children().stream().anyMatch(c -> DOT.equals(c.kind()));
  }

  /** The span of the parenthesis that opens the call's argument list, where Checkstyle reports. */
  private static Span openParenthesis(final SyntaxNode node) {
    return node.children().stream()
        .filter(c -> METHOD_CALL.equals(c.kind()))
        .flatMap(c -> c.children().stream())
        .filter(c -> ARGUMENTS.equals(c.kind()))
        .flatMap(c -> c.children().stream())
        .filter(c -> OPEN.equals(c.kind()))
        .map(SyntaxNode::span)
        .findFirst()
        .orElseGet(node::span);
  }

  private boolean reported(final String text) {
    final Matcher matcher = CALL.matcher(text);
    return matcher.matches()
        && !(ignoreEqualsIgnoreCase && "equalsIgnoreCase".equals(matcher.group(1)));
  }
}
