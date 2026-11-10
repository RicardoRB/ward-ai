package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The kind of each named TypeScript declaration of a unit, keyed by the offset of its name. The
 * kind is the grammar rule of the declaration, such as {@code interfaceDeclaration}.
 */
final class DeclarationKinds {

  private static final Set<String> NAMED =
      Set.of(
          "interfaceDeclaration",
          "typeAliasDeclaration",
          "enumDeclaration",
          "classDeclaration",
          "namespaceDeclaration",
          "functionDeclaration");
  private static final Set<String> NAME_RULES = Set.of("identifier", "namespaceName");
  private static final String IDENTIFIER_LEAF = "Identifier";

  private DeclarationKinds() {}

  /** The declaration kind of each named declaration, by the offset of its name. */
  static Map<Integer, String> of(final SyntaxNode root) {
    final Map<Integer, String> found = new HashMap<>();
    collect(root, found);
    return found;
  }

  private static void collect(final SyntaxNode node, final Map<Integer, String> found) {
    if (NAMED.contains(node.kind())) {
      nameOffset(node).ifPresent(offset -> found.put(offset, node.kind()));
    }
    node.children().forEach(child -> collect(child, found));
  }

  /** The name is the first direct child that is an identifier or a namespace name. */
  private static Optional<Integer> nameOffset(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> NAME_RULES.contains(child.kind()))
        .findFirst()
        .flatMap(DeclarationKinds::leafOffset);
  }

  private static Optional<Integer> leafOffset(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return IDENTIFIER_LEAF.equals(node.kind())
          ? Optional.of(node.span().startOffset())
          : Optional.empty();
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<Integer> found = leafOffset(child);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }
}
