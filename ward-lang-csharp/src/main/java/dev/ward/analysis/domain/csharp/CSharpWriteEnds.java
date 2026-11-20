package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;

/**
 * The end offset of the declaration or assignment that writes each name, by the start offset of the
 * name, so the write comes after the reads of its value.
 */
final class CSharpWriteEnds {
  private static final int ASSIGNMENT_PARTS = 3;

  private CSharpWriteEnds() {}

  static void collect(final SyntaxNode node, final Map<Integer, Integer> ends) {
    if ("local_variable_declarator".equals(node.kind()) && CSharpSyntax.hasToken(node, "=")) {
      CSharpSyntax.name(node)
          .ifPresent(name -> ends.put(name.span().startOffset(), node.span().endOffset()));
    } else if ("expression".equals(node.kind()) && node.children().size() == ASSIGNMENT_PARTS) {
      CSharpSyntax.soleDescendant(node.children().get(0), "simple_name")
          .flatMap(CSharpSyntax::name)
          .ifPresent(name -> ends.put(name.span().startOffset(), node.span().endOffset()));
    }
    node.children().forEach(child -> collect(child, ends));
  }
}
