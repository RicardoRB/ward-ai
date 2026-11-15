package dev.ward.analysis.frontend.antlr.format.ecma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Finds, once per source, which brace opens a block, which bracket closes which opener, and which
 * bracket lists may break as a group.
 */
final class EcmaBrackets {

  private static final String COLON = ":";
  private static final String COMMA = ",";
  private static final String SEMICOLON = ";";
  private static final String OPEN_BRACE = "{";
  private static final String DEFAULT = "default";
  private static final String CASE = "case";
  private static final Set<String> BLOCK_PRECEDERS =
      Set.of(")", "=>", ";", "{", "}", "else", "try", "finally", "do", ">");

  private final List<EcmaToken> tokens;
  private final boolean[] blockOpen;
  private final boolean[] blockClose;
  private final int[] closer;
  private final int[] blockPrefix;
  private final int[] semicolonPrefix;

  /**
   * Analyzes the tokens once.
   *
   * @param tokens the layout tokens in order
   */
  EcmaBrackets(final List<EcmaToken> tokens) {
    this.tokens = List.copyOf(tokens);
    final int size = this.tokens.size();
    blockOpen = new boolean[size];
    blockClose = new boolean[size];
    closer = new int[size];
    Arrays.fill(closer, -1);
    blockPrefix = new int[size + 1];
    semicolonPrefix = new int[size + 1];
    analyze();
  }

  /**
   * Tells whether the token is a brace that opens a block.
   *
   * @param index the token index
   * @return true for a block's opening brace
   */
  boolean isBlockOpen(final int index) {
    return blockOpen[index];
  }

  /**
   * Tells whether the token is the brace that closes a block.
   *
   * @param index the token index
   * @return true for a block's closing brace
   */
  boolean isBlockClose(final int index) {
    return blockClose[index];
  }

  /**
   * Returns the index of the bracket that closes an opener.
   *
   * @param index the opener index
   * @return the closer index, or -1 when the token is not an opener with a closer
   */
  int closerOf(final int index) {
    return closer[index];
  }

  /**
   * Tells whether a bracket list can break as a group: it has content, no block and no semicolon,
   * and it does not only wrap one object or array, which breaks inside itself.
   *
   * @param open the opener index
   * @return true when the list is a group
   */
  boolean isGroup(final int open) {
    final int close = closer[open];
    return !blockOpen[open]
        && close > open + 1
        && blockPrefix[close] - blockPrefix[open + 1] == 0
        && semicolonPrefix[close] - semicolonPrefix[open + 1] == 0
        && !wrapsOneBracket(open, close);
  }

  private void analyze() {
    for (int index = 0; index < tokens.size(); index++) {
      blockOpen[index] = OPEN_BRACE.equals(text(index)) && isBlockBrace(index);
    }
    pairBrackets();
    for (int index = 0; index < tokens.size(); index++) {
      blockPrefix[index + 1] = blockPrefix[index] + (blockOpen[index] ? 1 : 0);
      semicolonPrefix[index + 1] = semicolonPrefix[index] + (SEMICOLON.equals(text(index)) ? 1 : 0);
      if (closer[index] >= 0 && blockOpen[index]) {
        blockClose[closer[index]] = true;
      }
    }
  }

  private void pairBrackets() {
    final List<Integer> open = new ArrayList<>();
    for (int index = 0; index < tokens.size(); index++) {
      final String text = text(index);
      if (EcmaText.isOpener(text)) {
        open.add(index);
      } else if (EcmaText.isCloser(text) && !open.isEmpty()) {
        closer[open.removeLast()] = index;
      }
    }
  }

  private boolean isBlockBrace(final int index) {
    final int prev = previousCode(index);
    if (prev < 0) {
      return true;
    }
    final String word = text(prev);
    if (COLON.equals(word)) {
      return !isPropertyColon(prev);
    }
    return BLOCK_PRECEDERS.contains(word)
        || isStringLiteral(word)
        || (EcmaText.isWord(word) && !EcmaText.OBJECT_KEYWORDS.contains(word));
  }

  /** A module name, as in {@code declare module "*.vue" {}}, precedes a block, never an object. */
  private static boolean isStringLiteral(final String word) {
    return word.startsWith("\"") || word.startsWith("'");
  }

  /** A colon after an object key, which starts an object as its value. */
  private boolean isPropertyColon(final int colon) {
    final int key = previousCode(colon);
    if (key < 0 || isSwitchLabel(text(key))) {
      return false;
    }
    final int before = previousCode(key);
    return before >= 0 && isPropertyStart(text(before));
  }

  private static boolean isSwitchLabel(final String word) {
    return DEFAULT.equals(word) || CASE.equals(word);
  }

  private static boolean isPropertyStart(final String word) {
    return OPEN_BRACE.equals(word) || COMMA.equals(word);
  }

  private boolean wrapsOneBracket(final int open, final int close) {
    final int inner = open + 1;
    return closer[inner] == close - 1
        && (OPEN_BRACE.equals(text(inner)) || "[".equals(text(inner)));
  }

  private int previousCode(final int index) {
    for (int candidate = index - 1; candidate >= 0; candidate--) {
      if (!tokens.get(candidate).comment()) {
        return candidate;
      }
    }
    return -1;
  }

  private String text(final int index) {
    return tokens.get(index).text();
  }
}
