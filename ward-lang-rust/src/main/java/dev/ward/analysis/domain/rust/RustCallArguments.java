package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the arguments of a Rust call. */
final class RustCallArguments {
  private RustCallArguments() {}

  /** The spans of the arguments of a {@code callParams} node, in order; none for a null node. */
  static List<Span> spans(final SyntaxNode params) {
    return params == null
        ? List.of()
        : RustTrees.children(params, RustKinds.EXPRESSION).stream().map(SyntaxNode::span).toList();
  }
}
