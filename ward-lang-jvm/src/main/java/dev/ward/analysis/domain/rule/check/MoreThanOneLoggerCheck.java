package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a class that declares more than one logger field, as PMD does: a field whose type is
 * named {@code Logger} or {@code Log}. A logger in a local variable does not count.
 */
public final class MoreThanOneLoggerCheck implements RuleCheck {
  private static final Set<String> LOGGER_TYPES = Set.of("Logger", "Log");
  private static final int ONE = 1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the class declaration
   */
  public MoreThanOneLoggerCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(clazz -> loggers(clazz) > ONE)
        .map(clazz -> CheckSupport.at(clazz.span(), message))
        .toList();
  }

  private static int loggers(final SyntaxNode clazz) {
    return ClassMembers.members(clazz).stream()
        .map(ClassMembers::declarationOf)
        .filter(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
        .filter(MoreThanOneLoggerCheck::isLogger)
        .mapToInt(field -> ClassMembers.fieldNames(field).size())
        .sum();
  }

  private static boolean isLogger(final SyntaxNode field) {
    return ClassMembers.typeText(field)
        .map(ClassMembers::simpleName)
        .filter(LOGGER_TYPES::contains)
        .isPresent();
  }
}
