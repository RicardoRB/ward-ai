package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the constructors of a class body for the check of fields that must not be null. */
final class ConstructorInitialization {

  private static final String MEMBER = "memberDeclaration";
  private static final String BLOCK = "block";

  private ConstructorInitialization() {}

  static List<SyntaxNode> constructors(final List<SyntaxNode> entries) {
    return entries.stream()
        .flatMap(entry -> entry.children().stream())
        .filter(child -> MEMBER.equals(child.kind()))
        .flatMap(member -> member.children().stream())
        .filter(child -> "constructorDeclaration".equals(child.kind()))
        .toList();
  }

  static boolean delegates(final SyntaxNode constructor) {
    return NullSyntax.first(constructor, BLOCK)
        .map(block -> SyntaxWalk.text(block).startsWith("{this("))
        .orElse(false);
  }

  static boolean assignsAll(final SyntaxNode constructor, final Set<String> names) {
    final Set<String> shadowed =
        SyntaxWalk.descendants(constructor).stream()
            .filter(node -> "formalParameter".equals(node.kind()))
            .flatMap(param -> NullSyntax.first(param, "variableDeclaratorId").stream())
            .map(SyntaxWalk::text)
            .collect(Collectors.toSet());
    final SyntaxNode body = NullSyntax.first(constructor, BLOCK).orElse(null);
    return names.stream().allMatch(name -> NullFlow.assigns(body, name, shadowed));
  }
}
