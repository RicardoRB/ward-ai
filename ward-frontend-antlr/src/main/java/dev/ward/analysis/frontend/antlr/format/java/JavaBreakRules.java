package dev.ward.analysis.frontend.antlr.format.java;

import java.util.Set;

/** The token rules that decide where a Java line breaks and how a bracket scope is kept. */
final class JavaBreakRules {

  static final String OPEN_BLOCK = "{";
  static final String OPEN_PAREN = "(";
  static final String OPEN_BRACKET = "[";
  static final String CLOSE_BLOCK = "}";
  static final String CLOSE_PAREN = ")";
  static final String CLOSE_BRACKET = "]";
  static final String COLON = ":";
  static final String DOT = ".";
  static final String COMMA = ",";
  static final String SEMICOLON = ";";
  static final String AT = "@";
  static final String CASE = "case";
  static final String DEFAULT = "default";

  private static final String ELSE = "else";
  private static final String CATCH = "catch";
  private static final String FINALLY = "finally";
  private static final String WHILE = "while";
  private static final Set<String> KEEP_SPACE_AFTER_BLOCK = Set.of(ELSE, CATCH, FINALLY, WHILE);
  private static final Set<String> KEEP_NONE_AFTER_BLOCK =
      Set.of(CLOSE_PAREN, CLOSE_BRACKET, COMMA, SEMICOLON, DOT);
  private static final Set<String> NONE_BEFORE =
      Set.of(CLOSE_PAREN, CLOSE_BRACKET, COMMA, SEMICOLON);

  private static final int PLAIN_LINE = 0;
  private static final int CONDITIONAL_LINE = 1;
  private static final int OPERATOR_LINE = 2;
  private static final int CHAIN_LINE = 3;
  private static final Set<String> CONDITIONAL = Set.of("?", COLON);
  private static final Set<String> BINARY =
      Set.of(
          "+",
          "-",
          "*",
          "/",
          "%",
          "&&",
          "||",
          "&",
          "|",
          "^",
          "==",
          "!=",
          "<",
          ">",
          "<=",
          ">=",
          "<<",
          ">>",
          ">>>",
          "instanceof");
  private static final Set<String> STATEMENT_ENDS = Set.of(SEMICOLON, OPEN_BLOCK, CLOSE_BLOCK);

  private JavaBreakRules() {}

  /**
   * Returns how tightly the token that starts a continuation line binds: a plain operand, a
   * conditional {@code ?} or {@code :}, a binary operator, or a call-chain dot, from loosest to
   * tightest.
   *
   * @param text the token text
   * @return the line kind, higher for tighter
   */
  static int lineKind(final String text) {
    if (DOT.equals(text)) {
      return CHAIN_LINE;
    }
    if (BINARY.contains(text)) {
      return OPERATOR_LINE;
    }
    return CONDITIONAL.contains(text) ? CONDITIONAL_LINE : PLAIN_LINE;
  }

  /**
   * Tells whether a token ends a statement, so a line break after it is not a continuation.
   *
   * @param text the token text
   * @return whether the token ends a statement
   */
  static boolean endsStatement(final String text) {
    return STATEMENT_ENDS.contains(text);
  }

  /**
   * Returns the break a token asks for before it, from its own text. A dot that starts a line in
   * the source keeps its line, so a call chain stays one call per line.
   *
   * @param atom the token
   * @return the break, or {@code UNSET} when the token has no rule of its own
   */
  static JavaBreak beforeOf(final JavaAtom atom) {
    if (OPEN_BLOCK.equals(atom.text())) {
      return JavaBreak.SPACE;
    }
    if (DOT.equals(atom.text())) {
      return atom.newlines() > 0 ? JavaBreak.UNSET : JavaBreak.NONE;
    }
    return NONE_BEFORE.contains(atom.text()) ? JavaBreak.NONE : JavaBreak.UNSET;
  }

  /**
   * Returns the break after a closed block, from the token that follows it.
   *
   * @param text the next token text
   * @return the break
   */
  static JavaBreak afterBlock(final String text) {
    if (KEEP_SPACE_AFTER_BLOCK.contains(text)) {
      return JavaBreak.SPACE;
    }
    return KEEP_NONE_AFTER_BLOCK.contains(text) ? JavaBreak.NONE : JavaBreak.HARD;
  }

  /**
   * Returns the break a token leaves for the token after it.
   *
   * @param atom the token just written
   * @param facts the parse-tree facts
   * @param blockTop whether the enclosing scope is a block
   * @param groupTop whether the enclosing scope is a group
   * @return the break, or {@code UNSET} when the next token decides
   */
  static JavaBreak afterToken(
      final JavaAtom atom,
      final JavaTreeFacts facts,
      final boolean blockTop,
      final boolean groupTop) {
    final String text = atom.text();
    if (facts.hardAfter().contains(atom.index())) {
      return JavaBreak.HARD;
    }
    if (SEMICOLON.equals(text)) {
      return blockTop ? JavaBreak.HARD : JavaBreak.SPACE;
    }
    if (COMMA.equals(text)) {
      return groupTop ? JavaBreak.LINE : JavaBreak.SPACE;
    }
    return DOT.equals(text) || AT.equals(text) ? JavaBreak.NONE : JavaBreak.UNSET;
  }

  /**
   * Returns the scope kind an opening token starts.
   *
   * @param atom the opening token
   * @param facts the parse-tree facts
   * @return the kind
   */
  static JavaFrame.Kind openKind(final JavaAtom atom, final JavaTreeFacts facts) {
    if (OPEN_BLOCK.equals(atom.text())) {
      return facts.arrayOpens().contains(atom.index())
          ? JavaFrame.Kind.GROUP
          : JavaFrame.Kind.BLOCK;
    }
    if (OPEN_PAREN.equals(atom.text()) && facts.groupOpens().contains(atom.index())) {
      return JavaFrame.Kind.GROUP;
    }
    return JavaFrame.Kind.RAW;
  }

  static String closingOf(final String open) {
    if (OPEN_BLOCK.equals(open)) {
      return CLOSE_BLOCK;
    }
    return OPEN_PAREN.equals(open) ? CLOSE_PAREN : CLOSE_BRACKET;
  }

  static boolean isOpen(final String text) {
    return OPEN_BLOCK.equals(text) || OPEN_PAREN.equals(text) || OPEN_BRACKET.equals(text);
  }

  static boolean isClose(final String text) {
    return CLOSE_BLOCK.equals(text) || CLOSE_PAREN.equals(text) || CLOSE_BRACKET.equals(text);
  }

  /** Tells whether a scope lays its statements out one per line. */
  static boolean isBlockLike(final JavaFrame.Kind kind) {
    return kind == JavaFrame.Kind.BLOCK
        || kind == JavaFrame.Kind.ROOT
        || kind == JavaFrame.Kind.CASE;
  }
}
