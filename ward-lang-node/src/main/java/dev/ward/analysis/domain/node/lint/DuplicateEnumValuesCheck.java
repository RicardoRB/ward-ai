package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reports an enum member whose literal value an earlier member of the same enum already has. */
public final class DuplicateEnumValuesCheck implements RuleCheck {

  private static final String ENUM_MEMBER = "enumMember";
  private static final String ENUM_BODY = "enumBody";
  private static final String EXPRESSION = "singleExpression";
  private static final String MINUS = "-";
  private static final String PLUS = "+";
  private static final String VALUE = "{value}";
  private static final String NUMBER = "number:";
  private static final String STRING = "string:";
  private static final int MIN_QUOTED = 2;

  private final String message;

  /** Creates the check with the message of the rule; {@code {value}} is the repeated value. */
  public DuplicateEnumValuesCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode body : SyntaxText.nodesOf(root, ENUM_BODY)) {
      checkBody(out, body);
    }
    return out;
  }

  private void checkBody(final List<RuleMatch> out, final SyntaxNode body) {
    final Set<String> seen = new HashSet<>();
    for (final SyntaxNode member : SyntaxText.nodesOf(body, ENUM_MEMBER)) {
      final SyntaxNode init = initializerOf(member);
      final String value = init == null ? null : valueOf(init);
      if (value != null && !seen.add(value)) {
        out.add(new RuleMatch(member.span(), message.replace(VALUE, display(value))));
      }
    }
  }

  private static SyntaxNode initializerOf(final SyntaxNode member) {
    final List<SyntaxNode> parts = member.children();
    return parts.size() == SyntaxText.INFIX_PARTS && EXPRESSION.equals(parts.get(2).kind())
        ? parts.get(2)
        : null;
  }

  /** Returns the canonical value of a static initializer, or null when it is not static. */
  private static String valueOf(final SyntaxNode init) {
    final String text = SyntaxText.of(init);
    if (text.length() >= MIN_QUOTED && isQuoted(text)) {
      return STRING + text.substring(1, text.length() - 1);
    }
    return numberOf(init, text);
  }

  private static String numberOf(final SyntaxNode init, final String text) {
    final List<SyntaxNode> parts = init.children();
    if (parts.size() == 2 && isSign(parts.get(0))) {
      final SyntaxNode operand = parts.get(1);
      final String inner = numberOf(operand, SyntaxText.of(operand));
      return inner == null ? null : sign(parts.get(0), inner);
    }
    try {
      return NUMBER + new BigDecimal(text.replace("_", "")).stripTrailingZeros().toPlainString();
    } catch (final NumberFormatException exception) {
      return null;
    }
  }

  private static String display(final String value) {
    return value.startsWith(NUMBER)
        ? value.substring(NUMBER.length())
        : value.substring(STRING.length());
  }

  private static boolean isSign(final SyntaxNode node) {
    return SyntaxText.isToken(node, MINUS) || SyntaxText.isToken(node, PLUS);
  }

  private static String sign(final SyntaxNode operator, final String number) {
    final String value = number.substring(NUMBER.length());
    return NUMBER + (SyntaxText.isToken(operator, MINUS) ? "-" : "") + value;
  }

  private static boolean isQuoted(final String text) {
    final char first = text.charAt(0);
    return (first == '\'' || first == '"') && text.charAt(text.length() - 1) == first;
  }
}
