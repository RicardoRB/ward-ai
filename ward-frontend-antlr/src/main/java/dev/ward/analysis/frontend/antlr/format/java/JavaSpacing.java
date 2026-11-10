package dev.ward.analysis.frontend.antlr.format.java;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether two tokens on one line are separated by a space. The rules are token-pair rules.
 * The role of an operator (prefix, postfix, cast, type-argument bracket) comes from the tokens
 * around it and from the parse tree.
 */
final class JavaSpacing {

  private static final Set<String> MULTI_TOKEN =
      Set.of(
          "<<", ">>", "<=", ">=", "==", "!=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "&&",
          "||", "++", "--", "->", "::");
  private static final Set<String> NONE_BEFORE = Set.of(")", "]", ",", ";", ".", "::", "...", "}");
  private static final Set<String> NONE_AFTER = Set.of("(", "[", ".", "::", "@");
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String OPEN_BRACKET = "[";
  private static final String CLOSE_BRACKET = "]";
  private static final String OPEN_BLOCK = "{";
  private static final String OPEN_ANGLE = "<";
  private static final String CLOSE_ANGLE = ">";
  private static final String COLON = ":";
  private static final String NOT = "!";
  private static final String TILDE = "~";
  private static final String INCREMENT = "++";
  private static final String DECREMENT = "--";
  private static final String PLUS = "+";
  private static final String MINUS = "-";

  private final JavaTreeFacts facts;
  private final Set<Integer> prefix = new HashSet<>();
  private final Set<Integer> postfix = new HashSet<>();
  private final Set<Integer> castClose = new HashSet<>();

  /**
   * Classifies the operators and casts of one source.
   *
   * @param atoms the atoms in source order, comments included
   * @param facts the parse-tree facts of the same source
   */
  JavaSpacing(final List<JavaAtom> atoms, final JavaTreeFacts facts) {
    this.facts = facts;
    final List<JavaAtom> codes = atoms.stream().filter(atom -> !atom.comment()).toList();
    for (int i = 0; i < codes.size(); i++) {
      if (OPEN_PAREN.equals(codes.get(i).text()) && !operandEnd(codes, i - 1)) {
        classifyCast(codes, i);
      }
    }
    for (int i = 0; i < codes.size(); i++) {
      classifyOperator(codes, i);
    }
  }

  /**
   * Tells whether a space separates two tokens on the same line.
   *
   * @param before the earlier token
   * @param after the later token
   * @return true for one space, false for none
   */
  boolean spaceBetween(final JavaAtom before, final JavaAtom after) {
    final String second = after.text();
    if (MULTI_TOKEN.contains(before.text() + second) && !after.gapSpace()) {
      return false;
    }
    if (isNoneBefore(after) || isNoneAfter(before)) {
      return false;
    }
    if (OPEN_PAREN.equals(second) || OPEN_BRACKET.equals(second)) {
      return spaceBeforeOpening(before, second);
    }
    return !(OPEN_ANGLE.equals(second) && isAngle(after) && JavaTokens.isIdentifier(before));
  }

  /** A call, declaration or index opens right after a name; a keyword or operator does not. */
  private boolean spaceBeforeOpening(final JavaAtom before, final String opening) {
    final String text = before.text();
    if (OPEN_BRACKET.equals(opening)) {
      return !(JavaTokens.isIdentifier(before)
          || JavaTokens.PRIMITIVES.contains(text)
          || CLOSE_BRACKET.equals(text));
    }
    return JavaTokens.KEYWORD_PAREN.contains(text)
        || !(JavaTokens.isIdentifier(before)
            || CLOSE_PAREN.equals(text)
            || CLOSE_BRACKET.equals(text)
            || isAngleClose(before));
  }

  private void classifyCast(final List<JavaAtom> codes, final int open) {
    final int close = JavaTokens.matchingClose(codes, open);
    if (close < 0
        || close + 1 >= codes.size()
        || close == open + 1
        || JavaTokens.followsKeywordParen(codes, open - 1)
        || !JavaTokens.castContentIsType(codes, open, close)) {
      return;
    }
    final JavaAtom next = codes.get(close + 1);
    final boolean primitiveSign =
        JavaTokens.PRIMITIVES.contains(codes.get(open + 1).text()) && JavaTokens.isSign(next);
    if (JavaTokens.startsOperand(next) || primitiveSign) {
      castClose.add(codes.get(close).index());
    }
  }

  private void classifyOperator(final List<JavaAtom> codes, final int index) {
    final JavaAtom atom = codes.get(index);
    final String text = atom.text();
    final boolean afterOperand = operandEnd(codes, index - 1);
    if (NOT.equals(text)
        || TILDE.equals(text)
        || !afterOperand && (PLUS.equals(text) || MINUS.equals(text))) {
      prefix.add(atom.index());
    } else if (INCREMENT.equals(text) || DECREMENT.equals(text)) {
      (afterOperand ? postfix : prefix).add(atom.index());
    }
  }

  private boolean operandEnd(final List<JavaAtom> codes, final int index) {
    if (index < 0) {
      return false;
    }
    final JavaAtom atom = codes.get(index);
    final String text = atom.text();
    if (CLOSE_PAREN.equals(text)) {
      return !castClose.contains(atom.index());
    }
    return CLOSE_BRACKET.equals(text)
        || postfix.contains(atom.index())
        || JavaTokens.isLiteral(text)
        || JavaTokens.isIdentifier(atom);
  }

  private boolean isNoneBefore(final JavaAtom atom) {
    final String text = atom.text();
    return NONE_BEFORE.contains(text)
        || postfix.contains(atom.index())
        || isAngleClose(atom)
        || COLON.equals(text) && facts.hardAfter().contains(atom.index());
  }

  private boolean isNoneAfter(final JavaAtom atom) {
    return NONE_AFTER.contains(atom.text())
        || prefix.contains(atom.index())
        || castClose.contains(atom.index())
        || OPEN_ANGLE.equals(atom.text()) && isAngle(atom)
        || OPEN_BLOCK.equals(atom.text()) && facts.arrayOpens().contains(atom.index());
  }

  private boolean isAngle(final JavaAtom atom) {
    return facts.angleTokens().contains(atom.index());
  }

  private boolean isAngleClose(final JavaAtom atom) {
    return CLOSE_ANGLE.equals(atom.text()) && isAngle(atom);
  }
}
