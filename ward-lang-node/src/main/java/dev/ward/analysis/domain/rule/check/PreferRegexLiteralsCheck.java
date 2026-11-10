package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports a {@code RegExp} call that a regular expression literal can replace, like ESLint {@code
 * prefer-regex-literals}: a call with one or two static string arguments. With {@code
 * disallowRedundantWrapping} set, a call that wraps a regular expression literal is reported too,
 * when its second argument, if any, is a string.
 */
public final class PreferRegexLiteralsCheck implements RuleCheck {

  private static final String CALL = "singleExpression";
  private static final String IDENTIFIER = "identifier";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String REGEXP = "RegExp";
  private static final String REGEX_TOKEN = "RegularExpressionLiteral";
  private static final String STRING_TOKEN = "StringLiteral";

  private final boolean disallowRedundantWrapping;
  private final String message;

  /**
   * Creates the check.
   *
   * @param disallowRedundantWrapping whether a call wrapping a regular expression literal is
   *     reported
   * @param message the message of each finding
   */
  public PreferRegexLiteralsCheck(final boolean disallowRedundantWrapping, final String message) {
    this.disallowRedundantWrapping = disallowRedundantWrapping;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (CALL.equals(node.kind()) && isRegExpCall(node) && reportable(argumentsOf(node))) {
      matches.add(new RuleMatch(node.span(), message));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private static boolean isRegExpCall(final SyntaxNode call) {
    return call.children().stream()
        .anyMatch(child -> isCallee(child.kind()) && REGEXP.equals(text(child)));
  }

  /** A call names its callee as an expression; a {@code new} call names it as an identifier. */
  private static boolean isCallee(final String kind) {
    return CALL.equals(kind) || IDENTIFIER.equals(kind);
  }

  /** The argument nodes of a call; TypeScript wraps several of them in an argumentList. */
  private static List<SyntaxNode> argumentsOf(final SyntaxNode call) {
    final List<SyntaxNode> arguments = new ArrayList<>();
    for (final SyntaxNode child : call.children()) {
      if (ARGUMENTS.equals(child.kind())) {
        for (final SyntaxNode item : child.children()) {
          if (ARGUMENT_LIST.equals(item.kind())) {
            item.children().stream().filter(a -> ARGUMENT.equals(a.kind())).forEach(arguments::add);
          } else if (ARGUMENT.equals(item.kind())) {
            arguments.add(item);
          }
        }
      }
    }
    return arguments;
  }

  private boolean reportable(final List<SyntaxNode> arguments) {
    if (arguments.isEmpty() || arguments.size() > 2) {
      return false;
    }
    final String first = firstToken(arguments.get(0));
    if (STRING_TOKEN.equals(first)) {
      return arguments.stream().allMatch(argument -> STRING_TOKEN.equals(firstToken(argument)));
    }
    return disallowRedundantWrapping
        && REGEX_TOKEN.equals(first)
        && (arguments.size() == 1 || STRING_TOKEN.equals(firstToken(arguments.get(1))));
  }

  private static String firstToken(final SyntaxNode node) {
    SyntaxNode current = node;
    while (!current.children().isEmpty()) {
      current = current.children().get(0);
    }
    return current.kind();
  }

  private static String text(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      final String token = node.fields().get("text");
      return token == null ? "" : token;
    }
    final StringBuilder out = new StringBuilder();
    for (final SyntaxNode child : node.children()) {
      out.append(text(child));
    }
    return out.toString();
  }
}
