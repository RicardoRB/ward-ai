package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each interface with exactly one abstract method that lacks {@code @FunctionalInterface}.
 * Default and static methods do not count as abstract.
 */
public final class ImplicitFunctionalInterfaceCheck implements UnitCheck {

  private static final String ANNOTATION = "FunctionalInterface";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the interface declaration
   */
  public ImplicitFunctionalInterfaceCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("typeDeclaration", "classBodyDeclaration")).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(wrapper -> !annotated(wrapper))
        .flatMap(wrapper -> interfaceOf(wrapper).stream())
        .filter(declaration -> abstractMethods(declaration) == 1)
        .map(declaration -> CheckSupport.at(declaration.span(), message))
        .toList();
  }

  private static boolean annotated(final SyntaxNode wrapper) {
    return wrapper.children().stream()
        .filter(
            c -> !"interfaceDeclaration".equals(c.kind()) && !"memberDeclaration".equals(c.kind()))
        .flatMap(c -> SyntaxWalk.descendants(c).stream())
        .anyMatch(
            n -> "annotation".equals(n.kind()) && ANNOTATION.equals(simple(SyntaxWalk.text(n))));
  }

  private static String simple(final String annotation) {
    final String name = annotation.startsWith("@") ? annotation.substring(1) : annotation;
    final int arguments = name.indexOf('(');
    final String bare = arguments < 0 ? name : name.substring(0, arguments);
    return bare.substring(bare.lastIndexOf('.') + 1);
  }

  private static java.util.Optional<SyntaxNode> interfaceOf(final SyntaxNode wrapper) {
    for (final SyntaxNode child : wrapper.children()) {
      if ("interfaceDeclaration".equals(child.kind())) {
        return java.util.Optional.of(child);
      }
      if ("memberDeclaration".equals(child.kind())) {
        return child.children().stream()
            .filter(c -> "interfaceDeclaration".equals(c.kind()))
            .findFirst();
      }
    }
    return java.util.Optional.empty();
  }

  private static long abstractMethods(final SyntaxNode declaration) {
    return SyntaxWalk.descendants(declaration).stream()
        .filter(
            n ->
                "interfaceMethodDeclaration".equals(n.kind())
                    || "genericInterfaceMethodDeclaration".equals(n.kind()))
        .filter(n -> !isDefaultOrStatic(n))
        .count();
  }

  private static boolean isDefaultOrStatic(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .anyMatch(n -> "'default'".equals(n.kind()) || "'static'".equals(n.kind()));
  }
}
