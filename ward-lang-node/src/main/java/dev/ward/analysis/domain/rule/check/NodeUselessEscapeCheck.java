package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a backslash that escapes a character which needs no escape, like ESLint {@code
 * no-useless-escape}, in string literals, template literals and regular expression literals. The
 * valid escapes depend on the context, see {@link EscapeRules}.
 */
public final class NodeUselessEscapeCheck implements RuleCheck {

  private static final String STRING = "StringLiteral";
  private static final String REGEX = "RegularExpressionLiteral";
  private static final String TEMPLATE = "templateStringLiteral";
  private static final String ATOM = "TemplateStringAtom";
  private static final String ESCAPE_ATOM = "TemplateStringEscapeAtom";
  private static final String SUBSTITUTION = "TemplateStringStartExpression";
  private static final char BACKSLASH = '\\';
  private static final char SLASH = '/';
  private static final String VALUE = "{value}";
  private static final int ESCAPE_WIDTH = 2;

  private final EscapeRules rules;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowedRegexCharacters the characters whose escape a regular expression may keep
   * @param message the message, with {@code {value}} as the escaped character
   */
  public NodeUselessEscapeCheck(final Set<String> allowedRegexCharacters, final String message) {
    this.rules = new EscapeRules(allowedRegexCharacters);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (TEMPLATE.equals(node.kind())) {
      template(node, matches);
    } else if (STRING.equals(node.kind()) || REGEX.equals(node.kind())) {
      token(node, matches);
    } else {
      for (final SyntaxNode child : node.children()) {
        collect(child, matches);
      }
    }
  }

  /** The escapes of a string or regular expression token, read from its text. */
  private void token(final SyntaxNode token, final List<RuleMatch> matches) {
    final String text = LeafText.of(token);
    final boolean regex = REGEX.equals(token.kind());
    final char quote = text.charAt(0);
    final int bodyEnd = regex ? text.lastIndexOf(SLASH) : text.length() - 1;
    final int[] position = {token.span().startLine(), token.span().startColumn()};
    LeafText.advance(position, quote);
    boolean inClass = false;
    int index = 1;
    while (index < bodyEnd) {
      final char c = text.charAt(index);
      final int consumed = width(c, index, bodyEnd);
      final boolean escape = consumed == ESCAPE_WIDTH;
      if (escape && !rules.valid(text.charAt(index + 1), regex, inClass, quote)) {
        matches.add(report(token, position, index, text.charAt(index + 1)));
      }
      inClass = EscapeRules.classState(regex, escape, inClass, c);
      LeafText.advanceBy(position, text, index, consumed);
      index += consumed;
    }
  }

  /** The number of characters a step consumes: an escape and its character, or one character. */
  private static int width(final char c, final int index, final int bodyEnd) {
    return c == BACKSLASH && index + 1 < bodyEnd ? ESCAPE_WIDTH : 1;
  }

  /**
   * The escapes of a template literal, which is a run of one-character atoms with substitutions.
   */
  private void template(final SyntaxNode template, final List<RuleMatch> matches) {
    final StringBuilder text = new StringBuilder();
    final List<int[]> positions = new ArrayList<>();
    LeafText.leaves(template).forEach(atom -> appendAtom(atom, text, positions));
    int index = 0;
    while (index + 1 < text.length()) {
      if (text.charAt(index) == BACKSLASH) {
        final char next = text.charAt(index + 1);
        final boolean brace = index + 2 < text.length() && text.charAt(index + 2) == '{';
        if (!rules.template(next, brace)) {
          matches.add(new RuleMatch(span(positions.get(index)), message(next)));
        }
        index += 2;
      } else {
        index++;
      }
    }
  }

  /** Appends the characters of a literal or substitution atom, each with its own position. */
  private static void appendAtom(
      final SyntaxNode atom, final StringBuilder text, final List<int[]> positions) {
    if (!ATOM.equals(atom.kind())
        && !ESCAPE_ATOM.equals(atom.kind())
        && !SUBSTITUTION.equals(atom.kind())) {
      return;
    }
    final String value = LeafText.of(atom);
    for (int index = 0; index < value.length(); index++) {
      text.append(value.charAt(index));
      positions.add(
          new int[] {
            atom.span().startLine(),
            atom.span().startColumn() + index,
            atom.span().startOffset() + index
          });
    }
  }

  private RuleMatch report(
      final SyntaxNode token, final int[] position, final int index, final char next) {
    final int offset = token.span().startOffset() + index;
    return new RuleMatch(
        new Span(position[0], position[1], position[0], position[1] + 1, offset, offset + 1),
        message(next));
  }

  private static Span span(final int... position) {
    return new Span(
        position[0], position[1], position[0], position[1] + 1, position[2], position[2] + 1);
  }

  private String message(final char escaped) {
    return message.replace(VALUE, String.valueOf(escaped));
  }
}
