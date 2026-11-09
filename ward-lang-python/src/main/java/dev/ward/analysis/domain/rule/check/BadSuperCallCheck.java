package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code super(...)} call whose first argument is not the enclosing class, like Pylint's
 * {@code bad-super-call}: {@code type(self)} and {@code self.__class__} can recurse, and a name
 * that is neither the class nor one of its bases skips a step of the method resolution order.
 * Shortcut: a name is judged by its text against the class and its direct bases, not by inference.
 */
public final class BadSuperCallCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Set<String> CLASSES = Set.of("classdef");
  private static final String SUPER = "super";
  private static final String TYPE_CALL = "type(";
  private static final String SELF_CLASS = "self.__class__";
  private static final Pattern NAME = Pattern.compile("[A-Za-z_]\\w*");
  private static final String MESSAGE_ARGUMENT = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BadSuperCallCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final String text = SyntaxWalk.text(visit.node());
      if (!text.startsWith(SUPER + "(")) {
        continue;
      }
      final List<String> arguments = superArguments(text);
      final String enclosing = enclosingClass(visit.ancestors());
      if (!arguments.isEmpty()
          && enclosing != null
          && isBad(arguments, enclosing, visit.ancestors())) {
        found.add(
            new RuleMatch(
                visit.node().span(), message.replace(MESSAGE_ARGUMENT, arguments.get(0))));
      }
    }
    return List.copyOf(found);
  }

  private static boolean isBad(
      final List<String> arguments, final String enclosing, final List<SyntaxNode> ancestors) {
    final String first = arguments.get(0);
    if (first.startsWith(TYPE_CALL)) {
      return true;
    }
    if (SELF_CLASS.equals(first)) {
      return arguments.size() > 1 && "self".equals(arguments.get(1));
    }
    return NAME.matcher(first).matches()
        && !first.equals(enclosing)
        && !basesOf(ancestors).contains(first);
  }

  /** The arguments of the first call of {@code text}, from the parentheses after {@code super}. */
  static List<String> superArguments(final String text) {
    final int open = text.indexOf('(');
    int depth = 0;
    for (int index = open; index < text.length(); index++) {
      final char c = text.charAt(index);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return ThreadInstantiationCheck.topLevelArguments(text.substring(open + 1, index))
              .stream()
              .filter(argument -> !argument.isEmpty())
              .toList();
        }
      }
    }
    return List.of();
  }

  /** The name of the nearest enclosing class, or {@code null} outside a class. */
  private static String enclosingClass(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode node = ancestors.get(index);
      if (CLASSES.contains(node.kind())) {
        return node.children().stream()
            .filter(child -> "name".equals(child.kind()) || "NAME".equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse(null);
      }
    }
    return null;
  }

  /** The names in the parentheses of the enclosing class header. */
  private static List<String> basesOf(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode node = ancestors.get(index);
      if (CLASSES.contains(node.kind())) {
        final String text = SyntaxWalk.text(node);
        final int open = text.indexOf('(');
        final int colon = text.indexOf(':');
        if (open < 0 || colon < open) {
          return List.of();
        }
        final String header = text.substring(open + 1, text.lastIndexOf(')', colon));
        return Arrays.stream(header.split(",")).toList();
      }
    }
    return List.of();
  }
}
