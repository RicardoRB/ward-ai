package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a class that declares a {@code transient} field itself although it has no way to be
 * Serializable: no {@code extends} clause, whose superclass may be Serializable, and no listed
 * {@code Serializable} or {@code Externalizable}. A transient field of a nested class is that
 * class's own business.
 */
public final class TransientInPlainClassCheck implements UnitCheck {
  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public TransientInPlainClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(clazz -> isPlain(clazz) && declaresTransient(clazz))
        .map(clazz -> CheckSupport.at(clazz.span(), CheckSupport.fill(message, "", "")))
        .toList();
  }

  private static boolean isPlain(final SyntaxNode clazz) {
    final String header =
        clazz.children().stream()
            .filter(child -> !"classBody".equals(child.kind()))
            .map(child -> child.kind() + ":" + SyntaxWalk.text(child))
            .collect(Collectors.joining(" "));
    return !header.contains("'extends'")
        && !header.contains("Serializable")
        && !header.contains("Externalizable");
  }

  private static boolean declaresTransient(final SyntaxNode clazz) {
    return ClassMembers.fieldMembers(clazz).stream()
        .anyMatch(member -> MemberDeclarations.modifiers(member).contains("transient"));
  }
}
