package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The token, call and number reading that the magic-number check shares. */
final class NodeMagicNumberSyntax {

  private static final String ASSIGN = "=";
  private static final String PARSE_INT = "parseInt";
  private static final String NUMBER_PARSE_INT = "Number.parseInt";
  private static final String LEGACY_OCTAL = "OctalIntegerLiteral2";
  private static final String UNDERSCORE = "_";
  private static final String PREFIX_HEX = "x";
  private static final String PREFIX_OCTAL = "o";
  private static final String PREFIX_BINARY = "b";
  private static final int HEX = 16;
  private static final int OCTAL = 8;
  private static final int BINARY = 2;
  private static final int PREFIX_LENGTH = 2;
  private static final long MAX_INDEX = 4_294_967_294L;

  private NodeMagicNumberSyntax() {}

  /** Whether the node is a token leaf with the given text. */
  static boolean isToken(final SyntaxNode node, final String token) {
    return isLeaf(node) && token.equals(NodeSyntax.text(node));
  }

  static boolean isLeaf(final SyntaxNode node) {
    return node.children().isEmpty();
  }

  /** The text of the child's previous sibling when that sibling is a token, or empty. */
  static String tokenBefore(final SyntaxNode parent, final SyntaxNode child) {
    final List<SyntaxNode> parts = parent.children();
    final int index = parts.indexOf(child);
    return index > 0 && isLeaf(parts.get(index - 1)) ? NodeSyntax.text(parts.get(index - 1)) : "";
  }

  /** The text of the first leaf under the node, or empty. */
  static String firstLeaf(final SyntaxNode node) {
    return NodeSyntax.leaves(node).stream().findFirst().orElse("");
  }

  /** Whether the call's callee is {@code parseInt} or {@code Number.parseInt}. */
  static boolean isParseIntCall(final SyntaxNode call) {
    final String callee = NodeSyntax.text(call.children().get(0));
    return PARSE_INT.equals(callee) || NUMBER_PARSE_INT.equals(callee);
  }

  /** Whether the token is the assignment sign that a default value follows. */
  static boolean isAssign(final String token) {
    return ASSIGN.equals(token);
  }

  /** Whether the value is an array index: an integer from 0 to 2 ** 32 - 2. */
  static boolean validIndex(final BigDecimal value) {
    return value.signum() >= 0
        && value.stripTrailingZeros().scale() <= 0
        && value.compareTo(BigDecimal.valueOf(MAX_INDEX)) <= 0;
  }

  /** The value of a literal as written, or empty when it is not a number this rule reads. */
  static Optional<BigDecimal> value(final String kind, final String written) {
    final String digits = written.replace(UNDERSCORE, "");
    try {
      return Optional.of(number(kind, digits));
    } catch (NumberFormatException malformed) {
      return Optional.empty();
    }
  }

  private static BigDecimal number(final String kind, final String digits) {
    if (digits.length() > PREFIX_LENGTH && digits.charAt(0) == '0') {
      final String prefix = digits.substring(1, 2).toLowerCase(Locale.ROOT);
      final String body = digits.substring(PREFIX_LENGTH);
      if (PREFIX_HEX.equals(prefix)) {
        return new BigDecimal(new BigInteger(body, HEX));
      }
      if (PREFIX_OCTAL.equals(prefix)) {
        return new BigDecimal(new BigInteger(body, OCTAL));
      }
      if (PREFIX_BINARY.equals(prefix)) {
        return new BigDecimal(new BigInteger(body, BINARY));
      }
    }
    if (LEGACY_OCTAL.equals(kind)) {
      return new BigDecimal(new BigInteger(digits, OCTAL));
    }
    return new BigDecimal(digits);
  }
}
