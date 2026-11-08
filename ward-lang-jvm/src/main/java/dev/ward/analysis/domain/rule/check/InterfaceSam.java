package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads the methods of an interface declaration: its single abstract method and its defaults. */
final class InterfaceSam {

  private static final String METHOD = "interfaceMethodDeclaration";
  private static final String MODIFIER = "interfaceMethodModifier";
  private static final Set<String> CONCRETE = Set.of("default", "static", "private");

  /**
   * An abstract method.
   *
   * @param name its name
   * @param arity its number of parameters
   */
  record Sam(String name, int arity) {}

  private InterfaceSam() {}

  /** Returns the methods declared in the body of {@code declaration}. */
  static List<SyntaxNode> methods(final SyntaxNode declaration) {
    return SyntaxWalk.descendants(declaration).stream()
        .filter(node -> METHOD.equals(node.kind()))
        .filter(node -> owns(declaration, node))
        .toList();
  }

  /** Tells whether the method sits directly in the body of {@code declaration}. */
  private static boolean owns(final SyntaxNode declaration, final SyntaxNode method) {
    return NullSyntax.first(declaration, "interfaceBody")
        .map(body -> SyntaxWalk.descendants(body).contains(method) && direct(body, method))
        .orElse(false);
  }

  private static boolean direct(final SyntaxNode body, final SyntaxNode method) {
    return body.children().stream()
        .filter(entry -> "interfaceBodyDeclaration".equals(entry.kind()))
        .flatMap(entry -> entry.children().stream())
        .filter(member -> "interfaceMemberDeclaration".equals(member.kind()))
        .flatMap(member -> member.children().stream())
        .anyMatch(method::equals);
  }

  /** Tells whether the method is declared {@code default}. */
  static boolean isDefault(final SyntaxNode method) {
    return modifiers(method).contains("default");
  }

  private static Set<String> modifiers(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> MODIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .collect(java.util.stream.Collectors.toSet());
  }

  /** Returns the only abstract method the interface body declares, if there is exactly one. */
  static Optional<Sam> only(final SyntaxNode declaration) {
    final List<Sam> abstracts =
        methods(declaration).stream()
            .filter(method -> modifiers(method).stream().noneMatch(CONCRETE::contains))
            .map(InterfaceSam::sam)
            .toList();
    return abstracts.size() == 1 ? Optional.of(abstracts.get(0)) : Optional.empty();
  }

  private static Sam sam(final SyntaxNode method) {
    final SyntaxNode common =
        NullSyntax.first(method, "interfaceCommonBodyDeclaration").orElse(method);
    final long arity =
        SyntaxWalk.descendants(common).stream()
            .filter(node -> "formalParameter".equals(node.kind()))
            .count();
    return new Sam(NullOperands.methodName(common), (int) arity);
  }

  /** Returns the name of a method declaration. */
  static String name(final SyntaxNode method) {
    return NullOperands.methodName(
        NullSyntax.first(method, "interfaceCommonBodyDeclaration").orElse(method));
  }
}
