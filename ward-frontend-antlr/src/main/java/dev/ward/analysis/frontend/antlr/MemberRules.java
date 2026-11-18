package dev.ward.analysis.frontend.antlr;

import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * What {@link DeclarationText} needs to know about one language to find the blocks that are bodies
 * of functions, constructors, initializers and accessors. Not thread-safe: one instance per call.
 */
interface MemberRules {
  /** The role of a token in the block structure. */
  enum Kind {
    OPEN,
    TEMPLATE_OPEN,
    CLOSE,
    LEFT_PAREN,
    RIGHT_PAREN,
    END,
    NEWLINE,
    OTHER
  }

  /** What to do with the block a {@code {} opens. */
  enum Action {
    /** A type body: its members are read in turn. */
    TYPE,
    /** A body whose text the surface does not need: blank it. */
    STRIP,
    /** Anything else, such as an initializer: keep it as written. */
    KEEP
  }

  /** Returns the tokens of the text on the default channel, or empty if the text does not lex. */
  List<Token> lex(String path, String text);

  /** Returns the role of the token. */
  Kind kind(Token token);

  /**
   * Notes a token of the head of the current member, outside parentheses and blocks.
   *
   * @param token the token
   * @param previous the token before it, or null
   * @param firstOnLine whether it is the first token on its line
   */
  void see(Token token, Token previous, boolean firstOnLine);

  /**
   * Decides what a block that opens at the head of a member is.
   *
   * @param previous the token before the brace
   * @param insideType whether the member belongs to a type body, not to the file
   * @param enumConstants whether the member is in the constant list of an enum
   */
  Action open(Token previous, boolean insideType, boolean enumConstants);

  /** Returns whether the head of the current member declares an enum. */
  boolean enumHead();

  /** Starts a new member. */
  void reset();
}
