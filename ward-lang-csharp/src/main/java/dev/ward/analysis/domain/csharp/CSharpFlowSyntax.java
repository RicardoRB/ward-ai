package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** The span reads and body kinds that the C# flow lowering, its accesses and its sites share. */
final class CSharpFlowSyntax {
  /** Declarations that are bodies of their own, so a statement does not enter them. */
  static final Set<String> OTHER_BODIES =
      Set.of(
          "local_function_declaration",
          "class_declaration",
          "struct_declaration",
          "record_declaration",
          "interface_declaration",
          "enum_declaration"
      );

  private static final int ASSIGNMENT_PARTS = 3;
  private static final int OPERATOR = 1;

  private CSharpFlowSyntax() {}

  /** Whether {@code node} is an assignment: a target, an assignment operator and a value. */
  static boolean isAssignment(final SyntaxNode node) {
    return "expression".equals(node.kind())
        && node.children().size() == ASSIGNMENT_PARTS
        && "assignment_operator".equals(node.children().get(OPERATOR).kind());
  }

  /** The arguments of the first argument list among {@code parts}, in order. */
  static List<SyntaxNode> argumentsOf(final List<SyntaxNode> parts) {
    return parts.stream()
        .filter(part -> "argument_list".equals(part.kind()))
        .findFirst()
        .map(list -> CSharpSyntax.children(list, "argument"))
        .orElse(List.of());
  }

  /** Whether {@code inner} lies within {@code outer}. */
  static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }

  /** The span from the start of {@code from} to the end of {@code to}. */
  static Span between(final Span from, final Span to) {
    return new Span(
        from.startLine(),
        from.startColumn(),
        to.endLine(),
        to.endColumn(),
        from.startOffset(),
        to.endOffset()
    );
  }

  /** The text of the identifier a node names, or empty. */
  static String name(final SyntaxNode node) {
    return CSharpSyntax.name(node).map(CSharpSyntax::text).orElse("");
  }
}
