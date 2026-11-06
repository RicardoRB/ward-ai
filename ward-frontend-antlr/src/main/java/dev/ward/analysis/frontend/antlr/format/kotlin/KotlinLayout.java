package dev.ward.analysis.frontend.antlr.format.kotlin;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Builds the {@link Document} of one Kotlin parse. Every gap between two significant tokens is kept
 * as a break or a space, never removed, so line breaks that Kotlin reads stay where they are.
 * Argument and parameter lists are the only place where a break may be added: they form a group
 * that breaks one item per line, with the continuation indent, when it does not fit.
 */
final class KotlinLayout {

  private static final String SPACE = " ";
  private static final char NEWLINE = '\n';
  private static final int OUTER_CONTRIBUTION = 1;
  private static final int NO_END = Integer.MAX_VALUE;

  private final KotlinParse parse;
  private final int[] codePoints;
  private final int maxBlankLines;
  private final int continuationLevels;
  private final Set<Integer> argumentLists;
  private final Map<Integer, Integer> continuations;
  private final Set<Integer> superColons;
  private final Deque<List<Document>> lists = new ArrayDeque<>();
  private final Deque<Opener> openers = new ArrayDeque<>();
  private int level;
  private Token before;

  /**
   * Creates a layout for one parse.
   *
   * @param parse the parse of the source
   * @param maxBlankLines the most blank lines kept in a row
   * @param continuationLevels the indent levels a continuation adds: an argument list, or an
   *     expression that starts a line after an assignment sign
   */
  KotlinLayout(final KotlinParse parse, final int maxBlankLines, final int continuationLevels) {
    this.parse = parse;
    this.codePoints = parse.codePoints();
    this.maxBlankLines = maxBlankLines;
    this.continuationLevels = continuationLevels;
    this.argumentLists = parse.walk().argumentLists();
    this.continuations = parse.walk().continuations();
    this.superColons = parse.walk().superColons();
  }

  /**
   * Builds the document.
   *
   * @return the document, ending with a line break when it has any token
   */
  Document build() {
    final List<Token> items = parse.items();
    lists.push(new ArrayList<>());
    for (int i = 0; i < items.size(); i++) {
      before = i < 2 ? null : items.get(i - 2);
      place(i == 0 ? null : items.get(i - 1), items.get(i));
    }
    if (!items.isEmpty()) {
      lists.peek().add(Document.hardLine());
    }
    return Document.concat(lists.peek().toArray(Document[]::new));
  }

  private void place(final Token prev, final Token token) {
    expireContinuations(token.getTokenIndex());
    if (prev != null) {
      startContinuation(prev, token);
    }
    if (KotlinTokens.isCloser(token) && !openers.isEmpty()) {
      closeBracket(prev, token);
      return;
    }
    separate(prev, token, level, !openers.isEmpty() && openers.peek().group());
    lists.peek().add(Document.text(KotlinTokens.text(token)));
    if (KotlinTokens.isOpener(token)) {
      openBracket(token);
    }
  }

  /** Ends the continuations whose expression ended before this token. */
  private void expireContinuations(final int index) {
    while (!openers.isEmpty() && openers.peek().until() < index) {
      level -= openers.pop().contribution();
    }
  }

  private void startContinuation(final Token prev, final Token token) {
    final Integer end = continuations.get(prev.getTokenIndex());
    if (end == null || KotlinTokens.gap(codePoints, prev, token).indexOf(NEWLINE) < 0) {
      return;
    }
    openers.push(new Opener(continuationLevels, false, end));
    level += continuationLevels;
  }

  private void closeBracket(final Token prev, final Token token) {
    final Opener top = openers.pop();
    level -= top.contribution();
    if (prev != null) {
      separate(prev, token, level, top.group());
    }
    if (top.group()) {
      final List<Document> body = lists.pop();
      lists.peek().add(Document.group(Document.concat(body.toArray(Document[]::new))));
    }
    lists.peek().add(Document.text(KotlinTokens.text(token)));
  }

  private void openBracket(final Token token) {
    final boolean group = argumentLists.contains(token.getTokenIndex());
    final int contribution = group ? continuationLevels : OUTER_CONTRIBUTION;
    openers.push(new Opener(contribution, group, NO_END));
    level += contribution;
    if (group) {
      lists.push(new ArrayList<>());
    }
  }

  private void separate(final Token prev, final Token next, final int depth, final boolean group) {
    if (prev == null) {
      return;
    }
    if (KotlinTokens.isVerbatim(prev, next)) {
      final String gap = KotlinTokens.gap(codePoints, prev, next);
      if (!gap.isEmpty()) {
        lists.peek().add(Document.text(gap));
      }
    } else if (group) {
      separateInArguments(prev, next, depth);
    } else {
      separateNormally(prev, next, depth);
    }
  }

  /**
   * Breaks inside an argument list: after its parenthesis and after each comma, and before its
   * closing parenthesis, so that the group can break one item per line. Other gaps keep their
   * newline as a space, which Kotlin accepts inside parentheses.
   */
  private void separateInArguments(final Token prev, final Token next, final int depth) {
    if (KotlinTokens.isLineComment(prev)) {
      lists.peek().add(indentTo(depth, Document.hardLine()));
    } else if (KotlinTokens.isOpener(prev)
        || KotlinTokens.isComma(prev)
        || KotlinTokens.isCloser(next)) {
      if (KotlinTokens.isOpener(prev) && KotlinTokens.isCloser(next)) {
        return;
      }
      final boolean flat = KotlinTokens.isComma(prev) && !KotlinTokens.isCloser(next);
      lists.peek().add(indentTo(depth, flat ? Document.line() : Document.softLine()));
    } else {
      addSpacing(prev, next);
    }
  }

  private void separateNormally(final Token prev, final Token next, final int depth) {
    final String gap = KotlinTokens.gap(codePoints, prev, next);
    final int newlines = (int) gap.chars().filter(c -> c == NEWLINE).count();
    if (newlines == 0) {
      addSpacing(prev, next);
      return;
    }
    final int blankLines = Math.min(newlines - 1, maxBlankLines);
    for (int i = 0; i < blankLines; i++) {
      lists.peek().add(Document.hardLine());
    }
    lists.peek().add(indentTo(depth, Document.hardLine()));
  }

  /** Adds the space the token pair asks for; a kept space is one space, and no gap stays none. */
  private void addSpacing(final Token prev, final Token next) {
    final KotlinTokens.Spacing spacing =
        superColons.contains(next.getTokenIndex())
            ? KotlinTokens.Spacing.SPACE
            : KotlinTokens.spacing(before, prev, next);
    final boolean kept =
        spacing == KotlinTokens.Spacing.KEEP && !KotlinTokens.gap(codePoints, prev, next).isEmpty();
    if (spacing == KotlinTokens.Spacing.SPACE || kept) {
      lists.peek().add(Document.text(SPACE));
    }
  }

  private static Document indentTo(final int depth, final Document line) {
    Document indented = line;
    for (int i = 0; i < depth; i++) {
      indented = Document.indent(indented);
    }
    return indented;
  }

  /**
   * An open bracket or continuation: the levels it adds, whether it is an argument list group, and
   * the last token index it covers (a bracket covers until it closes).
   */
  private record Opener(int contribution, boolean group, int until) {}
}
