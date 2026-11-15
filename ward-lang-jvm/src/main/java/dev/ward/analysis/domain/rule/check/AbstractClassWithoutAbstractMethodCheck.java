package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each abstract class that declares no abstract method and has no superclass or interface.
 * The abstract modifier sits on the declaration wrapper, either a top-level {@code typeDeclaration}
 * or a member's {@code classBodyDeclaration}.
 */
public final class AbstractClassWithoutAbstractMethodCheck implements UnitCheck {

  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final Set<String> HIERARCHY = Set.of("extends", "implements");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the class declaration
   */
  public AbstractClassWithoutAbstractMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("typeDeclaration", "classBodyDeclaration")).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(wrapper -> hasChildText(wrapper, "abstract"))
        .flatMap(wrapper -> classOf(wrapper).stream())
        .filter(clazz -> !hasSuperType(clazz) && !hasAbstractMethod(clazz))
        .map(clazz -> CheckSupport.at(clazz.span(), message))
        .toList();
  }

  private static boolean hasChildText(final SyntaxNode wrapper, final String text) {
    return wrapper.children().stream().anyMatch(c -> text.equals(SyntaxWalk.text(c)));
  }

  private static java.util.Optional<SyntaxNode> classOf(final SyntaxNode wrapper) {
    for (final SyntaxNode child : wrapper.children()) {
      if (CLASS_DECLARATION.equals(child.kind())) {
        return java.util.Optional.of(child);
      }
      if (MEMBER_DECLARATION.equals(child.kind())) {
        return child.children().stream()
            .filter(c -> CLASS_DECLARATION.equals(c.kind()))
            .findFirst();
      }
    }
    return java.util.Optional.empty();
  }

  /** PMD leaves a class that extends or implements a type, which may leave the methods open. */
  private static boolean hasSuperType(final SyntaxNode clazz) {
    return clazz.children().stream().anyMatch(child -> HIERARCHY.contains(SyntaxWalk.text(child)));
  }

  private static boolean hasAbstractMethod(final SyntaxNode clazz) {
    return JUnitMembers.body(clazz).stream()
        .flatMap(body -> JUnitMembers.members(body).stream())
        .anyMatch(member -> hasChildText(member, "abstract"));
  }
}
