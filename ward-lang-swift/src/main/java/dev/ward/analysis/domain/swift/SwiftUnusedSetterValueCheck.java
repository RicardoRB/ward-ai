package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reports a {@code set} accessor whose new value ({@code newValue}, or the name in {@code
 * set(name)}) is never used in its body, like SwiftLint's {@code unused_setter_value}. An empty
 * setter of an {@code override} is not reported. The grammar keeps accessors in the variable's code
 * block, so the accessor is found from its tokens: a {@code set} that follows {@code {} or {@code
 * }} and opens a brace block, optionally after a parenthesised name.
 */
public final class SwiftUnusedSetterValueCheck implements RuleCheck {

  private static final String SET = "set";
  private static final String VARIABLE = "variable_declaration";
  private static final String NEW_VALUE = "newValue";
  private static final String OPEN = "{";
  private static final String CLOSE = "}";
  private static final String OPEN_PARENTHESIS = "(";
  private static final String CLOSE_PARENTHESIS = ")";
  private static final String OVERRIDE = "override";
  private static final int OVERRIDE_LOOKBACK = 40;
  private static final int CLOSE_AFTER_NAME = 3;
  private static final int BODY_AFTER_NAME = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftUnusedSetterValueCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<Integer> reported = new HashSet<>();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode declaration : SwiftTrees.descendants(root, VARIABLE)) {
      final List<SyntaxNode> tokens = SwiftTrees.leaves(declaration);
      for (int i = 1; i < tokens.size() - 1; i++) {
        final SyntaxNode set = tokens.get(i);
        if (isSetAccessor(tokens, i)
            && isUnused(tokens, i)
            && reported.add(set.span().startOffset())) {
          matches.add(SwiftTrees.at(set, message));
        }
      }
    }
    return List.copyOf(matches);
  }

  /*
   * shortcut: the accessor is found from tokens, because the grammar keeps get and set inside the
   * variable's code block; upgrade when the grammar models getter_setter_block for class members.
   */
  private static boolean isSetAccessor(final List<SyntaxNode> tokens, final int i) {
    final String before = text(tokens, i - 1);
    final String current = text(tokens, i);
    final String next = text(tokens, i + 1);
    return SET.equals(current)
        && (OPEN.equals(before) || CLOSE.equals(before))
        && (OPEN.equals(next) || OPEN_PARENTHESIS.equals(next));
  }

  private static boolean isUnused(final List<SyntaxNode> tokens, final int set) {
    final int open = bodyOpen(tokens, set);
    if (open < 0) {
      return false;
    }
    final int close = matchingClose(tokens, open);
    if (close < 0) {
      return false;
    }
    final String name = parameterName(tokens, set);
    final boolean empty = close == open + 1;
    if (empty && isOverride(tokens, set)) {
      return false;
    }
    for (int k = open + 1; k < close; k++) {
      if (name.equals(text(tokens, k))) {
        return false;
      }
    }
    return true;
  }

  /** The index of the body's opening brace, after an optional parameter name. */
  private static int bodyOpen(final List<SyntaxNode> tokens, final int set) {
    if (OPEN.equals(text(tokens, set + 1))) {
      return set + 1;
    }
    final boolean named =
        CLOSE_PARENTHESIS.equals(text(tokens, set + CLOSE_AFTER_NAME))
            && OPEN.equals(text(tokens, set + BODY_AFTER_NAME));
    return named ? set + BODY_AFTER_NAME : -1;
  }

  private static String parameterName(final List<SyntaxNode> tokens, final int set) {
    return OPEN_PARENTHESIS.equals(text(tokens, set + 1)) ? text(tokens, set + 2) : NEW_VALUE;
  }

  private static int matchingClose(final List<SyntaxNode> tokens, final int open) {
    int depth = 0;
    for (int k = open; k < tokens.size(); k++) {
      final String text = text(tokens, k);
      if (OPEN.equals(text)) {
        depth++;
      } else if (CLOSE.equals(text)) {
        depth--;
        if (depth == 0) {
          return k;
        }
      }
    }
    return -1;
  }

  /** Whether the declaration of this accessor is an override, looking back to its keyword. */
  private static boolean isOverride(final List<SyntaxNode> tokens, final int set) {
    for (int k = set - 1; k >= 0 && k >= set - OVERRIDE_LOOKBACK; k--) {
      final String text = text(tokens, k);
      if (OVERRIDE.equals(text)) {
        return true;
      }
      if (isDeclarationKeyword(text)) {
        return false;
      }
    }
    return false;
  }

  private static boolean isDeclarationKeyword(final String text) {
    return "var".equals(text) || "subscript".equals(text) || "func".equals(text);
  }

  private static String text(final List<SyntaxNode> tokens, final int index) {
    return index >= 0 && index < tokens.size() ? tokens.get(index).fields().get("text") : "";
  }
}
