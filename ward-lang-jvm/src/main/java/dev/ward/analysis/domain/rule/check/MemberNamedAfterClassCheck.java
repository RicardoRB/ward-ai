package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a field or a method whose name equals, ignoring case, the name of its enclosing class, as
 * PMD's AvoidFieldNameMatchingTypeName and MethodWithSameNameAsEnclosingClass do. A constructor is
 * not a method, so it is never reported.
 */
public final class MemberNamedAfterClassCheck implements UnitCheck {

  /** The member kind that selects fields. */
  public static final String FIELD = "field";

  /** The member kind that selects methods. */
  public static final String METHOD = "method";

  private final boolean fields;
  private final String message;

  /**
   * Creates the check.
   *
   * @param member {@code field} or {@code method}
   * @param message the message reported at the member; {@code {value}} is the member name
   * @throws IllegalArgumentException if {@code member} is neither
   */
  public MemberNamedAfterClassCheck(final String member, final String message) {
    if (!FIELD.equals(member) && !METHOD.equals(member)) {
      throw new IllegalArgumentException("member must be field or method: " + member);
    }
    this.fields = FIELD.equals(member);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> reportsOf(clazz).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode clazz) {
    final String className = ClassMembers.nameOf(clazz);
    return ClassMembers.members(clazz).stream()
        .map(ClassMembers::declarationOf)
        .flatMap(declaration -> namesOf(declaration).stream().map(name -> pair(declaration, name)))
        .filter(pair -> pair.name().equalsIgnoreCase(className))
        .map(pair -> CheckSupport.at(pair.declaration().span(), fill(pair.name())))
        .toList();
  }

  private List<String> namesOf(final SyntaxNode declaration) {
    if (fields && MemberDeclarations.FIELD.equals(declaration.kind())) {
      return ClassMembers.fieldNames(declaration);
    }
    if (!fields && MemberDeclarations.METHOD.equals(declaration.kind())) {
      return List.of(MemberDeclarations.name(declaration));
    }
    return List.of();
  }

  private String fill(final String name) {
    return CheckSupport.fill(message, name, "");
  }

  private static Named pair(final SyntaxNode declaration, final String name) {
    return new Named(declaration, name);
  }

  private record Named(SyntaxNode declaration, String name) {}
}
