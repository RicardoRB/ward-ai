package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds the local variables of a class that have the name of a field the class never writes. */
final class LocalMasks {
  private static final String DECLARATION = "localVariableDeclaration";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String DECLARATOR_ID = "variableDeclaratorId";

  private LocalMasks() {}

  /** Returns the declarators of the locals of {@code type} that hide an unwritten field. */
  static List<SyntaxNode> of(final SyntaxNode type, final FieldAccesses accesses) {
    final Set<String> unwritten = new HashSet<>();
    accesses.fields().stream()
        .filter(field -> !field.initialized() && !field.annotated())
        .filter(
            field ->
                accesses.accessesTo(field.name()).stream()
                    .allMatch(access -> access.use() == FieldAccesses.Use.READ))
        .forEach(field -> unwritten.add(field.name()));
    return SyntaxWalk.descendants(type).stream()
        .filter(node -> DECLARATION.equals(node.kind()))
        .flatMap(declaration -> SyntaxWalk.descendants(declaration).stream())
        .filter(node -> DECLARATOR.equals(node.kind()))
        .filter(node -> unwritten.contains(name(node)))
        .toList();
  }

  /** Returns the name a variable declarator declares. */
  static String name(final SyntaxNode declarator) {
    return SyntaxWalk.descendants(declarator).stream()
        .filter(node -> DECLARATOR_ID.equals(node.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
