package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each number literal that is a magic number, like ESLint's no-magic-numbers with its
 * default options. Property values and assignments to members are left alone unless {@code
 * detectObjects} is set. The initializer of a declaration is left alone; with {@code enforceConst}
 * only a number declared without {@code const} is reported, with its own message. The numbers 0, 1
 * and 2 are never reported, as in Checkstyle's defaults.
 */
public final class NodeMagicNumberCheck implements RuleCheck {

  private static final Set<String> LITERALS =
      Set.of(
          "DecimalLiteral",
          "HexIntegerLiteral",
          "OctalIntegerLiteral",
          "OctalIntegerLiteral2",
          "BinaryIntegerLiteral");
  private static final String LEGACY_OCTAL = "OctalIntegerLiteral2";
  private static final String PREFIX_HEX = "x";
  private static final String PREFIX_OCTAL = "o";
  private static final String PREFIX_BINARY = "b";
  private static final String UNDERSCORE = "_";
  private static final int HEX = 16;
  private static final int OCTAL = 8;
  private static final int BINARY = 2;
  private static final int PREFIX_LENGTH = 2;
  private static final long MAX_INDEX = 4_294_967_294L;
  private static final List<BigDecimal> BUILT_IN =
      List.of(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.valueOf(2));

  private final List<BigDecimal> ignore;
  private final NodeMagicNumberFlags flags;
  private final String message;
  private final String constMessage;

  /**
   * Creates the check.
   *
   * @param ignore the numbers that are never reported, besides 0, 1 and 2
   * @param flags the boolean options
   * @param message the message of a magic number; {@code {value}} is the number as written
   * @param constMessage the message of a number declared with {@code let} or {@code var} when
   *     {@code enforceConst} is set
   */
  public NodeMagicNumberCheck(
      final List<BigDecimal> ignore,
      final NodeMagicNumberFlags flags,
      final String message,
      final String constMessage) {
    this.ignore = List.copyOf(ignore);
    this.flags = flags;
    this.message = message;
    this.constMessage = constMessage;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final NodeMagicNumberRoles roles = new NodeMagicNumberRoles(root);
    final List<RuleMatch> found = new ArrayList<>();
    literals(root, found, roles);
    return List.copyOf(found);
  }

  private void literals(
      final SyntaxNode node, final List<RuleMatch> found, final NodeMagicNumberRoles roles) {
    if (LITERALS.contains(node.kind())) {
      literal(node, roles).ifPresent(found::add);
      return;
    }
    node.children().forEach(child -> literals(child, found, roles));
  }

  private Optional<RuleMatch> literal(final SyntaxNode leaf, final NodeMagicNumberRoles roles) {
    final Optional<BigDecimal> value = value(leaf.kind(), NodeSyntax.text(leaf));
    if (value.isEmpty()) {
      return Optional.empty();
    }
    final NodeMagicNumberRoles.Usage usage = roles.usage(leaf);
    final BigDecimal signed = usage.negative() ? value.get().negate() : value.get();
    final Optional<String> text = problem(usage, signed);
    final Span span = usage.negative() ? usage.node().span() : leaf.span();
    return text.map(reported -> new RuleMatch(span, reported));
  }

  /** The message of the usage when it is reported, empty when it is allowed. */
  private Optional<String> problem(final NodeMagicNumberRoles.Usage usage, final BigDecimal value) {
    if (ignored(value) || skippedRole(usage, value)) {
      return Optional.empty();
    }
    if (usage.role() == NodeMagicNumberRoles.Role.DECLARATOR) {
      return flags.enforceConst()
          ? Optional.of(constMessage).filter(text -> !usage.constant())
          : Optional.empty();
    }
    if (flags.detectObjects()) {
      return Optional.of(magic(usage));
    }
    return isObjectForm(usage.role()) ? Optional.empty() : Optional.of(magic(usage));
  }

  private boolean skippedRole(final NodeMagicNumberRoles.Usage usage, final BigDecimal value) {
    final NodeMagicNumberRoles.Role role = usage.role();
    return (flags.ignoreDefaultValues() && role == NodeMagicNumberRoles.Role.DEFAULT)
        || (flags.ignoreClassFieldInitialValues() && role == NodeMagicNumberRoles.Role.CLASS_FIELD)
        || (flags.ignoreArrayIndexes()
            && role == NodeMagicNumberRoles.Role.MEMBER_INDEX
            && validIndex(value));
  }

  private static boolean isObjectForm(final NodeMagicNumberRoles.Role role) {
    return role == NodeMagicNumberRoles.Role.PROPERTY
        || role == NodeMagicNumberRoles.Role.ASSIGNED_MEMBER;
  }

  private String magic(final NodeMagicNumberRoles.Usage usage) {
    return message.replace("{value}", NodeSyntax.text(usage.node()));
  }

  private boolean ignored(final BigDecimal value) {
    final BigDecimal magnitude = value.abs();
    return BUILT_IN.stream().anyMatch(number -> number.compareTo(magnitude) == 0)
        || ignore.stream().anyMatch(number -> number.compareTo(value) == 0);
  }

  private static boolean validIndex(final BigDecimal value) {
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
