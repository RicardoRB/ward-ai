package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Reports each numeric literal that is not one of the ignored numbers and is not the value of a
 * constant, as Checkstyle's MagicNumber does. The user options are ignore-numbers, whether numbers
 * in field declarations, annotations and hashCode methods are ignored.
 *
 * <p>shortcut: the sign of a literal is not read, so an ignored number also ignores its negation;
 * upgrade when unary minus is read from the tree.
 */
public final class MagicNumberCheck implements UnitCheck {

  private static final Set<String> LITERALS =
      Set.of(
          "DECIMAL_LITERAL",
          "HEX_LITERAL",
          "OCT_LITERAL",
          "BINARY_LITERAL",
          "FLOAT_LITERAL",
          "HEX_FLOAT_LITERAL");
  private static final String CONSTANT = "constDeclaration";
  private static final String FIELD = "fieldDeclaration";
  private static final String MEMBER = "classBodyDeclaration";
  private static final String ANNOTATION = "annotation";
  private static final String METHOD = "methodDeclaration";
  private static final String HASH_CODE = "hashCode";
  private static final int RADIX_HEX = 16;
  private static final int RADIX_BINARY = 2;
  private static final int RADIX_OCTAL = 8;

  private final Set<Double> ignored;
  private final boolean ignoreFieldDeclaration;
  private final boolean ignoreAnnotation;
  private final boolean ignoreHashCodeMethod;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoredNumbers the numbers that are never magic, compared by their absolute value
   * @param ignoreFieldDeclaration whether numbers in field declarations are ignored
   * @param ignoreAnnotation whether numbers in annotations are ignored
   * @param ignoreHashCodeMethod whether numbers in hashCode methods are ignored
   * @param message the message; {@code {value}} is the literal text
   * @throws IllegalArgumentException if an ignored number is not a number
   */
  public MagicNumberCheck(
      final List<String> ignoredNumbers,
      final boolean ignoreFieldDeclaration,
      final boolean ignoreAnnotation,
      final boolean ignoreHashCodeMethod,
      final String message) {
    this.ignored = parseAll(ignoredNumbers);
    this.ignoreFieldDeclaration = ignoreFieldDeclaration;
    this.ignoreAnnotation = ignoreAnnotation;
    this.ignoreHashCodeMethod = ignoreHashCodeMethod;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LITERALS)) {
      if (magic(visit)) {
        final String text = SyntaxWalk.text(visit.node());
        matches.add(CheckSupport.at(visit.node().span(), CheckSupport.fill(message, text, "")));
      }
    }
    return List.copyOf(matches);
  }

  private boolean magic(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (ignoredNumber(SyntaxWalk.text(visit.node()))) {
      return false;
    }
    if (up.stream().anyMatch(node -> CONSTANT.equals(node.kind()) || constantField(node))) {
      return false;
    }
    return !(ignoreFieldDeclaration && up.stream().anyMatch(n -> FIELD.equals(n.kind())))
        && !(ignoreAnnotation && up.stream().anyMatch(n -> ANNOTATION.equals(n.kind())))
        && !(ignoreHashCodeMethod && up.stream().anyMatch(MagicNumberCheck::isHashCode));
  }

  private boolean ignoredNumber(final String text) {
    final OptionalDouble value = value(text);
    return value.isPresent() && ignored.contains(Math.abs(value.getAsDouble()));
  }

  private static boolean constantField(final SyntaxNode node) {
    if (!MEMBER.equals(node.kind())) {
      return false;
    }
    final List<String> modifiers =
        node.children().stream()
            .filter(child -> "modifier".equals(child.kind()))
            .map(SyntaxWalk::text)
            .toList();
    return modifiers.contains("static") && modifiers.contains("final");
  }

  private static boolean isHashCode(final SyntaxNode node) {
    return METHOD.equals(node.kind())
        && SyntaxWalk.firstDescendant(
                node, "identifier", Set.of("typeTypeOrVoid", "formalParameters"))
            .map(SyntaxWalk::text)
            .filter(HASH_CODE::equals)
            .isPresent();
  }

  private static Set<Double> parseAll(final List<String> numbers) {
    final Set<Double> values = new HashSet<>();
    for (final String number : numbers) {
      final OptionalDouble value = value(number);
      if (value.isEmpty()) {
        throw new IllegalArgumentException("'" + number + "' is not a number");
      }
      values.add(Math.abs(value.getAsDouble()));
    }
    return Set.copyOf(values);
  }

  /** The value of a literal or a number written in source, or none when it is not one. */
  static OptionalDouble value(final String literal) {
    final String digits = literal.replace("_", "").replaceAll("[lLfFdD]$", "");
    try {
      if (digits.startsWith("0x") || digits.startsWith("0X")) {
        return OptionalDouble.of(Long.parseLong(digits.substring(2), RADIX_HEX));
      }
      if (digits.startsWith("0b") || digits.startsWith("0B")) {
        return OptionalDouble.of(Long.parseLong(digits.substring(2), RADIX_BINARY));
      }
      if (digits.length() > 1 && digits.startsWith("0") && !digits.contains(".")) {
        return OptionalDouble.of(Long.parseLong(digits.substring(1), RADIX_OCTAL));
      }
      return OptionalDouble.of(Double.parseDouble(digits));
    } catch (final NumberFormatException exception) {
      return OptionalDouble.empty();
    }
  }
}
