package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each interface with exactly one abstract method that lacks {@code @FunctionalInterface}.
 * Default, static and private methods do not count as abstract, and an interface that extends
 * another one is left alone, as the methods it inherits are unknown.
 */
public final class ImplicitFunctionalInterfaceCheck implements UnitCheck {

  private static final String INTERFACE_DECLARATION = "interfaceDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";

  private static final String ANNOTATION = "FunctionalInterface";
  private static final String EXTENDS = "extends";
  private static final Set<String> METHODS =
      Set.of("interfaceMethodDeclaration", "genericInterfaceMethodDeclaration");
  private static final List<String> CONCRETE = List.of("default", "static", "private");

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
        .filter(declaration -> !extendsInterface(declaration) && abstractMethods(declaration) == 1)
        .map(declaration -> CheckSupport.at(declaration.span(), message))
        .toList();
  }

  private static boolean annotated(final SyntaxNode wrapper) {
    return wrapper.children().stream()
        .filter(
            c -> !INTERFACE_DECLARATION.equals(c.kind()) && !MEMBER_DECLARATION.equals(c.kind()))
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
      if (INTERFACE_DECLARATION.equals(child.kind())) {
        return java.util.Optional.of(child);
      }
      if (MEMBER_DECLARATION.equals(child.kind())) {
        return child.children().stream()
            .filter(c -> INTERFACE_DECLARATION.equals(c.kind()))
            .findFirst();
      }
    }
    return java.util.Optional.empty();
  }

  /** Without types the inherited methods are unknown, so an interface that extends one is left. */
  private static boolean extendsInterface(final SyntaxNode declaration) {
    return declaration.children().stream().anyMatch(c -> EXTENDS.equals(SyntaxWalk.text(c)));
  }

  private static long abstractMethods(final SyntaxNode declaration) {
    return DesignMembers.body(declaration).map(DesignMembers::members).orElse(List.of()).stream()
        .filter(member -> METHODS.contains(member.declaration().kind()))
        .filter(member -> !isConcrete(member))
        .count();
  }

  /** A default, static or private method has a body; the modifier sits on the member or method. */
  private static boolean isConcrete(final DesignMembers.Member member) {
    return CONCRETE.stream().anyMatch(member::has)
        || SyntaxWalk.descendants(member.declaration()).stream()
            .anyMatch(n -> CONCRETE.stream().anyMatch(word -> ("'" + word + "'").equals(n.kind())));
  }
}
