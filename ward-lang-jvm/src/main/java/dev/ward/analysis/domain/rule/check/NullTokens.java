package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads how a local variable of known nullness is used in a run of tokens. */
final class NullTokens {

  private static final String DOT = ".";
  private static final String NULL = "null";
  private static final String RETURN = "return";
  private static final int NEXT = 1;
  private static final int AFTER_NEXT = 2;

  private NullTokens() {}

  /** How one occurrence of the variable is used. */
  enum Use {
    /** Compared with {@code null}. */
    NULL_COMPARISON,
    /** The receiver of a member access. */
    RECEIVER,
    /** Returned from the method. */
    RETURNED,
    /** Read as a value, for instance passed on. */
    LOAD
  }

  /** One occurrence of the variable and how it is used. */
  record Occurrence(SyntaxNode token, Use use) {}

  /** Returns the leaf tokens of {@code nodes}, in order. */
  static List<SyntaxNode> tokens(final List<SyntaxNode> nodes) {
    final List<SyntaxNode> tokens = new ArrayList<>();
    nodes.forEach(node -> tokens.addAll(SyntaxWalk.leaves(node)));
    return tokens;
  }

  /** Tells whether the tokens assign {@code name} anywhere. */
  static boolean assigns(final List<SyntaxNode> tokens, final String name) {
    for (int index = 0; index < tokens.size(); index++) {
      if (isName(tokens, index, name) && isAssignment(text(tokens, index + NEXT))) {
        return true;
      }
    }
    return false;
  }

  /** Returns every occurrence of {@code name} that is not a member of another object. */
  static List<Occurrence> uses(final List<SyntaxNode> tokens, final String name) {
    final List<Occurrence> uses = new ArrayList<>();
    for (int index = 0; index < tokens.size(); index++) {
      if (isName(tokens, index, name)) {
        uses.add(new Occurrence(tokens.get(index), classify(tokens, index)));
      }
    }
    return uses;
  }

  private static Use classify(final List<SyntaxNode> tokens, final int index) {
    final String next = text(tokens, index + NEXT);
    final String before = index == 0 ? "" : text(tokens, index - NEXT);
    if (DOT.equals(next)) {
      return Use.RECEIVER;
    }
    final boolean after = isEquality(next) && NULL.equals(text(tokens, index + AFTER_NEXT));
    final boolean ahead =
        isEquality(before) && index >= AFTER_NEXT && NULL.equals(text(tokens, index - AFTER_NEXT));
    return after || ahead ? Use.NULL_COMPARISON : load(before);
  }

  private static Use load(final String before) {
    return RETURN.equals(before) ? Use.RETURNED : Use.LOAD;
  }

  private static boolean isName(final List<SyntaxNode> tokens, final int index, final String name) {
    return name.equals(text(tokens, index))
        && (index == 0 || !DOT.equals(text(tokens, index - NEXT)));
  }

  private static boolean isEquality(final String token) {
    return "==".equals(token) || "!=".equals(token);
  }

  private static boolean isAssignment(final String token) {
    return token.endsWith("=") && !isEquality(token) && !"<=".equals(token) && !">=".equals(token);
  }

  private static String text(final List<SyntaxNode> tokens, final int index) {
    return index >= 0 && index < tokens.size() ? SyntaxWalk.text(tokens.get(index)) : "";
  }
}
