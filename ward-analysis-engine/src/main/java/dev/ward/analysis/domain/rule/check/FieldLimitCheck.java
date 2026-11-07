package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each class that declares more fields than the limit, counting only fields that are
 * neither final nor static, as PMD's too-many-fields does.
 */
public final class FieldLimitCheck implements UnitCheck {

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest number of counted fields a class may declare
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public FieldLimitCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode clazz) {
    final long count =
        MemberDeclarations.body(clazz).stream()
            .flatMap(body -> MemberDeclarations.members(body).stream())
            .filter(FieldLimitCheck::isCountedField)
            .count();
    if (count <= max) {
      return List.of();
    }
    return List.of(
        CheckSupport.at(
            clazz.span(), CheckSupport.fill(message, String.valueOf(count), String.valueOf(max))));
  }

  private static boolean isCountedField(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return MemberDeclarations.declaration(member)
            .filter(declaration -> MemberDeclarations.FIELD.equals(declaration.kind()))
            .isPresent()
        && !modifiers.contains(MemberDeclarations.FINAL)
        && !modifiers.contains(MemberDeclarations.STATIC);
  }
}
