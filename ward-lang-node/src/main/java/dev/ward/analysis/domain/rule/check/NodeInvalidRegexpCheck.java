package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a call of {@code RegExp} with a string pattern or flags that the regular expression is
 * not valid with, like ESLint {@code no-invalid-regexp}. The syntax is checked by {@link
 * RegexSyntax}.
 */
public final class NodeInvalidRegexpCheck implements RuleCheck {

  private static final String ESCAPE_KEYS = "ntrbfv0";
  private static final String ESCAPE_VALUES = "\n\t\r\b\f\u000B\0";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT = "argument";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String SINGLE = "singleExpression";
  private static final String NEW = "new";
  private static final String REGEXP = "RegExp";
  private static final String STRING = "StringLiteral";
  private static final String LITERAL = "literal";
  private static final String BACKSLASH = "\\";
  private static final int CALL_SIZE = 2;
  private static final int NEW_SIZE = 3;
  private static final int CALLEE_AT_NEW = 1;
  private static final int SECOND_INDEX = 1;

  private final Set<String> allowedFlags;

  /**
   * Creates the check.
   *
   * @param allowedFlags the flags a constructor call may use beyond the valid ones
   */
  public NodeInvalidRegexpCheck(final Set<String> allowedFlags) {
    this.allowedFlags = Set.copyOf(allowedFlags);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (SINGLE.equals(node.kind())) {
      construction(node)
          .flatMap(this::reason)
          .ifPresent(reason -> matches.add(new RuleMatch(node.span(), reason)));
    }
    node.children().forEach(child -> collect(child, matches));
  }

  /** The arguments of a call of {@code RegExp}, when the node is one. */
  private static Optional<SyntaxNode> construction(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() == CALL_SIZE
        && ARGUMENTS.equals(children.get(1).kind())
        && isRegExpName(children.get(0))) {
      return Optional.of(children.get(1));
    }
    if (children.size() == NEW_SIZE
        && NEW.equals(LeafText.of(children.get(0)))
        && isRegExpName(children.get(CALLEE_AT_NEW))
        && ARGUMENTS.equals(children.get(2).kind())) {
      return Optional.of(children.get(2));
    }
    return Optional.empty();
  }

  private static boolean isRegExpName(final SyntaxNode callee) {
    return REGEXP.equals(LeafText.of(callee));
  }

  /** The reason the first argument or the flags are invalid, when they are string literals. */
  private Optional<String> reason(final SyntaxNode arguments) {
    final List<SyntaxNode> values = argumentValues(arguments);
    final Optional<String> pattern =
        values.isEmpty() ? Optional.empty() : stringValue(values.get(0));
    if (pattern.isEmpty()) {
      return Optional.empty();
    }
    final Optional<String> patternReason = RegexSyntax.problem(pattern.get());
    return patternReason.isPresent() ? patternReason : flagsReason(values);
  }

  /** The reason the second argument, when it is a string literal, is not valid flags. */
  private Optional<String> flagsReason(final List<SyntaxNode> values) {
    if (values.size() <= SECOND_INDEX) {
      return Optional.empty();
    }
    return stringValue(values.get(SECOND_INDEX))
        .flatMap(flags -> RegexSyntax.flagProblem(flags, allowedFlags));
  }

  /** The expressions of an argument list, one per argument. */
  private static List<SyntaxNode> argumentValues(final SyntaxNode arguments) {
    final List<SyntaxNode> items = new ArrayList<>();
    for (final SyntaxNode child : arguments.children()) {
      items.addAll(ARGUMENT_LIST.equals(child.kind()) ? child.children() : List.of(child));
    }
    final List<SyntaxNode> values = new ArrayList<>();
    for (final SyntaxNode item : items) {
      if (ARGUMENT.equals(item.kind())) {
        item.children().stream()
            .filter(c -> SINGLE.equals(c.kind()))
            .findFirst()
            .ifPresent(values::add);
      }
    }
    return values;
  }

  /** The value of a string literal expression, with its JavaScript escapes read. */
  private static Optional<String> stringValue(final SyntaxNode expression) {
    final List<SyntaxNode> children = expression.children();
    if (children.size() != 1 || !LITERAL.equals(children.get(0).kind())) {
      return Optional.empty();
    }
    return children.get(0).children().stream()
        .filter(child -> STRING.equals(child.kind()))
        .findFirst()
        .map(literal -> unquote(LeafText.of(literal)));
  }

  /** The text of a string literal without its quotes, with the simple escapes read. */
  static String unquote(final String raw) {
    final String body = raw.substring(1, raw.length() - 1);
    final StringBuilder out = new StringBuilder();
    int index = 0;
    while (index < body.length()) {
      final char c = body.charAt(index);
      if (BACKSLASH.equals(String.valueOf(c)) && index + 1 < body.length()) {
        out.append(escaped(body.charAt(index + 1)));
        index += 2;
      } else {
        out.append(c);
        index++;
      }
    }
    return out.toString();
  }

  /** The character that a backslash escape stands for; other escapes stand for themselves. */
  private static char escaped(final char next) {
    final int index = ESCAPE_KEYS.indexOf(next);
    return index < 0 ? next : ESCAPE_VALUES.charAt(index);
  }
}
