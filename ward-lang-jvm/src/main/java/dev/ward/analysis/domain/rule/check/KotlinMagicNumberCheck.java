package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each numeric literal other than -1, 0, 1 and 2, as Checkstyle's MagicNumber does with its
 * default ignored numbers. Literals in a {@code const val}, in an annotation and in {@code
 * hashCode} are not reported.
 *
 * <p>Shortcut: a sign is only read from a unary minus that applies to the literal alone, so {@code
 * -(5)} is reported as {@code 5}.
 */
public final class KotlinMagicNumberCheck implements UnitCheck {

  private static final Set<String> LITERALS =
      Set.of("IntegerLiteral", "LongLiteral", "HexLiteral", "BinLiteral", "RealLiteral");
  private static final Set<BigDecimal> IGNORED =
      Set.of(BigDecimal.valueOf(-1), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.valueOf(2));
  private static final String CONST = "const";
  private static final String ANNOTATION = "annotation";
  private static final String PROPERTY = "propertyDeclaration";
  private static final String FUNCTION = "functionDeclaration";
  private static final String MODIFIERS = "modifierList";
  private static final String HASH_CODE = "hashCode";
  private static final String PREFIX = "prefixUnaryExpression";
  private static final String MINUS = "-";
  private static final String HEX = "0x";
  private static final String BINARY = "0b";
  private static final String UNDERSCORE = "_";
  private static final String SUFFIXES = "lLfFdD";
  private static final String VALUE = "{value}";
  private static final int PREFIX_LENGTH = 2;
  private static final int RADIX_HEX = 16;
  private static final int RADIX_BINARY = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the literal
   */
  public KotlinMagicNumberCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    visit(root, false, matches);
    return List.copyOf(matches);
  }

  private void visit(final SyntaxNode node, final boolean exempt, final List<RuleMatch> matches) {
    final boolean inside = exempt || isExempt(node);
    if (PREFIX.equals(node.kind()) && isSignedLiteral(node)) {
      final SyntaxNode literal = SyntaxWalk.leaves(node).get(1);
      report(literal, MINUS + KotlinDocs.text(literal), inside, matches);
    } else if (node.children().isEmpty()) {
      if (LITERALS.contains(node.kind())) {
        report(node, KotlinDocs.text(node), inside, matches);
      }
    } else {
      node.children().forEach(child -> visit(child, inside, matches));
    }
  }

  private void report(
      final SyntaxNode literal,
      final String text,
      final boolean exempt,
      final List<RuleMatch> matches) {
    if (!exempt && isMagic(text)) {
      matches.add(CheckSupport.at(literal.span(), message.replace(VALUE, text)));
    }
  }

  private static boolean isExempt(final SyntaxNode node) {
    if (ANNOTATION.equals(node.kind())) {
      return true;
    }
    if (PROPERTY.equals(node.kind())) {
      return KotlinDocs.childrenOf(node, MODIFIERS).stream()
          .flatMap(modifiers -> SyntaxWalk.leaves(modifiers).stream())
          .anyMatch(leaf -> CONST.equals(KotlinDocs.text(leaf)));
    }
    return FUNCTION.equals(node.kind()) && isHashCode(node);
  }

  private static boolean isHashCode(final SyntaxNode function) {
    return SyntaxWalk.leaves(function).stream()
        .anyMatch(leaf -> HASH_CODE.equals(KotlinDocs.text(leaf)));
  }

  private static boolean isSignedLiteral(final SyntaxNode prefix) {
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(prefix);
    return leaves.size() == 2
        && MINUS.equals(KotlinDocs.text(leaves.get(0)))
        && LITERALS.contains(leaves.get(1).kind());
  }

  private static boolean isMagic(final String text) {
    final BigDecimal value = value(text);
    return value != null && !IGNORED.contains(value.stripTrailingZeros());
  }

  /** Returns the numeric value of a literal with its sign, or null when its form is unknown. */
  private static BigDecimal value(final String literal) {
    if (literal.startsWith(MINUS)) {
      final BigDecimal magnitude = value(literal.substring(MINUS.length()));
      return magnitude == null ? null : magnitude.negate();
    }
    final String text = literal.replace(UNDERSCORE, "");
    try {
      if (text.regionMatches(true, 0, HEX, 0, PREFIX_LENGTH)) {
        return integer(text.substring(PREFIX_LENGTH), RADIX_HEX);
      }
      if (text.regionMatches(true, 0, BINARY, 0, PREFIX_LENGTH)) {
        return integer(text.substring(PREFIX_LENGTH), RADIX_BINARY);
      }
      return new BigDecimal(stripSuffix(text));
    } catch (NumberFormatException malformed) {
      return null;
    }
  }

  /** Parses the digits of a hexadecimal or binary literal, whose letters are not suffixes. */
  private static BigDecimal integer(final String digits, final int radix) {
    final String body =
        digits.endsWith("L") || digits.endsWith("l")
            ? digits.substring(0, digits.length() - 1)
            : digits;
    return new BigDecimal(new BigInteger(body, radix));
  }

  private static String stripSuffix(final String text) {
    final int last = text.length() - 1;
    return last >= 0 && SUFFIXES.indexOf(text.charAt(last)) >= 0 ? text.substring(0, last) : text;
  }
}
