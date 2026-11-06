package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads children of Java syntax nodes by kind. */
final class JavaSyntaxNodes {

  private JavaSyntaxNodes() {}

  /** The direct children of {@code node} whose kind is {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }
}
