package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each class that declares at least the minimum number of public methods and fields, as
 * PMD's excessive public count does.
 */
public final class PublicCountCheck implements UnitCheck {

  private final int minimum;
  private final String message;

  /**
   * Creates the check.
   *
   * @param minimum the count from which a class is reported
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public PublicCountCheck(final int minimum, final String message) {
    this.minimum = CheckSupport.nonNegative(minimum, "minimum");
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
            .filter(PublicCountCheck::isPublicMethodOrField)
            .count();
    if (count < minimum) {
      return List.of();
    }
    return List.of(
        CheckSupport.at(
            clazz.span(),
            CheckSupport.fill(message, String.valueOf(count), String.valueOf(minimum))));
  }

  private static boolean isPublicMethodOrField(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(MemberDeclarations.PUBLIC)
        && MemberDeclarations.declaration(member)
            .map(declaration -> isMethodOrField(declaration.kind()))
            .orElse(false);
  }

  private static boolean isMethodOrField(final String kind) {
    return MemberDeclarations.METHOD.equals(kind) || MemberDeclarations.FIELD.equals(kind);
  }
}
