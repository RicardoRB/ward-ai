package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each later key of a dictionary literal that repeats an earlier key, like SwiftLint's
 * {@code duplicated_key_in_dictionary_literal}. Only literal, member and identifier keys are
 * compared; a key that is a call or any other expression is never a duplicate.
 */
public final class SwiftDuplicatedDictionaryKeyCheck implements RuleCheck {

  private static final String LITERAL = "dictionary_literal";
  private static final String ITEMS = "dictionary_literal_items";
  private static final String ITEM = "dictionary_literal_item";
  private static final String EXPRESSION = "expression";
  private static final Pattern SIMPLE_KEY =
      Pattern.compile("\"[^\"]*\"|[-+]?[0-9][0-9._]*|\\.?[A-Za-z_][A-Za-z0-9_.]*");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftDuplicatedDictionaryKeyCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode literal : SwiftTrees.descendants(root, LITERAL)) {
      repeatedKeys(literal, matches);
    }
    return List.copyOf(matches);
  }

  private void repeatedKeys(final SyntaxNode literal, final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    final List<SyntaxNode> keys = new ArrayList<>();
    collectKeys(literal, keys);
    for (final SyntaxNode key : keys) {
      final String text = SwiftTrees.compact(key);
      if (SIMPLE_KEY.matcher(text).matches() && !seen.add(text)) {
        matches.add(SwiftTrees.at(key, message));
      }
    }
  }

  /** Collects the key of each item of the literal, in order, without entering nested literals. */
  private static void collectKeys(final SyntaxNode node, final List<SyntaxNode> keys) {
    for (final SyntaxNode child : node.children()) {
      if (ITEM.equals(child.kind())) {
        SwiftTrees.child(child, EXPRESSION).ifPresent(keys::add);
      } else if (ITEMS.equals(child.kind())) {
        collectKeys(child, keys);
      }
    }
  }
}
