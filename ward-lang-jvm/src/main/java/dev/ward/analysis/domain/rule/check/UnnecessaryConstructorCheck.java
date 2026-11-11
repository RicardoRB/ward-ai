package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports the only constructor of a class when it is the one the compiler would generate, as PMD's
 * {@code UnnecessaryConstructor} does: no parameters, no annotations, no {@code throws}, a body
 * that is empty or only {@code super();}, and the access modifier of the class. An enum's default
 * constructor is private.
 */
public final class UnnecessaryConstructorCheck implements UnitCheck {
  private static final String ENUM = "enumDeclaration";
  private static final Set<String> ACCESS = Set.of("public", "protected", "private");
  private static final Set<String> BODIES = Set.of("{}", "{super();}");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the constructor
   */
  public UnnecessaryConstructorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS, ENUM)).stream()
        .flatMap(
            visit ->
                body(visit.node()).stream()
                    .flatMap(body -> only(body).stream())
                    .filter(member -> isDefault(member, visit))
                    .map(member -> CheckSupport.at(member.span(), message)))
        .toList();
  }

  private static Optional<SyntaxNode> body(final SyntaxNode type) {
    return type.children().stream()
        .filter(
            child ->
                "classBody".equals(child.kind()) || "enumBodyDeclarations".equals(child.kind()))
        .findFirst();
  }

  /** The member that declares the only constructor of the body, if there is exactly one. */
  private static Optional<SyntaxNode> only(final SyntaxNode body) {
    final List<SyntaxNode> constructors =
        SyntaxWalk.descendants(body).stream()
            .filter(node -> "classBodyDeclaration".equals(node.kind()))
            .filter(node -> isConstructorOf(node, body))
            .toList();
    return constructors.size() == 1 ? Optional.of(constructors.get(0)) : Optional.empty();
  }

  private static boolean isConstructorOf(final SyntaxNode member, final SyntaxNode body) {
    return body.children().contains(member)
        && MemberDeclarations.declaration(member)
            .filter(d -> MemberDeclarations.CONSTRUCTOR.equals(d.kind()))
            .isPresent();
  }

  private static boolean isDefault(final SyntaxNode member, final SyntaxWalk.Visit type) {
    final SyntaxNode constructor = MemberDeclarations.declaration(member).orElseThrow();
    final String text = SyntaxWalk.text(constructor);
    final String rest = text.substring(text.indexOf('('));
    return rest.startsWith("()")
        && BODIES.contains(rest.substring(2))
        && JUnitMembers.annotations(member).isEmpty()
        && accessMatches(member, type);
  }

  private static boolean accessMatches(final SyntaxNode member, final SyntaxWalk.Visit type) {
    final String own = access(MemberDeclarations.modifiers(member));
    if (ENUM.equals(type.node().kind())) {
      return own.isEmpty() || "private".equals(own);
    }
    return own.equals(
        access(
            MemberDeclarations.wrapper(type.ancestors())
                .map(MemberDeclarations::modifiers)
                .orElse(List.of())));
  }

  private static String access(final List<String> modifiers) {
    return modifiers.stream().filter(ACCESS::contains).findFirst().orElse("");
  }
}
