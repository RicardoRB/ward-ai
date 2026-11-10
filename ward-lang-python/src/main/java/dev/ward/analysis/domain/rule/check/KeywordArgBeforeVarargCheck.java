package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a function that declares a positional parameter with a default before {@code *args}, like
 * pylint {@code keyword-arg-before-vararg}. A signature with {@code /} and no positional-or-keyword
 * parameter after it is not reported, as pylint does.
 */
public final class KeywordArgBeforeVarargCheck implements RuleCheck {

  private static final String PARAMETERS = "parameters";
  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public KeywordArgBeforeVarargCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      final String parameters = parameters(visit.node());
      if (parameters != null && violates(ArgumentList.split(strip(parameters)))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static String parameters(final SyntaxNode function) {
    for (final SyntaxNode child : function.children()) {
      if (PARAMETERS.equals(child.kind())) {
        return SyntaxWalk.text(child);
      }
    }
    return null;
  }

  private static String strip(final String parameters) {
    final String text = parameters.strip();
    return text.length() >= 2 ? text.substring(1, text.length() - 1) : "";
  }

  private static boolean violates(final List<String> items) {
    final int vararg = varargIndex(items);
    if (vararg < 0 || !hasPositionalDefault(items, vararg)) {
      return false;
    }
    final int slash = items.indexOf("/");
    return slash < 0 || vararg - slash - 1 > 0;
  }

  private static int varargIndex(final List<String> items) {
    for (int index = 0; index < items.size(); index++) {
      final String item = items.get(index);
      if (item.startsWith("*") && !item.startsWith("**") && item.length() > 1) {
        return index;
      }
    }
    return -1;
  }

  private static boolean hasPositionalDefault(final List<String> items, final int vararg) {
    return items.subList(0, vararg).stream().anyMatch(item -> item.contains("="));
  }
}
