package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** How a field reference is used by the expression around it. */
record AccessShape(FieldAccessScan.Kind kind, boolean arithmetic, boolean receiver) {

  private static final Set<String> UNARY = Set.of("'++'", "'--'");
  private static final Set<String> ARITHMETIC =
      Set.of("'+'", "'-'", "'*'", "'/'", "'%'", "'&'", "'|'", "'^'", "'<<'", "'>>'", "'>>>'");
  private static final String EXPRESSION = "expression";
  private static final int BINARY_PARTS = 3;
  private static final int UNARY_PARTS = 2;

  /**
   * Reads the use of a field reference.
   *
   * @param wrapper the expression that holds the reference
   * @param outer the expression around the wrapper, or {@code null}
   * @param name the field name
   */
  static AccessShape of(final SyntaxNode wrapper, final SyntaxNode outer, final String name) {
    final List<SyntaxNode> parts =
        outer != null && EXPRESSION.equals(outer.kind()) ? outer.children() : List.of();
    final boolean write = writes(outer, parts, wrapper);
    final boolean receiver =
        parts.size() == BINARY_PARTS
            && parts.get(0).equals(wrapper)
            && "methodCall".equals(parts.get(2).kind());
    return new AccessShape(
        write ? FieldAccessScan.Kind.WRITE : FieldAccessScan.Kind.READ,
        write && arithmetic(parts, name),
        receiver);
  }

  private static boolean writes(
      final SyntaxNode outer, final List<SyntaxNode> parts, final SyntaxNode wrapper) {
    if (parts.isEmpty()) {
      return false;
    }
    if (Assignments.isAssignment(outer)) {
      return parts.get(0).equals(wrapper);
    }
    return parts.size() == UNARY_PARTS
        && (unary(parts, 1) && parts.get(0).equals(wrapper)
            || unary(parts, 0) && parts.get(1).equals(wrapper));
  }

  private static boolean arithmetic(final List<SyntaxNode> parts, final String name) {
    return parts.size() == UNARY_PARTS
        || !"'='".equals(parts.get(1).kind())
        || readsArithmetically(parts.get(2), name);
  }

  private static boolean unary(final List<SyntaxNode> parts, final int index) {
    return UNARY.contains(parts.get(index).kind());
  }

  private static boolean readsArithmetically(final SyntaxNode value, final String name) {
    return SyntaxWalk.descendants(value).stream()
        .filter(node -> EXPRESSION.equals(node.kind()) && node.children().size() == BINARY_PARTS)
        .filter(node -> ARITHMETIC.contains(node.children().get(1).kind()))
        .anyMatch(
            node ->
                SyntaxWalk.descendants(node).stream()
                    .anyMatch(
                        id -> "identifier".equals(id.kind()) && name.equals(SyntaxWalk.text(id))));
  }
}
